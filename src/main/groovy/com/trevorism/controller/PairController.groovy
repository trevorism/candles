package com.trevorism.controller

import com.trevorism.model.AvailablePair
import com.trevorism.model.TrackResult
import com.trevorism.model.TrackedPair
import com.trevorism.secure.Permissions
import com.trevorism.secure.Roles
import com.trevorism.secure.Secure
import com.trevorism.service.IngestService
import com.trevorism.service.PairService
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.*
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.inject.Inject

@Controller("/pair")
class PairController {

    @Inject
    PairService pairService
    @Inject
    IngestService ingestService

    @Tag(name = "Pair Operations")
    @Operation(summary = "List pairs that are ingested hourly **Secure")
    @Get(value = "/", produces = MediaType.APPLICATION_JSON)
    @Secure(value = Roles.USER, permissions = Permissions.READ, allowInternal = true)
    List<TrackedPair> listTracked() {
        return pairService.listTracked()
    }

    @Tag(name = "Pair Operations")
    @Operation(summary = "List pairs Kraken offers, optionally filtered by quote currency such as USD **Secure")
    @Get(value = "/available", produces = MediaType.APPLICATION_JSON)
    @Secure(value = Roles.USER, permissions = Permissions.READ, allowInternal = true)
    List<AvailablePair> listAvailable(@QueryValue Optional<String> quote) {
        return pairService.listAvailable(quote.orElse(null))
    }

    @Tag(name = "Pair Operations")
    @Operation(summary = "Track a pair and backfill the last 720 hourly candles **Secure")
    @Post(value = "/", produces = MediaType.APPLICATION_JSON, consumes = MediaType.APPLICATION_JSON)
    @Secure(value = Roles.USER, permissions = Permissions.CREATE)
    TrackResult track(@Body TrackedPair request) {
        TrackedPair trackedPair = pairService.track(request?.pair)
        return new TrackResult(trackedPair: trackedPair, backfill: ingestService.ingestPair(trackedPair.pair, IngestService.MAX_HOURS))
    }

    @Tag(name = "Pair Operations")
    @Operation(summary = "Stop tracking a pair; stored candles are kept **Secure")
    @Delete(value = "{pair}", produces = MediaType.APPLICATION_JSON)
    @Secure(Roles.ADMIN)
    TrackedPair untrack(String pair) {
        return pairService.untrack(pair)
    }
}
