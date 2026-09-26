package com.trevorism.model

import org.junit.jupiter.api.Test

import java.time.Instant

import static org.junit.jupiter.api.Assertions.assertThrows

class CandleIntervalTest {

    private static Date utc(String instant) {
        Date.from(Instant.parse(instant))
    }

    @Test
    void testHourBucketTruncatesToTopOfHour() {
        assert CandleInterval.ONE_HOUR.bucketStart(utc("2026-09-26T13:45:10Z")) == utc("2026-09-26T13:00:00Z")
    }

    @Test
    void testFourHourBucketAlignsToUtcMidnight() {
        assert CandleInterval.FOUR_HOURS.bucketStart(utc("2026-09-26T03:59:59Z")) == utc("2026-09-26T00:00:00Z")
        assert CandleInterval.FOUR_HOURS.bucketStart(utc("2026-09-26T13:00:00Z")) == utc("2026-09-26T12:00:00Z")
    }

    @Test
    void testDayBucket() {
        assert CandleInterval.ONE_DAY.bucketStart(utc("2026-09-26T23:59:59Z")) == utc("2026-09-26T00:00:00Z")
    }

    @Test
    void testWeekBucketStartsOnMonday() {
        assert CandleInterval.ONE_WEEK.bucketStart(utc("2026-09-26T10:00:00Z")) == utc("2026-09-21T00:00:00Z")
        assert CandleInterval.ONE_WEEK.bucketStart(utc("2026-09-21T00:00:00Z")) == utc("2026-09-21T00:00:00Z")
        assert CandleInterval.ONE_WEEK.bucketStart(utc("2026-09-20T23:59:59Z")) == utc("2026-09-14T00:00:00Z")
    }

    @Test
    void testMinus() {
        assert CandleInterval.FOUR_HOURS.minus(utc("2026-09-26T12:00:00Z"), 3) == utc("2026-09-26T00:00:00Z")
    }

    @Test
    void testFromLabel() {
        assert CandleInterval.fromLabel("4H") == CandleInterval.FOUR_HOURS
        assert CandleInterval.fromLabel("1w") == CandleInterval.ONE_WEEK
    }

    @Test
    void testFromLabelRejectsUnknownInterval() {
        assertThrows(IllegalArgumentException, { CandleInterval.fromLabel("15m") })
    }
}
