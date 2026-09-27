package com.trevorism.service

import com.trevorism.model.Trade
import org.junit.jupiter.api.Test

import java.time.Instant

import static org.junit.jupiter.api.Assertions.assertThrows

class KrakenTradeHistoryClientTest {

    private static Date utc(String instant) {
        Date.from(Instant.parse(instant))
    }

    private static String page(List<List> trades, String last) {
        String rows = trades.collect { List t -> "[\"${t[0]}\",\"${t[1]}\",${t[2]},\"b\",\"m\",\"\",${t[3]}]" }.join(",")
        return "{\"error\":[],\"result\":{\"XLTCZUSD\":[${rows}],\"last\":\"${last}\"}}"
    }

    private List<String> requestedUrls = []

    private KrakenTradeHistoryClient createClient(List<String> pages) {
        KrakenTradeHistoryClient client = new KrakenTradeHistoryClient()
        Iterator<String> responses = pages.iterator()
        client.@fetchPage = { String url -> requestedUrls << url; responses.next() }
        client.@pauseBetweenPagesMillis = 0L
        return client
    }

    @Test
    void testPagesUntilTradesPassTheEndOfTheRange() {
        long start = utc("2026-07-01T00:00:00Z").time.intdiv(1000)
        KrakenTradeHistoryClient client = createClient([
                page([["41.90", "0.5", start + 10.25, 101], ["41.95", "1.0", start + 20, 102]], "1782864020000000000"),
                page([["42.00", "2.0", start + 86399, 103], ["42.10", "3.0", start + 86400, 104]], "1782950400000000000")
        ])

        List<Trade> trades = client.getTrades("LTCUSD", utc("2026-07-01T00:00:00Z"), utc("2026-07-02T00:00:00Z"))

        assert trades*.price == [41.90d, 41.95d, 42.00d]
        assert trades[0].time == new Date((start + 10.25) * 1000 as long)
        assert requestedUrls.size() == 2
        assert requestedUrls[0] == "https://api.kraken.com/0/public/Trades?pair=LTCUSD&since=${start * 1_000_000_000L}&count=1000"
        assert requestedUrls[1].contains("since=1782864020000000000")
    }

    @Test
    void testTradeRepeatedAtPageBoundaryIsCountedOnce() {
        long start = utc("2026-07-02T00:00:00Z").time.intdiv(1000)
        KrakenTradeHistoryClient client = createClient([
                page([["42.70", "1.0", start + 5, 17090798], ["42.71", "4.067", start + 12089.4168053, 17090799]], "1782962489416805300"),
                page([["42.71", "4.067", start + 12089.4168053, 17090799], ["42.80", "2.0", start + 90000, 17090800]], "1783040400000000000")
        ])

        List<Trade> trades = client.getTrades("LTCUSD", utc("2026-07-02T00:00:00Z"), utc("2026-07-03T00:00:00Z"))

        assert trades*.id == [17090798L, 17090799L]
        assert trades*.volume.sum() == 5.067d
    }

    @Test
    void testStopsOnEmptyPage() {
        KrakenTradeHistoryClient client = createClient([page([], "1782864000000000000")])

        assert client.getTrades("LTCUSD", utc("2026-07-01T00:00:00Z"), utc("2026-07-02T00:00:00Z")) == []
    }

    @Test
    void testKrakenErrorIsRaised() {
        KrakenTradeHistoryClient client = createClient(['{"error":["EQuery:Unknown asset pair"]}'])

        assertThrows(IllegalStateException, { client.getTrades("NOPEUSD", new Date(0), new Date()) })
    }
}
