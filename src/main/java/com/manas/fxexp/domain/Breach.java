package com.manas.fxexp.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * A live limit breach, always attributed to the single trade with the
 * largest-magnitude exposure to the breached factor within the breached
 * scope at detection time (see LimitCheckService): "the trade behind it"
 * means the actual biggest driver, not whichever trade happened to be
 * processed last.
 */
@Entity
public class Breach {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private ScopeType scopeType;

    private String scopeId;

    @Enumerated(EnumType.STRING)
    private FactorType factor;

    private BigDecimal aggregateValue;
    private BigDecimal threshold;

    private String triggeringTradeExternalId;
    private BigDecimal triggeringTradeContribution;

    private Instant detectedAt = Instant.now();
    private boolean active = true;

    protected Breach() {
    }

    public Breach(ScopeType scopeType, String scopeId, FactorType factor, BigDecimal aggregateValue,
                  BigDecimal threshold, String triggeringTradeExternalId,
                  BigDecimal triggeringTradeContribution) {
        this.scopeType = scopeType;
        this.scopeId = scopeId;
        this.factor = factor;
        this.aggregateValue = aggregateValue;
        this.threshold = threshold;
        this.triggeringTradeExternalId = triggeringTradeExternalId;
        this.triggeringTradeContribution = triggeringTradeContribution;
    }

    public void refresh(BigDecimal aggregateValue, String triggeringTradeExternalId,
                         BigDecimal triggeringTradeContribution) {
        this.aggregateValue = aggregateValue;
        this.triggeringTradeExternalId = triggeringTradeExternalId;
        this.triggeringTradeContribution = triggeringTradeContribution;
        this.detectedAt = Instant.now();
        this.active = true;
    }

    public void resolve() {
        this.active = false;
    }

    public Long getId() {
        return id;
    }

    public ScopeType getScopeType() {
        return scopeType;
    }

    public String getScopeId() {
        return scopeId;
    }

    public FactorType getFactor() {
        return factor;
    }

    public BigDecimal getAggregateValue() {
        return aggregateValue;
    }

    public BigDecimal getThreshold() {
        return threshold;
    }

    public String getTriggeringTradeExternalId() {
        return triggeringTradeExternalId;
    }

    public BigDecimal getTriggeringTradeContribution() {
        return triggeringTradeContribution;
    }

    public Instant getDetectedAt() {
        return detectedAt;
    }

    public boolean isActive() {
        return active;
    }
}
