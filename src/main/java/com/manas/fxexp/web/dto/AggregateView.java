package com.manas.fxexp.web.dto;

import com.manas.fxexp.domain.FundAggregate;
import com.manas.fxexp.domain.StrategyAggregate;
import java.math.BigDecimal;
import java.time.Instant;

public record AggregateView(
        String scopeId,
        BigDecimal spotExposure,
        BigDecimal volExposure,
        BigDecimal skewExposure,
        BigDecimal rateExposure,
        BigDecimal dividendExposure,
        Instant updatedAt
) {
    public static AggregateView of(StrategyAggregate a) {
        return new AggregateView(a.getStrategyId(), a.getSpotExposure(), a.getVolExposure(), a.getSkewExposure(),
                a.getRateExposure(), a.getDividendExposure(), a.getUpdatedAt());
    }

    public static AggregateView of(FundAggregate a) {
        return new AggregateView(a.getFundId(), a.getSpotExposure(), a.getVolExposure(), a.getSkewExposure(),
                a.getRateExposure(), a.getDividendExposure(), a.getUpdatedAt());
    }
}
