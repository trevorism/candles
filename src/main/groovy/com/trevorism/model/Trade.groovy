package com.trevorism.model

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

@ToString(includeNames = true)
@EqualsAndHashCode
class Trade {
    Date time
    double price
    double volume
}
