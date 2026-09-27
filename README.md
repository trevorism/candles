# candles
![Build](https://github.com/trevorism/candles/actions/workflows/deploy.yml/badge.svg)
![GitHub last commit](https://img.shields.io/github/last-commit/trevorism/candles)
![GitHub language count](https://img.shields.io/github/languages/count/trevorism/candles)
![GitHub top language](https://img.shields.io/github/languages/top/trevorism/candles)

Stores hourly Kraken candles in BigQuery (`trevorism-trade:candles.hourly_candle`) and serves them at 1h, 4h, 1d, and 1w intervals.

`GET /candle/{pair}?interval=4h&from=2025-01-01&to=2026-01-01` returns closed candles only; dates are UTC and default to the last 500 candles.

Pairs are data, not config:
- `GET /pair` lists tracked pairs; `GET /pair/available?quote=USD` lists what Kraken offers.
- `POST /pair {"pair": "LTCUSD"}` tracks a pair and backfills its last 720 hourly candles (Kraken's REST limit).
- `DELETE /pair/{pair}` stops tracking; stored candles are kept.
- `POST /ingest` saves the last 48 closed hourly candles for every tracked pair; the `schedule` service calls it hourly.

# How to build
`gradle clean build`

History beyond Kraken's 720-candle REST window (admin only):
- `POST /import/archive {"sourceUri": "gs://trevorism-candles-archive/2026Q2/*USD_60.csv", "from": "2023-07-01"}` inserts missing hourly candles from Kraken's quarterly OHLCVT archive, read in place from GCS. Existing candles are never overwritten. The archive has no VWAP, so `(high+low+close)/3` is stored.
- `POST /import/trades/{pair}?date=2026-07-01` rebuilds one UTC day of hourly candles from Kraken trade history. Use it to close the gap between the archive's end and the REST window.

Every row records its `source`: `kraken-ohlc`, `kraken-trades`, or `kraken-archive`.
