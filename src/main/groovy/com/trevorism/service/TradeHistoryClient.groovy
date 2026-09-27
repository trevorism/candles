package com.trevorism.service

import com.trevorism.model.Trade

interface TradeHistoryClient {
    List<Trade> getTrades(String pair, Date from, Date to)
}
