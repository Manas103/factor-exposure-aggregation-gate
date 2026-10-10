package com.manas.fxexp.web.dto;

import com.manas.fxexp.domain.Breach;
import java.math.BigDecimal;
import java.time.Instant;

public record BreachView(
        String scopeType,
        String scopeId,
        String factor,
        BigDecimal aggregateValue,
        BigDecimal threshold,
        String triggeringTradeExternalId,
        BigDecimal triggeringTradeContribution,
        Instant detectedAt
) {
    public static BreachView of(Breach b) {
        return new BreachView(b.getScopeType().name(), b.getScopeId(), b.getFactor().name(), b.getAggregateValue(),
                b.getThreshold(), b.getTriggeringTradeExternalId(), b.getTriggeringTradeContribution(),
                b.getDetectedAt());
    }
}
