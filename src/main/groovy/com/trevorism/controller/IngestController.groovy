package com.trevorism.controller

import com.trevorism.model.IngestResult
import com.trevorism.secure.Roles
import com.trevorism.secure.Secure
import com.trevorism.service.IngestService
import com.trevorism.service.PairService
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.QueryValue
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.inject.Inject

@Controller("/ingest")
class IngestController {

    @Inject
    PairService pairService
    @Inject
    IngestService ingestService

    @Tag(name = "Ingest Operations")
    @Operation(summary = "Save the most recent closed hourly candles for every tracked pair **Secure")
    @Post(value = "/", produces = MediaType.APPLICATION_JSON)
    @Secure(value = Roles.ADMIN, allowInternal = true)
    List<IngestResult> ingestTracked(@QueryValue(defaultValue = "48") int hours) {
        return ingestService.ingest(pairService.listTracked()*.pair, hours)
    }

    @Tag(name = "Ingest Operations")
    @Operation(summary = "Save up to 720 of the most recent closed hourly candles for any Kraken pair **Secure")
    @Post(value = "{pair}", produces = MediaType.APPLICATION_JSON)
    @Secure(value = Roles.ADMIN, allowInternal = true)
    IngestResult ingestPair(String pair, @QueryValue(defaultValue = "720") int hours) {
        return ingestService.ingestPair(pair, hours)
    }
}
