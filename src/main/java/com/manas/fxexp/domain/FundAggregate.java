package com.manas.fxexp.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.math.BigDecimal;
import java.time.Instant;

/** Same role as StrategyAggregate, one level up: the sum across every
 * strategy's aggregate belonging to this fund. */
@Entity
public class FundAggregate {

    @Id
    private String fundId;

    private BigDecimal spotExposure = BigDecimal.ZERO;
    private BigDecimal volExposure = BigDecimal.ZERO;
    private BigDecimal skewExposure = BigDecimal.ZERO;
    private BigDecimal rateExposure = BigDecimal.ZERO;
    private BigDecimal dividendExposure = BigDecimal.ZERO;

    private Instant updatedAt = Instant.now();

    protected FundAggregate() {
    }

    public FundAggregate(String fundId) {
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
