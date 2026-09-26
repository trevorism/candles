package com.trevorism.model

import com.fasterxml.jackson.annotation.JsonFormat
import groovy.transform.ToString

@ToString(includeNames = true)
class TrackedPair {
    String id
    String pair
    String baseName
    String quoteName
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    Date createdDate
}
