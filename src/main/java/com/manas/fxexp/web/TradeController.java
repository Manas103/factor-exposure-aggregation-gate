package com.manas.fxexp.web;

import com.manas.fxexp.service.ExposureAggregationService;
import com.manas.fxexp.web.dto.TradeEventRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** The single write path: a trade create or update event. Every call here
 * synchronously updates the strategy and fund aggregates and re-checks
 * limits before returning (see ExposureAggregationService); there is no
 * separate polling or batch recomputation job anywhere in this service. */
@RestController
public class TradeController {

    private final ExposureAggregationService exposureAggregationService;

    public TradeController(ExposureAggregationService exposureAggregationService) {
        this.exposureAggregationService = exposureAggregationService;
    }

    @PostMapping("/trades")
    public ResponseEntity<Void> submitTradeEvent(@RequestBody TradeEventRequest req) {
        exposureAggregationService.applyTradeEvent(req.externalId(), req.strategyId(), req.fundId(),
                req.instrument(), req.spotExposure(), req.volExposure(), req.skewExposure(), req.rateExposure(),
                req.dividendExposure());
        return ResponseEntity.ok().build();
    }
}
