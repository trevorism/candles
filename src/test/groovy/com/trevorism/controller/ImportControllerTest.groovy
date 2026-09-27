package com.trevorism.controller

import com.trevorism.model.ArchiveImportRequest
import com.trevorism.model.ArchiveImportResult
import com.trevorism.model.Candle
import com.trevorism.model.IngestResult
import com.trevorism.model.Trade
import com.trevorism.service.CandleRepository
import com.trevorism.service.CandleService
import com.trevorism.service.TradeBackfillService
import com.trevorism.service.TradeHistoryClient
import org.junit.jupiter.api.Test

class ImportControllerTest {

    private CandleService candleService = new CandleService([
            importArchive: { String uri, Date from -> 1234L },
            save         : { List<Candle> candles -> }
    ] as CandleRepository)

    private TradeBackfillService tradeBackfillService = new TradeBackfillService(
            [getTrades: { String pair, Date from, Date to -> [new Trade(time: from, price: 1, volume: 1)] }] as TradeHistoryClient,
            candleService)

    @Test
    void testImportArchive() {
        ImportController controller = new ImportController(candleService: candleService, tradeBackfillService: tradeBackfillService)

        ArchiveImportResult result = controller.importArchive(new ArchiveImportRequest(sourceUri: "gs://bucket/2026Q2/*USD_60.csv", from: "2023-07-01"))

        assert result.sourceUri == "gs://bucket/2026Q2/*USD_60.csv"
        assert result.candlesInserted == 1234L
    }

    @Test
    void testBackfillDay() {
        ImportController controller = new ImportController(candleService: candleService, tradeBackfillService: tradeBackfillService)

        IngestResult result = controller.backfillDay("SOLUSD", "2026-07-01")

        assert result.pair == "SOLUSD"
        assert result.candlesSaved == 1
    }
}
