package com.trevorism.model

enum CandleInterval {
    ONE_HOUR("1h", 3600, 0),
    FOUR_HOURS("4h", 14400, 0),
    ONE_DAY("1d", 86400, 0),
    ONE_WEEK("1w", 604800, 345600)

    final String label
    final long seconds
    final long offsetSeconds

    CandleInterval(String label, long seconds, long offsetSeconds) {
        this.label = label
        this.seconds = seconds
        this.offsetSeconds = offsetSeconds
    }

    Date bucketStart(Date date) {
        long epochSeconds = Math.floorDiv(date.time, 1000L)
        long bucketSeconds = Math.floorDiv(epochSeconds - offsetSeconds, seconds) * seconds + offsetSeconds
        return new Date(bucketSeconds * 1000L)
    }

    Date minus(Date date, int count) {
        return new Date(date.time - count * seconds * 1000L)
    }

    static CandleInterval fromLabel(String label) {
        CandleInterval interval = values().find { it.label == label?.toLowerCase() }
        if (!interval) {
            throw new IllegalArgumentException("Unsupported interval '${label}'; expected one of ${values()*.label}")
        }
        return interval
    }
}
