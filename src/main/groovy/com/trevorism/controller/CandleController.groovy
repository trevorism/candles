package com.trevorism.controller

import com.trevorism.model.Candle
import com.trevorism.secure.Permissions
import com.trevorism.secure.Roles
import com.trevorism.secure.Secure
import com.trevorism.service.CandleService
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.QueryValue
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.inject.Inject

@Controller("/candle")
class CandleController {

    @Inject
    CandleService candleService

    @Tag(name = "Candle Operations")
    @Operation(summary = "Get closed candles for a pair; interval is 1h, 4h, 1d, or 1w and dates are UTC **Secure")
    @Get(value = "{pair}", produces = MediaType.APPLICATION_JSON)
    @Secure(value = Roles.USER, permissions = Permissions.READ, allowInternal = true)
    List<Candle> getCandles(String pair,
                            @QueryValue(defaultValue = "1h") String interval,
                            @QueryValue Optional<String> from,
                            @QueryValue Optional<String> to) {
        return candleService.getCandles(pair, interval, from.orElse(null), to.orElse(null))
    }
}
