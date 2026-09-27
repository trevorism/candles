package com.trevorism.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.trevorism.model.Trade

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

@jakarta.inject.Singleton
class KrakenTradeHistoryClient implements TradeHistoryClient {

    static final String TRADES_URL = "https://api.kraken.com/0/public/Trades"
    static final int PAGE_SIZE = 1000
    static final int MAX_PAGES = 200
    private static final long NANOS_PER_MILLI = 1_000_000L

    private final ObjectMapper objectMapper = new ObjectMapper()
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
    private Closure<String> fetchPage = { String url -> httpGet(url) }
    private long pauseBetweenPagesMillis = 1100L

    @Override
    List<Trade> getTrades(String pair, Date from, Date to) {
        List<Trade> trades = []
        String since = String.valueOf(from.time * NANOS_PER_MILLI)
        for (int page = 0; page < MAX_PAGES; page++) {
            if (page > 0) {
                sleep(pauseBetweenPagesMillis)
            }
            Map response = objectMapper.readValue(fetchPage.call("${TRADES_URL}?pair=${pair}&since=${since}&count=${PAGE_SIZE}".toString()), Map)
            if (response.error) {
                throw new IllegalStateException("Kraken Trades error for ${pair}: ${response.error}")
            }
            Map result = response.result as Map
            List<List> rows = result.find { key, value -> key != "last" }?.value as List<List> ?: []
            List<Trade> pageTrades = rows.collect { List row -> toTrade(row) }
            trades.addAll(pageTrades.findAll { !it.time.before(from) && it.time.before(to) })
            String last = result.last as String
            boolean reachedEnd = pageTrades.isEmpty() || !pageTrades.last().time.before(to) || last == since
            if (reachedEnd) {
                return trades
            }
            since = last
        }
        throw new IllegalStateException("Kraken Trades for ${pair} exceeded ${MAX_PAGES} pages between ${from} and ${to}")
    }

    static Trade toTrade(List row) {
        BigDecimal seconds = new BigDecimal(row[2].toString())
        return new Trade(
                price: new BigDecimal(row[0].toString()).doubleValue(),
                volume: new BigDecimal(row[1].toString()).doubleValue(),
                time: new Date((seconds * 1000).longValue())
        )
    }

    private String httpGet(String url) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", "trevorism-candles")
                .GET()
                .build()
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Kraken Trades returned HTTP ${response.statusCode()}")
        }
        return response.body()
    }
}
