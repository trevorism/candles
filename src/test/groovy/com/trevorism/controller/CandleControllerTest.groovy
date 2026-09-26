package com.trevorism.controller

import com.trevorism.model.Candle
import com.trevorism.model.CandleInterval
import com.trevorism.service.CandleRepository
import com.trevorism.service.CandleService
import org.junit.jupiter.api.Test

class CandleControllerTest {

    @Test
    void testGetCandlesDelegatesToService() {
        Map received = [:]
        CandleRepository repository = [
                find: { String pair, CandleInterval interval, Date from, Date to ->
                    received = [pair: pair, interval: interval]
                    return [new Candle(pair: pair)]
                }
        ] as CandleRepository
        CandleController controller = new CandleController(candleService: new CandleService(repository))

        List<Candle> result = controller.getCandles("ltcusd", "4h", Optional.of("2026-01-01"), Optional.empty())

        assert result*.pair == ["LTCUSD"]
        assert received == [pair: "LTCUSD", interval: CandleInterval.FOUR_HOURS]
    }
}
