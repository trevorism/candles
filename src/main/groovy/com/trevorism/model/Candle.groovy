package com.trevorism.model

import com.fasterxml.jackson.annotation.JsonFormat
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

@ToString(includeNames = true)
@EqualsAndHashCode
class Candle {
    String pair
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    Date time
    Double open
    Double high
    Double low
    Double close
    Double vwap
    Double volume
    Long tradeCount
    String source
}
