package com.trevorism.service

import com.trevorism.model.Candle
import com.trevorism.model.CandleInterval
import com.trevorism.model.IngestResult
import com.trevorism.model.Trade
import jakarta.inject.Inject
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@jakarta.inject.Singleton
class TradeBackfillService {

    private static final Logger log = LoggerFactory.getLogger(TradeBackfillService)

    private final TradeHistoryClient tradeHistoryClient
    private final CandleService candleService
    private Closure<Date> clock = { new Date() }

    @Inject
    TradeBackfillService(TradeHistoryClient tradeHistoryClient, CandleService candleService) {
        this.tradeHistoryClient = tradeHistoryClient
        this.candleService = candleService
    }

    IngestResult backfillDay(String pair, String day) {
        String normalized = CandleService.normalizePair(pair)
        if (!day) {
            throw new IllegalArgumentException("date is required, e.g. 2026-07-01")
        }
        Date dayStart = CandleInterval.ONE_DAY.bucketStart(CandleService.parseDate(day))
        Date dayEnd = new Date(dayStart.time + CandleInterval.ONE_DAY.seconds * 1000L)
        Date lastClosedHourEnd = CandleInterval.ONE_HOUR.bucketStart(clock.call())
        Date end = dayEnd.before(lastClosedHourEnd) ? dayEnd : lastClosedHourEnd
        if (!dayStart.before(end)) {
            throw new IllegalArgumentException("No closed hours on ${day} yet")
        }

        List<Trade> trades = tradeHistoryClient.getTrades(normalized, dayStart, end)
        List<Candle> candles = TradeCandleBuilder.toHourlyCandles(normalized, trades)
        candleService.saveHourlyCandles(candles)
        log.info("Backfilled ${candles.size()} hourly candles for ${normalized} on ${day} from ${trades.size()} trades")
        return new IngestResult(pair: normalized, candlesSaved: candles.size(), latestCandleTime: candles ? candles.last().time : null)
    }
}
