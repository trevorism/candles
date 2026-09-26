package com.trevorism.service

import com.trevorism.model.AvailablePair
import com.trevorism.model.Candle
import com.trevorism.model.CandleInterval
import com.trevorism.model.IngestResult
import org.junit.jupiter.api.Test

import java.time.Instant

import static org.junit.jupiter.api.Assertions.assertThrows

class IngestServiceTest {

    private static Date utc(String instant) {
        Date.from(Instant.parse(instant))
    }

    private List<Candle> savedCandles = []

    private static List<Candle> hourlyCandles(String pair, String firstHour, int count) {
        Date start = utc(firstHour)
        (0..<count).collect { int i ->
            new Candle(pair: pair, time: new Date(start.time + i * 3600_000L), open: 1, high: 2, low: 0.5, close: 1.5, vwap: 1.2, volume: 10, tradeCount: 3)
        }
    }

    private IngestService createService(Map<String, Closure<List<Candle>>> candlesByPair) {
        MarketDataClient marketDataClient = [
                getAvailablePairs     : { -> [] as List<AvailablePair> },
                getRecentHourlyCandles: { String pair -> candlesByPair[pair].call() }
        ] as MarketDataClient
        CandleRepository repository = [save: { List<Candle> candles -> savedCandles.addAll(candles) }] as CandleRepository
        IngestService service = new IngestService(marketDataClient, new CandleService(repository))
        service.@clock = { utc("2026-09-26T13:20:00Z") }
        service.@pauseBetweenPairsMillis = 0L
        return service
    }

    @Test
    void testIngestPairDropsFormingCandleAndKeepsMostRecentHours() {
        IngestService service = createService([LTCUSD: { hourlyCandles("LTCUSD", "2026-09-26T00:00:00Z", 14) }])

        IngestResult result = service.ingestPair("ltcusd", 5)

        assert result.pair == "LTCUSD"
        assert result.candlesSaved == 5
        assert result.error == null
        assert result.latestCandleTime == utc("2026-09-26T12:00:00Z")
        assert savedCandles*.time == (8..12).collect { utc(String.format("2026-09-26T%02d:00:00Z", it)) }
    }

    @Test
    void testIngestContinuesPastFailingPair() {
        IngestService service = createService([
                LTCUSD : { hourlyCandles("LTCUSD", "2026-09-26T10:00:00Z", 3) },
                BADUSD : { throw new RuntimeException("EQuery:Unknown asset pair") },
                SOLUSD : { hourlyCandles("SOLUSD", "2026-09-26T11:00:00Z", 2) }
        ])

        List<IngestResult> results = service.ingest(["LTCUSD", "BADUSD", "SOLUSD"], IngestService.RECENT_HOURS)

        assert results*.pair == ["LTCUSD", "BADUSD", "SOLUSD"]
        assert results*.candlesSaved == [3, 0, 2]
        assert results[1].error == "EQuery:Unknown asset pair"
        assert savedCandles.size() == 5
    }

    @Test
    void testNoClosedCandlesSavesNothing() {
        IngestService service = createService([LTCUSD: { hourlyCandles("LTCUSD", "2026-09-26T13:00:00Z", 1) }])

        IngestResult result = service.ingestPair("LTCUSD", 48)

        assert result.candlesSaved == 0
        assert result.latestCandleTime == null
        assert savedCandles.isEmpty()
    }

    @Test
    void testHoursMustBeWithinKrakenWindow() {
        IngestService service = createService([:])
        assertThrows(IllegalArgumentException, { service.ingestPair("LTCUSD", 0) })
        assertThrows(IllegalArgumentException, { service.ingest(["LTCUSD"], IngestService.MAX_HOURS + 1) })
    }

    @Test
    void testCandleIntervalHourMatchesIngestAssumption() {
        assert CandleInterval.ONE_HOUR.seconds == 3600
    }
}
