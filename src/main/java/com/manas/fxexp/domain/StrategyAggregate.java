package com.manas.fxexp.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * The materialized, incrementally-maintained sum of every trade's exposures
 * in one strategy. Never recomputed from scratch on a read; only ever
 * adjusted by a delta when a trade is created or updated (see
 * ExposureAggregationService). RecomputeOracleService can independently
 * rebuild this same row by summing trades directly, used only to verify the
 * incremental path, never on the read hot path.
 */
@Entity
public class StrategyAggregate {

    @Id
    private String strategyId;

    @jakarta.persistence.Column(nullable = false)
    private String fundId;

    private BigDecimal spotExposure = BigDecimal.ZERO;
    private BigDecimal volExposure = BigDecimal.ZERO;
    private BigDecimal skewExposure = BigDecimal.ZERO;
    private BigDecimal rateExposure = BigDecimal.ZERO;
    private BigDecimal dividendExposure = BigDecimal.ZERO;

    private Instant updatedAt = Instant.now();

    protected StrategyAggregate() {
    }

    public StrategyAggregate(String strategyId, String fundId) {
        this.strategyId = strategyId;
        this.fundId = fundId;
    }

    public BigDecimal exposure(FactorType factor) {
        return switch (factor) {
            case SPOT -> spotExposure;
            case VOL -> volExposure;
            case SKEW -> skewExposure;
            case RATE -> rateExposure;
            case DIVIDEND -> dividendExposure;
        };
    }

    public void addDelta(FactorType factor, BigDecimal delta) {
        switch (factor) {
            case SPOT -> spotExposure = spotExposure.add(delta);
            case VOL -> volExposure = volExposure.add(delta);
            case SKEW -> skewExposure = skewExposure.add(delta);
            case RATE -> rateExposure = rateExposure.add(delta);
            case DIVIDEND -> dividendExposure = dividendExposure.add(delta);
        }
        updatedAt = Instant.now();
    }

    public String getStrategyId() {
        return strategyId;
    }

    public String getFundId() {
        return fundId;
    }

    public BigDecimal getSpotExposure() {
        return spotExposure;
    }

    public BigDecimal getVolExposure() {
        return volExposure;
    }

    public BigDecimal getSkewExposure() {
        return skewExposure;
    }

    public BigDecimal getRateExposure() {
        return rateExposure;
    }

    public BigDecimal getDividendExposure() {
        return dividendExposure;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
