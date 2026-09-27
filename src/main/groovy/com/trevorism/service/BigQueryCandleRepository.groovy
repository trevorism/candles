package com.trevorism.service

import com.google.cloud.bigquery.*
import com.trevorism.model.Candle
import com.trevorism.model.CandleInterval

import java.util.concurrent.TimeUnit

@jakarta.inject.Singleton
class BigQueryCandleRepository implements CandleRepository {

    static final String PROJECT_ID = "trevorism-trade"
    static final int MERGE_BATCH_SIZE = 5000
    private static final int ALREADY_EXISTS = 409

    private BigQuery bigquery
    private volatile boolean tableReady

    private BigQuery getClient() {
        if (bigquery == null) {
            bigquery = BigQueryOptions.newBuilder().setProjectId(PROJECT_ID).build().getService()
        }
        return bigquery
    }

    @Override
    void save(List<Candle> hourlyCandles) {
        if (!hourlyCandles) {
            return
        }
        ensureTableExists()
        hourlyCandles.collate(MERGE_BATCH_SIZE).each { List<Candle> batch ->
            client.query(buildMergeJob(batch))
        }
    }

    @Override
    List<Candle> find(String pair, CandleInterval interval, Date from, Date to) {
        ensureTableExists()
        TableResult result = client.query(buildAggregateJob(pair, interval, from, to))
        return result.iterateAll().collect { FieldValueList row -> toCandle(row) }
    }

    @Override
    long importArchive(String sourceUri, Date from) {
        ensureTableExists()
        Job job = client.create(JobInfo.of(buildArchiveMergeJob(sourceUri, from))).waitFor()
        if (job?.status?.error) {
            throw new IllegalStateException("Archive import failed: ${job.status.error.message}")
        }
        return ((JobStatistics.QueryStatistics) job.statistics).numDmlAffectedRows ?: 0L
    }

    static QueryJobConfiguration buildArchiveMergeJob(String sourceUri, Date from) {
        Schema archiveSchema = Schema.of(
                Field.of("ts", StandardSQLTypeName.INT64),
                Field.of("open", StandardSQLTypeName.FLOAT64),
                Field.of("high", StandardSQLTypeName.FLOAT64),
                Field.of("low", StandardSQLTypeName.FLOAT64),
                Field.of("close", StandardSQLTypeName.FLOAT64),
                Field.of("volume", StandardSQLTypeName.FLOAT64),
                Field.of("trades", StandardSQLTypeName.INT64)
        )
        ExternalTableDefinition archive = ExternalTableDefinition.of(sourceUri, archiveSchema,
                CsvOptions.newBuilder().setSkipLeadingRows(0).build())
        return QueryJobConfiguration.newBuilder(CandleSql.mergeArchive())
                .addTableDefinition(CandleSql.ARCHIVE_TABLE, archive)
                .addNamedParameter("fromSeconds", QueryParameterValue.int64(TimeUnit.MILLISECONDS.toSeconds(from.time)))
                .build()
    }

    static QueryJobConfiguration buildMergeJob(List<Candle> batch) {
        return QueryJobConfiguration.newBuilder(CandleSql.merge())
                .addNamedParameter("pairs", QueryParameterValue.array(batch*.pair as String[], String))
                .addNamedParameter("times", QueryParameterValue.array(batch.collect { toMicros(it.time) } as Long[], Long))
                .addNamedParameter("opens", QueryParameterValue.array(batch*.open as Double[], Double))
                .addNamedParameter("highs", QueryParameterValue.array(batch*.high as Double[], Double))
                .addNamedParameter("lows", QueryParameterValue.array(batch*.low as Double[], Double))
                .addNamedParameter("closes", QueryParameterValue.array(batch*.close as Double[], Double))
                .addNamedParameter("vwaps", QueryParameterValue.array(batch*.vwap as Double[], Double))
                .addNamedParameter("volumes", QueryParameterValue.array(batch*.volume as Double[], Double))
                .addNamedParameter("tradeCounts", QueryParameterValue.array(batch*.tradeCount as Long[], Long))
                .addNamedParameter("sources", QueryParameterValue.array(batch*.source as String[], String))
                .addNamedParameter("minTime", QueryParameterValue.timestamp(toMicros(batch*.time.min())))
                .addNamedParameter("maxTime", QueryParameterValue.timestamp(toMicros(batch*.time.max())))
                .build()
    }

    static QueryJobConfiguration buildAggregateJob(String pair, CandleInterval interval, Date from, Date to) {
        return QueryJobConfiguration.newBuilder(CandleSql.aggregate())
                .addNamedParameter("pair", QueryParameterValue.string(pair))
                .addNamedParameter("bucketSeconds", QueryParameterValue.int64(interval.seconds))
                .addNamedParameter("offsetSeconds", QueryParameterValue.int64(interval.offsetSeconds))
                .addNamedParameter("from", QueryParameterValue.timestamp(toMicros(from)))
                .addNamedParameter("to", QueryParameterValue.timestamp(toMicros(to)))
                .build()
    }

    static Candle toCandle(FieldValueList row) {
        new Candle(
                pair: row.get("pair").stringValue,
                time: new Date(TimeUnit.MICROSECONDS.toMillis(row.get("bucket").timestampValue)),
                open: doubleOrNull(row.get("open")),
                high: doubleOrNull(row.get("high")),
                low: doubleOrNull(row.get("low")),
                close: doubleOrNull(row.get("close")),
                vwap: doubleOrNull(row.get("vwap")),
                volume: doubleOrNull(row.get("volume")),
                tradeCount: row.get("tradeCount").isNull() ? null : row.get("tradeCount").longValue
        )
    }

    static TableInfo hourlyTableInfo() {
        StandardTableDefinition definition = StandardTableDefinition.newBuilder()
                .setSchema(hourlySchema())
                .setTimePartitioning(TimePartitioning.newBuilder(TimePartitioning.Type.MONTH).setField("time").build())
                .setClustering(Clustering.newBuilder().setFields(["pair", "time"]).build())
                .build()
        return TableInfo.of(TableId.of(CandleSql.DATASET, CandleSql.HOURLY_TABLE), definition)
    }

    static List<Field> missingFields(Schema current) {
        Set<String> existingNames = current.fields*.name as Set
        return hourlySchema().fields.findAll { !(it.name in existingNames) }
    }

    static Schema hourlySchema() {
        return Schema.of(
                Field.newBuilder("pair", StandardSQLTypeName.STRING).setMode(Field.Mode.REQUIRED).build(),
                Field.newBuilder("time", StandardSQLTypeName.TIMESTAMP).setMode(Field.Mode.REQUIRED).build(),
                Field.of("open", StandardSQLTypeName.FLOAT64),
                Field.of("high", StandardSQLTypeName.FLOAT64),
                Field.of("low", StandardSQLTypeName.FLOAT64),
                Field.of("close", StandardSQLTypeName.FLOAT64),
                Field.of("vwap", StandardSQLTypeName.FLOAT64),
                Field.of("volume", StandardSQLTypeName.FLOAT64),
                Field.of("tradeCount", StandardSQLTypeName.INT64),
                Field.of("ingestedAt", StandardSQLTypeName.TIMESTAMP),
                Field.of("source", StandardSQLTypeName.STRING)
        )
    }

    private void ensureTableExists() {
        if (tableReady) {
            return
        }
        if (client.getDataset(DatasetId.of(CandleSql.DATASET)) == null) {
            createIgnoringConflict { client.create(DatasetInfo.of(CandleSql.DATASET)) }
        }
        Table table = client.getTable(TableId.of(CandleSql.DATASET, CandleSql.HOURLY_TABLE))
        if (table == null) {
            createIgnoringConflict { client.create(hourlyTableInfo()) }
        } else {
            addMissingColumns(table)
        }
        tableReady = true
    }

    private static void addMissingColumns(Table table) {
        Schema current = table.getDefinition().getSchema()
        List<Field> missing = missingFields(current)
        if (missing) {
            Schema updated = Schema.of(current.fields.toList() + missing)
            table.toBuilder().setDefinition(table.getDefinition().toBuilder().setSchema(updated).build()).build().update()
        }
    }

    private static void createIgnoringConflict(Closure creation) {
        try {
            creation.call()
        } catch (BigQueryException e) {
            if (e.code != ALREADY_EXISTS) {
                throw e
            }
        }
    }

    private static Long toMicros(Date date) {
        return TimeUnit.MILLISECONDS.toMicros(date.time)
    }

    private static Double doubleOrNull(FieldValue value) {
        return value.isNull() ? null : value.doubleValue
    }
}
