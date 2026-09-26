package com.trevorism.gcloud

this.metaClass.mixin(io.cucumber.groovy.Hooks)
this.metaClass.mixin(io.cucumber.groovy.EN)

String baseUrl = System.getenv("ACCEPTANCE_BASE_URL") ?: "https://candles.trade.trevorism.com"

int responseCode

Given(/the candles application is alive/) { ->
    try {
        assert new URL("${baseUrl}/ping").text == "pong"
    }
    catch (Exception ignored) {
        Thread.sleep(10000)
        assert new URL("${baseUrl}/ping").text == "pong"
    }
}

When(/an anonymous client requests hourly candles for {string}/) { String pair ->
    HttpURLConnection connection = (HttpURLConnection) new URL("${baseUrl}/candle/${pair}?interval=1h").openConnection()
    responseCode = connection.responseCode
    connection.disconnect()
}

Then(/the request is rejected as unauthorized/) { ->
    assert responseCode in [401, 403]
}
