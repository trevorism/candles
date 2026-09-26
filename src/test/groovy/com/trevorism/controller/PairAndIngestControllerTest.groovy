package com.trevorism.controller

import com.trevorism.data.Repository
import com.trevorism.model.AvailablePair
import com.trevorism.model.Candle
import com.trevorism.model.IngestResult
import com.trevorism.model.TrackResult
import com.trevorism.model.TrackedPair
import com.trevorism.service.CandleRepository
import com.trevorism.service.CandleService
import com.trevorism.service.IngestService
import com.trevorism.service.MarketDataClient
import com.trevorism.service.PairService
import org.junit.jupiter.api.Test

class PairAndIngestControllerTest {

    private List<TrackedPair> stored = [new TrackedPair(id: "1", pair: "LTCUSD")]
    private List<String> fetchedPairs = []

    private MarketDataClient marketDataClient = [
            getAvailablePairs     : { -> [new AvailablePair(pair: "LTCUSD", quoteName: "USD"), new AvailablePair(pair: "SOLUSD", quoteName: "USD")] },
            getRecentHourlyCandles: { String pair ->
                fetchedPairs << pair
                [new Candle(pair: pair, time: new Date(0), open: 1, high: 1, low: 1, close: 1, volume: 1)]
            }
    ] as MarketDataClient

    private PairService pairService() {
        PairService service = new PairService(marketDataClient)
        service.@repository = [
                list  : { -> stored },
                create: { TrackedPair pair -> pair.id = "2"; stored << pair; pair },
                delete: { String id -> stored.find { it.id == id } }
        ] as Repository<TrackedPair>
        return service
    }

    private IngestService ingestService() {
        IngestService service = new IngestService(marketDataClient, new CandleService([save: { List<Candle> c -> }] as CandleRepository))
        service.@pauseBetweenPairsMillis = 0L
        return service
    }

    @Test
    void testTrackBackfillsNewPair() {
        PairController controller = new PairController(pairService: pairService(), ingestService: ingestService())

        TrackResult result = controller.track(new TrackedPair(pair: "solusd"))

        assert result.trackedPair.pair == "SOLUSD"
        assert result.backfill.candlesSaved == 1
        assert fetchedPairs == ["SOLUSD"]
    }

    @Test
    void testPairQueriesAndUntrack() {
        PairController controller = new PairController(pairService: pairService(), ingestService: ingestService())

        assert controller.listTracked()*.pair == ["LTCUSD"]
        assert controller.listAvailable(Optional.of("USD"))*.pair == ["LTCUSD", "SOLUSD"]
        assert controller.untrack("LTCUSD").id == "1"
    }

    @Test
    void testIngestTrackedPairs() {
        IngestController controller = new IngestController(pairService: pairService(), ingestService: ingestService())

        List<IngestResult> results = controller.ingestTracked(IngestService.RECENT_HOURS)

        assert results*.pair == ["LTCUSD"]
        assert results*.candlesSaved == [1]
    }

    @Test
    void testIngestSinglePair() {
        IngestController controller = new IngestController(pairService: pairService(), ingestService: ingestService())

        IngestResult result = controller.ingestPair("AVAXUSD", 720)

        assert result.pair == "AVAXUSD"
        assert fetchedPairs == ["AVAXUSD"]
    }
}
