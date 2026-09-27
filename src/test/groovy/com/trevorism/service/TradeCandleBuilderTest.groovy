package com.trevorism.service

import com.trevorism.model.Candle
import com.trevorism.model.Trade
import org.junit.jupiter.api.Test

import java.time.Instant

class TradeCandleBuilderTest {

    private static Date utc(String instant) {
        Date.from(Instant.parse(instant))
    }

    @Test
    void testBuildsOneCandlePerHourWithTrades() {
        List<Trade> trades = [
                new Trade(time: utc("2026-07-01T00:59:59Z"), price: 12, volume: 1),
                new Trade(time: utc("2026-07-01T00:00:05Z"), price: 10, volume: 2),
                new Trade(time: utc("2026-07-01T00:30:00Z"), price: 15, volume: 1),
                new Trade(time: utc("2026-07-01T00:40:00Z"), price: 8, volume: 4),
                new Trade(time: utc("2026-07-01T02:10:00Z"), price: 20, volume: 0.5)
        ]

        List<Candle> candles = TradeCandleBuilder.toHourlyCandles("LTCUSD", trades)

        assert candles*.time == [utc("2026-07-01T00:00:00Z"), utc("2026-07-01T02:00:00Z")]
        Candle first = candles[0]
        assert first.pair == "LTCUSD"
        assert first.open == 10
        assert first.high == 15
        assert first.low == 8
        assert first.close == 12
        assert first.volume == 8
        assert first.vwap == (10 * 2 + 15 + 8 * 4 + 12) / 8d
        assert first.tradeCount == 4
        assert first.source == "kraken-trades"
        assert candles[1].open == 20 && candles[1].close == 20
    }

    @Test
    void testNoTradesMeansNoCandles() {
        assert TradeCandleBuilder.toHourlyCandles("LTCUSD", []) == []
    }
}
