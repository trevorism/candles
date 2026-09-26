Feature: Candle data
  In order to keep market data private to Trevorism, candle routes require authentication

  Scenario: Anonymous request for candles is rejected
    Given the candles application is alive
    When an anonymous client requests hourly candles for "LTCUSD"
    Then the request is rejected as unauthorized

  Scenario: Anonymous request to ingest candles is rejected
    Given the candles application is alive
    When an anonymous client posts to "/ingest"
    Then the request is rejected as unauthorized

  Scenario: Anonymous request to track a pair is rejected
    Given the candles application is alive
    When an anonymous client posts to "/pair"
    Then the request is rejected as unauthorized
