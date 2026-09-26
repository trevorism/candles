package com.trevorism.service

import com.trevorism.model.Candle
import com.trevorism.model.CandleInterval
import com.trevorism.model.IngestResult
import jakarta.inject.Inject
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@jakarta.inject.Singleton
class IngestService {

    static final int RECENT_HOURS = 48
    static final int MAX_HOURS = 720
    private static final Logger log = LoggerFactory.getLogger(IngestService)
    private static final long HOUR_MILLIS = CandleInterval.ONE_HOUR.seconds * 1000L

    private final MarketDataClient marketDataClient
    private final CandleService candleService
    private Closure<Date> clock = { new Date() }
    private long pauseBetweenPairsMillis = 1000L

    @Inject
    IngestService(MarketDataClient marketDataClient, CandleService candleService) {
        this.marketDataClient = marketDataClient
        this.candleService = candleService
    }

    List<IngestResult> ingest(List<String> pairs, int hours) {
        validateHours(hours)
        List<IngestResult> results = []
        pairs.eachWithIndex { String pair, int index ->
            if (index > 0) {
                sleep(pauseBetweenPairsMillis)
            }
            results << ingestPair(pair, hours)
        }
        return results
    }

    IngestResult ingestPair(String pair, int hours) {
        validateHours(hours)
        String normalized = CandleService.normalizePair(pair)
        try {
            long now = clock.call().time
            List<Candle> closed = marketDataClient.getRecentHourlyCandles(normalized)
                    .findAll { Candle candle -> candle.time.time + HOUR_MILLIS <= now }
                    .sort { it.time }
            List<Candle> recent = closed.takeRight(hours)
            candleService.saveHourlyCandles(recent)
            log.info("Ingested ${recent.size()} hourly candles for ${normalized}")
            return new IngestResult(pair: normalized, candlesSaved: recent.size(), latestCandleTime: recent ? recent.last().time : null)
        } catch (Exception e) {
            log.warn("Failed to ingest ${normalized}", e)
            return new IngestResult(pair: normalized, error: e.message ?: e.class.simpleName)
        }
    }

    private static void validateHours(int hours) {
        if (hours < 1 || hours > MAX_HOURS) {
            throw new IllegalArgumentException("hours must be between 1 and ${MAX_HOURS}")
        }
    }
}
