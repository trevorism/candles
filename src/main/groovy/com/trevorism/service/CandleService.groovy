package com.trevorism.service

import com.trevorism.model.Candle
import com.trevorism.model.CandleInterval
import com.trevorism.model.CandleSource
import jakarta.inject.Inject

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeParseException

@jakarta.inject.Singleton
class CandleService {

    static final int DEFAULT_CANDLE_COUNT = 500
    static final int ARCHIVE_DEFAULT_DAYS = 3 * 365
    private static final String PAIR_PATTERN = /^[A-Z0-9]{2,20}$/

    private final CandleRepository candleRepository
    private Closure<Date> clock = { new Date() }

    @Inject
    CandleService(CandleRepository candleRepository) {
        this.candleRepository = candleRepository
    }

    List<Candle> getCandles(String pair, String intervalLabel, String from, String to) {
        String normalizedPair = normalizePair(pair)
        CandleInterval interval = CandleInterval.fromLabel(intervalLabel ?: CandleInterval.ONE_HOUR.label)
        Date now = clock.call()
        Date requestedTo = to ? parseDate(to) : now
        Date end = interval.bucketStart(requestedTo.before(now) ? requestedTo : now)
        Date start = from ? interval.bucketStart(parseDate(from)) : interval.minus(end, DEFAULT_CANDLE_COUNT)
        if (!start.before(end)) {
            return []
        }
        return candleRepository.find(normalizedPair, interval, start, end)
    }

    void saveHourlyCandles(List<Candle> candles) {
        List<Candle> cleaned = candles.collect { Candle candle -> normalizeHourlyCandle(candle) }
        List<Candle> deduplicated = cleaned.groupBy { [it.pair, it.time] }.values().collect { it.last() }
        candleRepository.save(deduplicated)
    }

    long importArchive(String sourceUri, String from) {
        String uri = sourceUri?.trim()
        if (!uri || !uri.startsWith("gs://") || !uri.endsWith(CandleSql.ARCHIVE_FILE_SUFFIX)) {
            throw new IllegalArgumentException("sourceUri must be a gs:// path to Kraken hourly files ending in ${CandleSql.ARCHIVE_FILE_SUFFIX}, e.g. gs://bucket/2026Q2/*USD${CandleSql.ARCHIVE_FILE_SUFFIX}")
        }
        Date start = from ? parseDate(from) : CandleInterval.ONE_DAY.minus(clock.call(), ARCHIVE_DEFAULT_DAYS)
        return candleRepository.importArchive(uri, CandleInterval.ONE_DAY.bucketStart(start))
    }

    static String normalizePair(String pair) {
        String normalized = pair?.trim()?.toUpperCase()
        if (!normalized || !(normalized ==~ PAIR_PATTERN)) {
            throw new IllegalArgumentException("Invalid pair '${pair}'")
        }
        return normalized
    }

    static Date parseDate(String value) {
        try {
            return Date.from(Instant.parse(value))
        } catch (DateTimeParseException ignored) {
            try {
                return Date.from(LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant())
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("Invalid date '${value}'; expected yyyy-MM-dd or yyyy-MM-ddTHH:mm:ssZ")
            }
        }
    }

    static Candle normalizeHourlyCandle(Candle candle) {
        if (candle.time == null || [candle.open, candle.high, candle.low, candle.close, candle.volume].contains(null)) {
            throw new IllegalArgumentException("Candle is missing required fields: ${candle}")
        }
        return new Candle(
                pair: normalizePair(candle.pair),
                time: CandleInterval.ONE_HOUR.bucketStart(candle.time),
                open: candle.open,
                high: candle.high,
                low: candle.low,
                close: candle.close,
                vwap: candle.vwap ?: candle.close,
                volume: candle.volume,
                tradeCount: candle.tradeCount ?: 0L,
                source: candle.source ?: CandleSource.KRAKEN_OHLC
        )
    }
}
