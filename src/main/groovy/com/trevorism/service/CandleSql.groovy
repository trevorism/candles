package com.trevorism.service

class CandleSql {

    static final String DATASET = "candles"
    static final String HOURLY_TABLE = "hourly_candle"

    static String merge() {
        return """
MERGE `${DATASET}.${HOURLY_TABLE}` T
USING (
  SELECT
    pair,
    TIMESTAMP_MICROS(@times[OFFSET(i)]) AS time,
    @opens[OFFSET(i)] AS open,
    @highs[OFFSET(i)] AS high,
    @lows[OFFSET(i)] AS low,
    @closes[OFFSET(i)] AS close,
    @vwaps[OFFSET(i)] AS vwap,
    @volumes[OFFSET(i)] AS volume,
    @tradeCounts[OFFSET(i)] AS tradeCount
  FROM UNNEST(@pairs) AS pair WITH OFFSET i
) S
ON T.pair = S.pair AND T.time = S.time AND T.time BETWEEN @minTime AND @maxTime
WHEN MATCHED THEN UPDATE SET
  open = S.open, high = S.high, low = S.low, close = S.close,
  vwap = S.vwap, volume = S.volume, tradeCount = S.tradeCount, ingestedAt = CURRENT_TIMESTAMP()
WHEN NOT MATCHED THEN INSERT (pair, time, open, high, low, close, vwap, volume, tradeCount, ingestedAt)
  VALUES (S.pair, S.time, S.open, S.high, S.low, S.close, S.vwap, S.volume, S.tradeCount, CURRENT_TIMESTAMP())
"""
    }

    static String aggregate() {
        return """
SELECT
  pair,
  TIMESTAMP_SECONDS(DIV(UNIX_SECONDS(time) - @offsetSeconds, @bucketSeconds) * @bucketSeconds + @offsetSeconds) AS bucket,
  ARRAY_AGG(open ORDER BY time ASC LIMIT 1)[OFFSET(0)] AS open,
  MAX(high) AS high,
  MIN(low) AS low,
  ARRAY_AGG(close ORDER BY time DESC LIMIT 1)[OFFSET(0)] AS close,
  SAFE_DIVIDE(SUM(vwap * volume), SUM(volume)) AS vwap,
  SUM(volume) AS volume,
  SUM(tradeCount) AS tradeCount
FROM `${DATASET}.${HOURLY_TABLE}`
WHERE pair = @pair AND time >= @from AND time < @to
GROUP BY pair, bucket
ORDER BY bucket
"""
    }
}
