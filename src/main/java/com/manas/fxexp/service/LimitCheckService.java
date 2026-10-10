package com.manas.fxexp.service;

import com.manas.fxexp.domain.Breach;
import com.manas.fxexp.domain.FactorType;
import com.manas.fxexp.domain.FundAggregate;
import com.manas.fxexp.domain.RiskLimit;
import com.manas.fxexp.domain.ScopeType;
import com.manas.fxexp.domain.StrategyAggregate;
import com.manas.fxexp.domain.Trade;
import com.manas.fxexp.repo.BreachRepository;
import com.manas.fxexp.repo.FundAggregateRepository;
import com.manas.fxexp.repo.RiskLimitRepository;
import com.manas.fxexp.repo.StrategyAggregateRepository;
import com.manas.fxexp.repo.TradeRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Re-checks one scope's aggregates against every configured limit for that
 * scope. A breach is always named by the single trade with the
 * largest-magnitude exposure to the breached factor, within the breached
 * scope, at detection time: a deterministic, order-independent answer to
 * "which trade is behind this", not whichever trade's event happened to be
 * the one that tipped the aggregate over the line.
 */
@Service
public class LimitCheckService {

    private final RiskLimitRepository riskLimitRepository;
    private final BreachRepository breachRepository;
    private final StrategyAggregateRepository strategyAggregateRepository;
    private final FundAggregateRepository fundAggregateRepository;
    private final TradeRepository tradeRepository;

    public LimitCheckService(RiskLimitRepository riskLimitRepository, BreachRepository breachRepository,
                              StrategyAggregateRepository strategyAggregateRepository,
                              FundAggregateRepository fundAggregateRepository,
                              TradeRepository tradeRepository) {
        this.riskLimitRepository = riskLimitRepository;
        this.breachRepository = breachRepository;
        this.strategyAggregateRepository = strategyAggregateRepository;
        this.fundAggregateRepository = fundAggregateRepository;
        this.tradeRepository = tradeRepository;
    }

    @Transactional
    public void checkScope(ScopeType scopeType, String scopeId) {
        List<RiskLimit> limits = riskLimitRepository.findByScopeTypeAndScopeId(scopeType, scopeId);
        if (limits.isEmpty()) return;

        for (RiskLimit limit : limits) {
            BigDecimal aggregateValue = currentExposure(scopeType, scopeId, limit.getFactor());
            Optional<Breach> existing =
                    breachRepository.findByScopeTypeAndScopeIdAndFactorAndActiveTrue(scopeType, scopeId,
                            limit.getFactor());

            if (aggregateValue.abs().compareTo(limit.getThreshold()) > 0) {
                Trade biggest = largestContributor(scopeType, scopeId, limit.getFactor());
                String tradeId = biggest == null ? null : biggest.getExternalId();
                BigDecimal contribution = biggest == null ? BigDecimal.ZERO : biggest.exposure(limit.getFactor());
                if (existing.isPresent()) {
                    existing.get().refresh(aggregateValue, tradeId, contribution);
                    breachRepository.save(existing.get());
                } else {
                    breachRepository.save(new Breach(scopeType, scopeId, limit.getFactor(), aggregateValue,
                            limit.getThreshold(), tradeId, contribution));
                }
            } else {
                existing.ifPresent(b -> {
                    b.resolve();
                    breachRepository.save(b);
                });
            }
        }
    }

    private BigDecimal currentExposure(ScopeType scopeType, String scopeId, FactorType factor) {
        if (scopeType == ScopeType.STRATEGY) {
            StrategyAggregate agg = strategyAggregateRepository.findByStrategyId(scopeId)
                    .orElseThrow(() -> new IllegalStateException("no strategy aggregate for " + scopeId));
            return agg.exposure(factor);
        }
        FundAggregate agg = fundAggregateRepository.findById(scopeId)
                .orElseThrow(() -> new IllegalStateException("no fund aggregate for " + scopeId));
        return agg.exposure(factor);
    }

    private Trade largestContributor(ScopeType scopeType, String scopeId, FactorType factor) {
        List<Trade> trades = scopeType == ScopeType.STRATEGY
                ? tradeRepository.findByStrategyId(scopeId)
                : tradeRepository.findByFundId(scopeId);
        Trade best = null;
        BigDecimal bestAbs = BigDecimal.ZERO.subtract(BigDecimal.ONE); // -1, so any non-negative abs wins
        for (Trade t : trades) {
            BigDecimal abs = t.exposure(factor).abs();
            if (abs.compareTo(bestAbs) > 0) {
                best = t;
                bestAbs = abs;
            }
        }
        return best;
    }
}
