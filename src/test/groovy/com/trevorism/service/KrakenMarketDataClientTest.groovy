package com.trevorism.service

import com.trevorism.kraken.PublicKrakenClient
import com.trevorism.kraken.model.AssetPair
import com.trevorism.model.Candle
import org.junit.jupiter.api.Test

import java.time.Duration

import com.trevorism.kraken.model.Candle as KrakenCandle

class KrakenMarketDataClientTest {

    @Test
    void testMapsKrakenCandlesAndRequestsHourlyInterval() {
        Duration requestedDuration
        Date time = new Date(1790000000000L)
        KrakenCandle krakenCandle = new KrakenCandle(time: time, open: 1, high: 2, low: 0.5, close: 1.5,
                volumeWeightedAveragePrice: 1.2, volume: 10, count: 3)
        KrakenMarketDataClient client = new KrakenMarketDataClient()
        client.@krakenClient = [getCandles: { String pair, Duration duration ->
            requestedDuration = duration
            [krakenCandle]
        }] as PublicKrakenClient

        List<Candle> candles = client.getRecentHourlyCandles("LTCUSD")

        assert requestedDuration == Duration.ofHours(1)
        assert candles == [new Candle(pair: "LTCUSD", time: time, open: 1, high: 2, low: 0.5, close: 1.5, vwap: 1.2, volume: 10, tradeCount: 3)]
    }

    @Test
    void testMapsAssetPairs() {
        KrakenMarketDataClient client = new KrakenMarketDataClient()
        client.@krakenClient = [getAssetPairs: { ->
            [new AssetPair(pairName: "LTCUSD", baseName: "LTC", quoteName: "USD")]
        }] as PublicKrakenClient

        assert client.availablePairs*.pair == ["LTCUSD"]
        assert client.availablePairs*.quoteName == ["USD"]
    }
}
