package com.manas.fxexp.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.math.BigDecimal;

/** A configured absolute-value threshold on one factor at one scope. */
@Entity
public class RiskLimit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private ScopeType scopeType;

    private String scopeId;

    @Enumerated(EnumType.STRING)
    private FactorType factor;

    private BigDecimal threshold;

    protected RiskLimit() {
    }

    public RiskLimit(ScopeType scopeType, String scopeId, FactorType factor, BigDecimal threshold) {
        this.scopeType = scopeType;
        this.scopeId = scopeId;
        this.factor = factor;
        this.threshold = threshold;
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

    public BigDecimal getThreshold() {
        return threshold;
    }
}
