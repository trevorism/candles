package com.trevorism.controller

import com.trevorism.model.ArchiveImportRequest
import com.trevorism.model.ArchiveImportResult
import com.trevorism.model.IngestResult
import com.trevorism.secure.Roles
import com.trevorism.secure.Secure
import com.trevorism.service.CandleService
import com.trevorism.service.TradeBackfillService
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.QueryValue
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.inject.Inject

@Controller("/import")
class ImportController {

    @Inject
    CandleService candleService
    @Inject
    TradeBackfillService tradeBackfillService

    @Tag(name = "Import Operations")
    @Operation(summary = "Insert missing hourly candles from Kraken OHLCVT archive files in GCS; existing candles are never overwritten **Secure")
    @Post(value = "/archive", produces = MediaType.APPLICATION_JSON, consumes = MediaType.APPLICATION_JSON)
    @Secure(Roles.ADMIN)
    ArchiveImportResult importArchive(@Body ArchiveImportRequest request) {
        long inserted = candleService.importArchive(request?.sourceUri, request?.from)
        return new ArchiveImportResult(sourceUri: request.sourceUri, candlesInserted: inserted)
    }

    @Tag(name = "Import Operations")
    @Operation(summary = "Rebuild one UTC day of hourly candles for a pair from Kraken trade history **Secure")
    @Post(value = "/trades/{pair}", produces = MediaType.APPLICATION_JSON)
    @Secure(value = Roles.ADMIN, allowInternal = true)
    IngestResult backfillDay(String pair, @QueryValue String date) {
        return tradeBackfillService.backfillDay(pair, date)
    }
}
