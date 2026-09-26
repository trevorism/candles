package com.trevorism.service

import com.trevorism.data.Repository
import com.trevorism.model.AvailablePair
import com.trevorism.model.Candle
import com.trevorism.model.TrackedPair
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertThrows

class PairServiceTest {

    private List<TrackedPair> stored = [new TrackedPair(id: "1", pair: "SOLUSD"), new TrackedPair(id: "2", pair: "LTCUSD")]
    private List<String> deletedIds = []

    private PairService createService() {
        MarketDataClient marketDataClient = [
                getAvailablePairs     : { ->
                    [new AvailablePair(pair: "XBTUSD", baseName: "XBT", quoteName: "USD"),
                     new AvailablePair(pair: "AVAXUSD", baseName: "AVAX", quoteName: "USD"),
                     new AvailablePair(pair: "AVAXEUR", baseName: "AVAX", quoteName: "EUR"),
                     new AvailablePair(pair: "LTCUSD", baseName: "LTC", quoteName: "USD")]
                },
                getRecentHourlyCandles: { String pair -> [] as List<Candle> }
        ] as MarketDataClient
        Repository<TrackedPair> repository = [
                list  : { -> stored },
                create: { TrackedPair pair -> pair.id = "3"; stored << pair; pair },
                delete: { String id -> deletedIds << id; stored.find { it.id == id } }
        ] as Repository<TrackedPair>
        PairService service = new PairService(marketDataClient)
        service.@repository = repository
        return service
    }

    @Test
    void testListTrackedIsSortedByPair() {
        assert createService().listTracked()*.pair == ["LTCUSD", "SOLUSD"]
    }

    @Test
    void testListAvailableFiltersByQuote() {
        assert createService().listAvailable("usd")*.pair == ["AVAXUSD", "LTCUSD", "XBTUSD"]
        assert createService().listAvailable(null).size() == 4
    }

    @Test
    void testTrackCreatesPairKrakenLists() {
        TrackedPair tracked = createService().track("avaxusd")

        assert tracked.id == "3"
        assert tracked.pair == "AVAXUSD"
        assert tracked.baseName == "AVAX"
        assert tracked.quoteName == "USD"
        assert tracked.createdDate != null
    }

    @Test
    void testTrackReturnsExistingPairWithoutDuplicating() {
        TrackedPair tracked = createService().track("LTCUSD")

        assert tracked.id == "2"
        assert stored.size() == 2
    }

    @Test
    void testTrackRejectsPairKrakenDoesNotList() {
        assertThrows(IllegalArgumentException, { createService().track("BTCUSD") })
    }

    @Test
    void testUntrackDeletesById() {
        TrackedPair removed = createService().untrack("solusd")

        assert removed.pair == "SOLUSD"
        assert deletedIds == ["1"]
    }

    @Test
    void testUntrackRejectsUnknownPair() {
        assertThrows(IllegalArgumentException, { createService().untrack("AVAXUSD") })
    }
}
