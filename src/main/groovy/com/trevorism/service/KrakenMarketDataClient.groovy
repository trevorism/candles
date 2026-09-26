package com.trevorism.service

import com.trevorism.kraken.PublicKrakenClient
import com.trevorism.kraken.impl.DefaultPublicKrakenClient
import com.trevorism.kraken.model.AssetPair
import com.trevorism.kraken.model.ValidCandleDurations
import com.trevorism.model.AvailablePair
import com.trevorism.model.Candle

import com.trevorism.kraken.model.Candle as KrakenCandle

@jakarta.inject.Singleton
class KrakenMarketDataClient implements MarketDataClient {

    private PublicKrakenClient krakenClient = new DefaultPublicKrakenClient()

    @Override
    List<AvailablePair> getAvailablePairs() {
        return krakenClient.getAssetPairs().collect { AssetPair assetPair ->
            new AvailablePair(pair: assetPair.pairName, baseName: assetPair.baseName, quoteName: assetPair.quoteName)
        }
    }

    @Override
    List<Candle> getRecentHourlyCandles(String pair) {
        return krakenClient.getCandles(pair, ValidCandleDurations.HOUR).collect { KrakenCandle krakenCandle ->
            toCandle(pair, krakenCandle)
        }
    }

    static Candle toCandle(String pair, KrakenCandle krakenCandle) {
        new Candle(
                pair: pair,
                time: krakenCandle.time,
                open: krakenCandle.open,
                high: krakenCandle.high,
                low: krakenCandle.low,
                close: krakenCandle.close,
                vwap: krakenCandle.volumeWeightedAveragePrice,
                volume: krakenCandle.volume,
                tradeCount: krakenCandle.count
        )
    }
}
