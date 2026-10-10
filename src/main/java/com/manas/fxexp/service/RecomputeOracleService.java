package com.manas.fxexp.service;

import com.manas.fxexp.domain.FactorType;
import com.manas.fxexp.repo.TradeRepository;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Independent, from-scratch recomputation: sums every trade's exposures
 * directly from the trades table, sharing no code path with
 * ExposureAggregationService's incremental delta updates. This is the slow
 * oracle the fast incrementally-maintained aggregate is diffed against
 * (ExposureIncrementalVsOracleTest), and it is also what
 * CommentaryPublishGate calls to verify a generated commentary's numbers,
 * deliberately not trusting the materialized aggregate row the commentary
 * may have been generated from, in case that row and the trades table have
 * ever drifted apart.
 */
@Service
public class RecomputeOracleService {

    private final TradeRepository tradeRepository;

    public RecomputeOracleService(TradeRepository tradeRepository) {
        this.tradeRepository = tradeRepository;
    }

    public Map<FactorType, BigDecimal> recomputeStrategy(String strategyId) {
        return sum(tradeRepository.findByStrategyId(strategyId));
    }

    public Map<FactorType, BigDecimal> recomputeFund(String fundId) {
        return sum(tradeRepository.findByFundId(fundId));
    }

    private Map<FactorType, BigDecimal> sum(List<com.manas.fxexp.domain.Trade> trades) {
        Map<FactorType, BigDecimal> totals = new EnumMap<>(FactorType.class);
        for (FactorType f : FactorType.values()) totals.put(f, BigDecimal.ZERO);
        for (com.manas.fxexp.domain.Trade t : trades) {
            for (FactorType f : FactorType.values()) {
                totals.put(f, totals.get(f).add(t.exposure(f)));
            }
        }
        return totals;
    }
}
