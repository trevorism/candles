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
