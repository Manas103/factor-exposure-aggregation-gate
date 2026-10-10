package com.manas.fxexp.web.dto;

import java.math.BigDecimal;

public record TradeEventRequest(
        String externalId,
        String strategyId,
        String fundId,
        String instrument,
        BigDecimal spotExposure,
        BigDecimal volExposure,
        BigDecimal skewExposure,
        BigDecimal rateExposure,
        BigDecimal dividendExposure
) {
}
