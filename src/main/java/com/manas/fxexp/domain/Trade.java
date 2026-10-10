package com.manas.fxexp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * One trade's current factor exposures. A trade is upserted by externalId:
 * a second event for the same externalId replaces its exposures rather than
 * adding a second row, which is what makes "an update" meaningful (the
 * incremental aggregate update applies the delta between the old and new
 * row, not the new row alone).
 */
@Entity
public class Trade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String externalId;

    @Column(nullable = false)
    private String strategyId;

    private String instrument;

    @Column(nullable = false)
    private BigDecimal spotExposure = BigDecimal.ZERO;
    @Column(nullable = false)
    private BigDecimal volExposure = BigDecimal.ZERO;
    @Column(nullable = false)
    private BigDecimal skewExposure = BigDecimal.ZERO;
    @Column(nullable = false)
    private BigDecimal rateExposure = BigDecimal.ZERO;
    @Column(nullable = false)
    private BigDecimal dividendExposure = BigDecimal.ZERO;

    private Instant updatedAt = Instant.now();

    protected Trade() {
    }

    public Trade(String externalId, String strategyId, String instrument, BigDecimal spotExposure,
                 BigDecimal volExposure, BigDecimal skewExposure, BigDecimal rateExposure,
                 BigDecimal dividendExposure) {
        this.externalId = externalId;
        this.strategyId = strategyId;
        this.instrument = instrument;
        this.spotExposure = spotExposure;
        this.volExposure = volExposure;
        this.skewExposure = skewExposure;
        this.rateExposure = rateExposure;
        this.dividendExposure = dividendExposure;
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

    public void applyNewExposures(String strategyId, String instrument, BigDecimal spotExposure,
                                   BigDecimal volExposure, BigDecimal skewExposure, BigDecimal rateExposure,
                                   BigDecimal dividendExposure) {
        this.strategyId = strategyId;
        this.instrument = instrument;
        this.spotExposure = spotExposure;
        this.volExposure = volExposure;
        this.skewExposure = skewExposure;
        this.rateExposure = rateExposure;
        this.dividendExposure = dividendExposure;
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getStrategyId() {
        return strategyId;
    }

    public String getInstrument() {
        return instrument;
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
