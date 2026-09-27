package com.trevorism.service

import com.trevorism.model.Candle
import com.trevorism.model.CandleInterval
import com.trevorism.model.CandleSource
import com.trevorism.model.Trade

class TradeCandleBuilder {

    static List<Candle> toHourlyCandles(String pair, List<Trade> trades) {
        Map<Date, List<Trade>> tradesByHour = trades.sort(false) { it.time }.groupBy { CandleInterval.ONE_HOUR.bucketStart(it.time) }
        return tradesByHour.collect { Date hour, List<Trade> hourTrades ->
            double volume = hourTrades.sum { it.volume } as double
            double notional = hourTrades.sum { it.price * it.volume } as double
            new Candle(
                    pair: pair,
                    time: hour,
                    open: hourTrades.first().price,
                    high: hourTrades*.price.max(),
                    low: hourTrades*.price.min(),
                    close: hourTrades.last().price,
                    vwap: volume > 0 ? notional / volume : hourTrades.last().price,
                    volume: volume,
                    tradeCount: hourTrades.size(),
                    source: CandleSource.KRAKEN_TRADES
            )
        }.sort { it.time }
    }
}
