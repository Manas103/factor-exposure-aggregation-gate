package com.manas.fxexp.web;

import com.manas.fxexp.repo.FundAggregateRepository;
import com.manas.fxexp.repo.StrategyAggregateRepository;
import com.manas.fxexp.web.dto.AggregateView;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** The read path this service's latency and throughput claims are about:
 * each endpoint is a single indexed-primary-key row lookup against an
 * already-materialized aggregate, not a sum over trades. */
@RestController
public class AggregateController {

    private final StrategyAggregateRepository strategyAggregateRepository;
    private final FundAggregateRepository fundAggregateRepository;

    public AggregateController(StrategyAggregateRepository strategyAggregateRepository,
                                FundAggregateRepository fundAggregateRepository) {
        this.strategyAggregateRepository = strategyAggregateRepository;
        this.fundAggregateRepository = fundAggregateRepository;
    }

    @GetMapping("/aggregates/strategy/{id}")
    public ResponseEntity<AggregateView> getStrategyAggregate(@PathVariable String id) {
        return strategyAggregateRepository.findByStrategyId(id)
                .map(a -> ResponseEntity.ok(AggregateView.of(a)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/aggregates/fund/{id}")
    public ResponseEntity<AggregateView> getFundAggregate(@PathVariable String id) {
        return fundAggregateRepository.findById(id)
                .map(a -> ResponseEntity.ok(AggregateView.of(a)))
                .orElse(ResponseEntity.notFound().build());
    }
}
