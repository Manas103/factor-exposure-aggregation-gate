package com.manas.fxexp.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.manas.fxexp.domain.FactorType;
import com.manas.fxexp.repo.FundAggregateRepository;
import com.manas.fxexp.repo.StrategyAggregateRepository;
import java.math.BigDecimal;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * The correctness invariant this service's "event-driven, not polling"
 * design depends on: after an arbitrary sequence of trade creates and
 * updates, the incrementally-maintained strategy and fund aggregates
 * (never recomputed from scratch on read) must agree exactly with a
 * from-scratch sum of the trades table (RecomputeOracleService), which
 * shares no code with the incremental update path.
 */
@SpringBootTest
class ExposureIncrementalVsOracleTest {

    @Autowired
    private ExposureAggregationService exposureAggregationService;
    @Autowired
    private RecomputeOracleService recomputeOracleService;
    @Autowired
    private StrategyAggregateRepository strategyAggregateRepository;
    @Autowired
    private FundAggregateRepository fundAggregateRepository;

    @Test
    void incrementalAggregateMatchesFromScratchOracleAfterManyRandomUpdates() {
        String fundId = "FUND-" + UUID.randomUUID();
        String strategyA = "STRAT-A-" + UUID.randomUUID();
        String strategyB = "STRAT-B-" + UUID.randomUUID();
        Random rng = new Random(42);

        String[] externalIds = new String[20];
        for (int i = 0; i < externalIds.length; i++) externalIds[i] = "TRD-" + UUID.randomUUID();

        for (int round = 0; round < 200; round++) {
            String externalId = externalIds[rng.nextInt(externalIds.length)];
            String strategyId = rng.nextBoolean() ? strategyA : strategyB;
            BigDecimal spot = randomExposure(rng);
            BigDecimal vol = randomExposure(rng);
            BigDecimal skew = randomExposure(rng);
            BigDecimal rate = randomExposure(rng);
            BigDecimal dividend = randomExposure(rng);

            exposureAggregationService.applyTradeEvent(externalId, strategyId, fundId, "INSTR",
                    spot, vol, skew, rate, dividend);
        }

        var incrementalA = strategyAggregateRepository.findByStrategyId(strategyA).orElseThrow();
        var incrementalB = strategyAggregateRepository.findByStrategyId(strategyB).orElseThrow();
        var incrementalFund = fundAggregateRepository.findById(fundId).orElseThrow();

        var oracleA = recomputeOracleService.recomputeStrategy(strategyA);
        var oracleB = recomputeOracleService.recomputeStrategy(strategyB);
        var oracleFund = recomputeOracleService.recomputeFund(fundId);

        for (FactorType f : FactorType.values()) {
            assertThat(incrementalA.exposure(f)).as("strategy A, " + f).isEqualByComparingTo(oracleA.get(f));
            assertThat(incrementalB.exposure(f)).as("strategy B, " + f).isEqualByComparingTo(oracleB.get(f));
            assertThat(incrementalFund.exposure(f)).as("fund, " + f).isEqualByComparingTo(oracleFund.get(f));
        }
        System.out.println("200 trade events (20 external ids, 2 strategies, random strategy reassignment) "
                + "across 5 factors: incremental aggregate == from-scratch oracle, exactly, for all 15 "
                + "(2 strategies + 1 fund) x 5 factor comparisons");
    }

    private static BigDecimal randomExposure(Random rng) {
        return BigDecimal.valueOf(rng.nextInt(200_000) - 100_000).setScale(2);
    }
}
