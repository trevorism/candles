package com.trevorism.service

import com.trevorism.model.Candle
import com.trevorism.model.CandleInterval
import org.junit.jupiter.api.Test

import java.time.Instant

import static org.junit.jupiter.api.Assertions.assertThrows

class CandleServiceTest {

    private static Date utc(String instant) {
        Date.from(Instant.parse(instant))
    }

    private Map findCall = [:]
    private List<Candle> savedCandles

    private CandleService createService(String now = "2026-09-26T13:45:00Z") {
        CandleRepository repository = [
                find: { String pair, CandleInterval interval, Date from, Date to ->
                    findCall = [pair: pair, interval: interval, from: from, to: to]
                    return [new Candle(pair: pair)]
                },
                save: { List<Candle> candles -> savedCandles = candles }
        ] as CandleRepository
        CandleService service = new CandleService(repository)
        service.@clock = { utc(now) }
        return service
    }

    @Test
    void testDefaultsToLast500ClosedHourlyCandles() {
        List<Candle> result = createService().getCandles("ltcusd", null, null, null)

        assert result.size() == 1
        assert findCall.pair == "LTCUSD"
        assert findCall.interval == CandleInterval.ONE_HOUR
        assert findCall.to == utc("2026-09-26T13:00:00Z")
        assert findCall.from == CandleInterval.ONE_HOUR.minus(utc("2026-09-26T13:00:00Z"), 500)
    }

    @Test
    void testRangeIsAlignedToBucketsAndExcludesFormingCandle() {
        createService().getCandles("SOLUSD", "4h", "2026-09-01T02:00:00Z", "2026-12-01")

        assert findCall.interval == CandleInterval.FOUR_HOURS
        assert findCall.from == utc("2026-09-01T00:00:00Z")
        assert findCall.to == utc("2026-09-26T12:00:00Z")
    }

    @Test
    void testPastRangeIsUsedAsGiven() {
        createService().getCandles("AVAXUSD", "1d", "2025-01-01", "2025-02-01")

        assert findCall.from == utc("2025-01-01T00:00:00Z")
        assert findCall.to == utc("2025-02-01T00:00:00Z")
    }

    @Test
    void testEmptyRangeSkipsRepository() {
        List<Candle> result = createService().getCandles("LTCUSD", "1d", "2025-02-01", "2025-01-01")

        assert result == []
        assert findCall.isEmpty()
    }

    @Test
    void testInvalidInputsAreRejected() {
        CandleService service = createService()
        assertThrows(IllegalArgumentException, { service.getCandles("LTC/USD", "1h", null, null) })
        assertThrows(IllegalArgumentException, { service.getCandles("LTCUSD", "5m", null, null) })
        assertThrows(IllegalArgumentException, { service.getCandles("LTCUSD", "1h", "yesterday", null) })
    }

    @Test
    void testSaveNormalizesAndDeduplicates() {
        Candle first = new Candle(pair: "ltcusd", time: utc("2026-09-26T10:00:00Z"), open: 1, high: 2, low: 0.5, close: 1.5, volume: 10)
        Candle replacement = new Candle(pair: "LTCUSD", time: utc("2026-09-26T10:00:00Z"), open: 1, high: 3, low: 0.5, close: 2.5, vwap: 2.0, volume: 12, tradeCount: 4)
        Candle other = new Candle(pair: "SOLUSD", time: utc("2026-09-26T10:30:00Z"), open: 5, high: 6, low: 4, close: 5, volume: 1)

        createService().saveHourlyCandles([first, replacement, other])

        assert savedCandles.size() == 2
        Candle ltc = savedCandles.find { it.pair == "LTCUSD" }
        assert ltc.high == 3
        assert ltc.tradeCount == 4
        Candle sol = savedCandles.find { it.pair == "SOLUSD" }
        assert sol.time == utc("2026-09-26T10:00:00Z")
        assert sol.vwap == 5
        assert sol.tradeCount == 0
    }

    @Test
    void testSaveDefaultsSourceToKrakenOhlcButKeepsExplicitSource() {
        Candle ohlc = new Candle(pair: "LTCUSD", time: utc("2026-09-26T10:00:00Z"), open: 1, high: 2, low: 0.5, close: 1.5, volume: 10)
        Candle trades = new Candle(pair: "SOLUSD", time: utc("2026-09-26T10:00:00Z"), open: 1, high: 2, low: 0.5, close: 1.5, volume: 10, source: "kraken-trades")

        createService().saveHourlyCandles([ohlc, trades])

        assert savedCandles.find { it.pair == "LTCUSD" }.source == "kraken-ohlc"
        assert savedCandles.find { it.pair == "SOLUSD" }.source == "kraken-trades"
    }

    @Test
    void testImportArchiveAlignsStartToDayAndDefaultsToThreeYears() {
        List importCall = []
        CandleService service = new CandleService([importArchive: { String uri, Date from -> importCall = [uri, from]; 42L }] as CandleRepository)
        service.@clock = { utc("2026-09-26T13:45:00Z") }

        assert service.importArchive("gs://bucket/2026Q2/*USD_60.csv", "2023-07-01T05:00:00Z") == 42L
        assert importCall == ["gs://bucket/2026Q2/*USD_60.csv", utc("2023-07-01T00:00:00Z")]

        service.importArchive("gs://bucket/2026Q2/*USD_60.csv", null)
        assert importCall[1] == utc("2023-09-27T00:00:00Z")
    }

    @Test
    void testImportArchiveRejectsNonArchiveUris() {
        CandleService service = createService()
        assertThrows(IllegalArgumentException, { service.importArchive(null, null) })
        assertThrows(IllegalArgumentException, { service.importArchive("/local/LTCUSD_60.csv", null) })
        assertThrows(IllegalArgumentException, { service.importArchive("gs://bucket/LTCUSD_1440.csv", null) })
    }

    @Test
    void testSaveRejectsIncompleteCandle() {
        Candle missingClose = new Candle(pair: "LTCUSD", time: new Date(), open: 1, high: 2, low: 0.5, volume: 10)
        assertThrows(IllegalArgumentException, { createService().saveHourlyCandles([missingClose]) })
    }
}
