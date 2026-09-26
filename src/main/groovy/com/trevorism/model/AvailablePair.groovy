package com.trevorism.model

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

@ToString(includeNames = true)
@EqualsAndHashCode
class AvailablePair {
    String pair
    String baseName
    String quoteName
}
