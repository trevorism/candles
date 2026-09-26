package com.trevorism.service

import com.trevorism.model.AvailablePair
import com.trevorism.model.Candle

interface MarketDataClient {
    List<AvailablePair> getAvailablePairs()
    List<Candle> getRecentHourlyCandles(String pair)
}
