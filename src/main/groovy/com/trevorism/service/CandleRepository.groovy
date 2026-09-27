package com.trevorism.service

import com.trevorism.model.Candle
import com.trevorism.model.CandleInterval

interface CandleRepository {
    void save(List<Candle> hourlyCandles)
    List<Candle> find(String pair, CandleInterval interval, Date from, Date to)
    long importArchive(String sourceUri, Date from)
}
