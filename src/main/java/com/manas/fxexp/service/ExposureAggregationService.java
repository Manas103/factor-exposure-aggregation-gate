package com.manas.fxexp.service;

import com.manas.fxexp.domain.FactorType;
import com.manas.fxexp.domain.FundAggregate;
import com.manas.fxexp.domain.Strategy;
import com.manas.fxexp.domain.StrategyAggregate;
import com.manas.fxexp.domain.Trade;
import com.manas.fxexp.repo.FundAggregateRepository;
import com.manas.fxexp.repo.StrategyAggregateRepository;
import com.manas.fxexp.repo.StrategyRepository;
import com.manas.fxexp.repo.TradeRepository;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Event-driven position updates: a trade create or update is applied here,
 * synchronously, in the same request that delivered it. There is no
 * scheduled job anywhere in this service that walks the trades table and
 * recomputes aggregates on a timer; the strategy and fund aggregate rows
 * are adjusted by exactly the delta this one event introduced, which is
 * what keeps a read of an aggregate a single indexed row lookup (see
 * AggregateController) rather than a sum over however many trades a
 * strategy or fund holds.
 */
@Service
public class ExposureAggregationService {

    private final TradeRepository tradeRepository;
    private final StrategyRepository strategyRepository;
    private final StrategyAggregateRepository strategyAggregateRepository;
    private final FundAggregateRepository fundAggregateRepository;
    private final LimitCheckService limitCheckService;

    public ExposureAggregationService(TradeRepository tradeRepository, StrategyRepository strategyRepository,
                                       StrategyAggregateRepository strategyAggregateRepository,
                                       FundAggregateRepository fundAggregateRepository,
                                       LimitCheckService limitCheckService) {
        this.tradeRepository = tradeRepository;
        this.strategyRepository = strategyRepository;
        this.strategyAggregateRepository = strategyAggregateRepository;
        this.fundAggregateRepository = fundAggregateRepository;
        this.limitCheckService = limitCheckService;
    }

    @Transactional
    public Trade applyTradeEvent(String externalId, String strategyId, String fundId, String instrument,
                                  BigDecimal spot, BigDecimal vol, BigDecimal skew, BigDecimal rate,
                                  BigDecimal dividend) {
        strategyRepository.findById(strategyId)
                .orElseGet(() -> strategyRepository.save(new Strategy(strategyId, fundId)));

        Trade trade = tradeRepository.findByExternalId(externalId).orElse(null);
        String oldStrategyId = trade == null ? null : trade.getStrategyId();

        // A trade re-booked to a different strategy (and possibly a
        // different fund) is a real, if uncommon, event: its entire old
        // exposure must come out of the old strategy/fund aggregates, not
        // just the delta against its new exposure, because the old and new
        // scopes are different rows. Handled here rather than assumed away,
        // and exercised by ExposureIncrementalVsOracleTest.
        if (oldStrategyId != null && !oldStrategyId.equals(strategyId)) {
            StrategyAggregate oldStrategyAggregate = strategyAggregateRepository.findByStrategyId(oldStrategyId)
                    .orElseThrow(() -> new IllegalStateException("no strategy aggregate for " + oldStrategyId));
            String oldFundId = oldStrategyAggregate.getFundId();
            FundAggregate oldFundAggregate = fundAggregateRepository.findById(oldFundId)
                    .orElseThrow(() -> new IllegalStateException("no fund aggregate for " + oldFundId));
            for (FactorType f : FactorType.values()) {
                BigDecimal removal = trade.exposure(f).negate();
                if (removal.signum() == 0) continue;
                oldStrategyAggregate.addDelta(f, removal);
                oldFundAggregate.addDelta(f, removal);
            }
            strategyAggregateRepository.save(oldStrategyAggregate);
            fundAggregateRepository.save(oldFundAggregate);
            limitCheckService.checkScope(com.manas.fxexp.domain.ScopeType.STRATEGY, oldStrategyId);
            limitCheckService.checkScope(com.manas.fxexp.domain.ScopeType.FUND, oldFundId);
        }

        StrategyAggregate strategyAggregate = strategyAggregateRepository.findByStrategyId(strategyId)
                .orElseGet(() -> strategyAggregateRepository.save(new StrategyAggregate(strategyId, fundId)));
        FundAggregate fundAggregate = fundAggregateRepository.findById(fundId)
                .orElseGet(() -> fundAggregateRepository.save(new FundAggregate(fundId)));

        BigDecimal[] oldExposures = new BigDecimal[FactorType.values().length];
        boolean strategyUnchanged = trade != null && oldStrategyId.equals(strategyId);
        if (trade == null) {
            for (int i = 0; i < oldExposures.length; i++) oldExposures[i] = BigDecimal.ZERO;
            trade = new Trade(externalId, strategyId, instrument, spot, vol, skew, rate, dividend);
        } else {
            for (FactorType f : FactorType.values()) {
                oldExposures[f.ordinal()] = strategyUnchanged ? trade.exposure(f) : BigDecimal.ZERO;
            }
            trade.applyNewExposures(strategyId, instrument, spot, vol, skew, rate, dividend);
        }
        trade = tradeRepository.save(trade);

        BigDecimal[] newExposures = {spot, vol, skew, rate, dividend};
        for (FactorType f : FactorType.values()) {
            BigDecimal delta = newExposures[f.ordinal()].subtract(oldExposures[f.ordinal()]);
            if (delta.signum() == 0) continue;
            strategyAggregate.addDelta(f, delta);
            fundAggregate.addDelta(f, delta);
        }
        strategyAggregateRepository.save(strategyAggregate);
        fundAggregateRepository.save(fundAggregate);

        limitCheckService.checkScope(com.manas.fxexp.domain.ScopeType.STRATEGY, strategyId);
        limitCheckService.checkScope(com.manas.fxexp.domain.ScopeType.FUND, fundId);

        return trade;
    }
}
