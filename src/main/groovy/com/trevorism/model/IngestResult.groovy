package com.trevorism.model

import com.fasterxml.jackson.annotation.JsonFormat
import groovy.transform.ToString

@ToString(includeNames = true)
class IngestResult {
    String pair
    int candlesSaved
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    Date latestCandleTime
    String error
}
