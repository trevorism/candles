package com.trevorism.service

import com.trevorism.model.Candle
import com.trevorism.model.IngestResult
import com.trevorism.model.Trade
import org.junit.jupiter.api.Test

import java.time.Instant

import static org.junit.jupiter.api.Assertions.assertThrows

class TradeBackfillServiceTest {

    private static Date utc(String instant) {
        Date.from(Instant.parse(instant))
    }

    private List requestedRange = []
    private List<Candle> savedCandles = []

    private TradeBackfillService createService(String now) {
        TradeHistoryClient tradeHistoryClient = [getTrades: { String pair, Date from, Date to ->
            requestedRange = [pair, from, to]
            [new Trade(time: new Date(from.time + 60_000L), price: 10, volume: 1),
             new Trade(time: new Date(from.time + 3_660_000L), price: 11, volume: 2)]
        }] as TradeHistoryClient
        CandleService candleService = new CandleService([save: { List<Candle> candles -> savedCandles.addAll(candles) }] as CandleRepository)
        TradeBackfillService service = new TradeBackfillService(tradeHistoryClient, candleService)
        service.@clock = { utc(now) }
        return service
    }

    @Test
    void testBackfillsWholePastDay() {
        IngestResult result = createService("2026-09-26T13:45:00Z").backfillDay("ltcusd", "2026-07-01")

        assert requestedRange == ["LTCUSD", utc("2026-07-01T00:00:00Z"), utc("2026-07-02T00:00:00Z")]
        assert result.pair == "LTCUSD"
        assert result.candlesSaved == 2
        assert result.latestCandleTime == utc("2026-07-01T01:00:00Z")
        assert savedCandles*.source == ["kraken-trades", "kraken-trades"]
    }

    @Test
    void testTodayStopsAtLastClosedHour() {
        createService("2026-09-26T13:45:00Z").backfillDay("LTCUSD", "2026-09-26")

        assert requestedRange[2] == utc("2026-09-26T13:00:00Z")
    }

    @Test
    void testRejectsMissingOrFutureDay() {
        TradeBackfillService service = createService("2026-09-26T13:45:00Z")
        assertThrows(IllegalArgumentException, { service.backfillDay("LTCUSD", null) })
        assertThrows(IllegalArgumentException, { service.backfillDay("LTCUSD", "2026-09-27") })
    }
}
