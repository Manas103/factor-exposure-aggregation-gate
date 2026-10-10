package com.manas.fxexp.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.manas.fxexp.domain.FactorType;
import com.manas.fxexp.domain.RiskLimit;
import com.manas.fxexp.domain.ScopeType;
import com.manas.fxexp.repo.BreachRepository;
import com.manas.fxexp.repo.RiskLimitRepository;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Every limit breach names the trade and factor behind it, and "the
 * trade" always means the single largest-magnitude contributor to that
 * factor in that scope, not whichever trade's event happened to trigger
 * the check. */
@SpringBootTest
class LimitBreachAttributionTest {

    @Autowired
    private ExposureAggregationService exposureAggregationService;
    @Autowired
    private RiskLimitRepository riskLimitRepository;
    @Autowired
    private BreachRepository breachRepository;

    @Test
    void breachNamesTheLargestContributingTradeAndTheBreachedFactor() {
        String fundId = "FUND-" + UUID.randomUUID();
        String strategyId = "STRAT-" + UUID.randomUUID();
        riskLimitRepository.save(new RiskLimit(ScopeType.STRATEGY, strategyId, FactorType.SPOT,
                BigDecimal.valueOf(100_000)));

        // Three trades; the second is deliberately the largest spot exposure,
        // so a correct implementation must name it, not the first or the
        // third (which was applied last, the event that actually tipped the
        // check, but is not the biggest contributor).
        exposureAggregationService.applyTradeEvent("T1-" + fundId, strategyId, fundId, "INSTR",
                BigDecimal.valueOf(20_000), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        exposureAggregationService.applyTradeEvent("T2-" + fundId, strategyId, fundId, "INSTR",
                BigDecimal.valueOf(70_000), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        exposureAggregationService.applyTradeEvent("T3-" + fundId, strategyId, fundId, "INSTR",
                BigDecimal.valueOf(15_000), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        // aggregate spot = 105,000 > 100,000 limit -> breach, after T3's event

        Optional<com.manas.fxexp.domain.Breach> breach =
                breachRepository.findByScopeTypeAndScopeIdAndFactorAndActiveTrue(ScopeType.STRATEGY, strategyId,
                        FactorType.SPOT);
        assertThat(breach).isPresent();
        assertThat(breach.get().getTriggeringTradeExternalId()).isEqualTo("T2-" + fundId);
        assertThat(breach.get().getTriggeringTradeContribution()).isEqualByComparingTo(BigDecimal.valueOf(70_000));
        assertThat(breach.get().getAggregateValue()).isEqualByComparingTo(BigDecimal.valueOf(105_000));
        System.out.println("breach named factor=" + breach.get().getFactor() + " trade="
                + breach.get().getTriggeringTradeExternalId() + " (the largest contributor, not T3 which "
                + "triggered the check or T1 which was booked first)");

        // Reduce T2 so the strategy falls back under the limit: the breach
        // must resolve (go inactive), not linger.
        exposureAggregationService.applyTradeEvent("T2-" + fundId, strategyId, fundId, "INSTR",
                BigDecimal.valueOf(5_000), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        Optional<com.manas.fxexp.domain.Breach> resolved =
                breachRepository.findByScopeTypeAndScopeIdAndFactorAndActiveTrue(ScopeType.STRATEGY, strategyId,
                        FactorType.SPOT);
        assertThat(resolved).isEmpty();
    }
}
