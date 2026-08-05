package com.portfolio_management.portfolio.investments.stock.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record LivePriceUpdateResponse(
        String symbol,
        BigDecimal price,
        Instant timestamp
) {
}

