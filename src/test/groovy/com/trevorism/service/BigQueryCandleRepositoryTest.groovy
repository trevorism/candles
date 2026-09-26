package com.trevorism.service

import com.google.cloud.bigquery.*
import com.trevorism.model.Candle
import com.trevorism.model.CandleInterval
import org.junit.jupiter.api.Test

import java.time.Instant

class BigQueryCandleRepositoryTest {

    private static Date utc(String instant) {
        Date.from(Instant.parse(instant))
    }

    @Test
    void testMergeJobBindsParallelArraysAndPartitionBounds() {
        List<Candle> batch = [
                new Candle(pair: "LTCUSD", time: utc("2026-09-26T11:00:00Z"), open: 1, high: 2, low: 0.5, close: 1.5, vwap: 1.2, volume: 10, tradeCount: 3),
                new Candle(pair: "SOLUSD", time: utc("2026-09-26T10:00:00Z"), open: 5, high: 6, low: 4, close: 5.5, vwap: 5.1, volume: 20, tradeCount: 7)
        ]

        QueryJobConfiguration job = BigQueryCandleRepository.buildMergeJob(batch)
        Map<String, QueryParameterValue> parameters = job.namedParameters

        assert job.query.contains("MERGE `candles.hourly_candle`")
        assert parameters.pairs.arrayValues*.value == ["LTCUSD", "SOLUSD"]
        assert parameters.closes.arrayValues*.value == ["1.5", "5.5"]
        assert parameters.tradeCounts.arrayValues*.value == ["3", "7"]
        assert parameters.keySet().containsAll(["times", "opens", "highs", "lows", "vwaps", "volumes"])
        assert parameters.minTime.value.startsWith("2026-09-26 10:00:00")
        assert parameters.maxTime.value.startsWith("2026-09-26 11:00:00")
    }

    @Test
    void testAggregateJobBindsInterval() {
        QueryJobConfiguration job = BigQueryCandleRepository.buildAggregateJob("LTCUSD", CandleInterval.ONE_WEEK,
                utc("2026-01-05T00:00:00Z"), utc("2026-09-21T00:00:00Z"))
        Map<String, QueryParameterValue> parameters = job.namedParameters

        assert job.query.contains("FROM `candles.hourly_candle`")
        assert parameters.pair.value == "LTCUSD"
        assert parameters.bucketSeconds.value == "604800"
        assert parameters.offsetSeconds.value == "345600"
        assert parameters.from.value.startsWith("2026-01-05 00:00:00")
        assert parameters.to.value.startsWith("2026-09-21 00:00:00")
    }

    @Test
    void testToCandleMapsRow() {
        FieldList fields = FieldList.of(
                Field.of("pair", StandardSQLTypeName.STRING),
                Field.of("bucket", StandardSQLTypeName.TIMESTAMP),
                Field.of("open", StandardSQLTypeName.FLOAT64),
                Field.of("high", StandardSQLTypeName.FLOAT64),
                Field.of("low", StandardSQLTypeName.FLOAT64),
                Field.of("close", StandardSQLTypeName.FLOAT64),
                Field.of("vwap", StandardSQLTypeName.FLOAT64),
                Field.of("volume", StandardSQLTypeName.FLOAT64),
                Field.of("tradeCount", StandardSQLTypeName.INT64)
        )
        long bucketSeconds = utc("2026-09-26T12:00:00Z").time.intdiv(1000)
        List<FieldValue> values = [
                "LTCUSD", "${bucketSeconds}.000000", "1.0", "2.0", "0.5", "1.5", null, "10.0", "3"
        ].collect { FieldValue.of(FieldValue.Attribute.PRIMITIVE, it?.toString()) }

        Candle candle = BigQueryCandleRepository.toCandle(FieldValueList.of(values, fields))

        assert candle.pair == "LTCUSD"
        assert candle.time == utc("2026-09-26T12:00:00Z")
        assert candle.open == 1.0d
        assert candle.close == 1.5d
        assert candle.vwap == null
        assert candle.volume == 10.0d
        assert candle.tradeCount == 3L
    }

    @Test
    void testHourlyTableIsPartitionedByMonthAndClusteredByPair() {
        StandardTableDefinition definition = BigQueryCandleRepository.hourlyTableInfo().definition

        assert definition.timePartitioning.type == TimePartitioning.Type.MONTH
        assert definition.timePartitioning.field == "time"
        assert definition.clustering.fields == ["pair", "time"]
        assert definition.schema.fields*.name == ["pair", "time", "open", "high", "low", "close", "vwap", "volume", "tradeCount", "ingestedAt"]
    }

    @Test
    void testSaveOfEmptyListDoesNotTouchBigQuery() {
        new BigQueryCandleRepository().save([])
    }
}
