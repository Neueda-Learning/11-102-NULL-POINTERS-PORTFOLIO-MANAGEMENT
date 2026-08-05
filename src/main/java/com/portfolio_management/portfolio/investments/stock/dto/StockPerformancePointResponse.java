package com.portfolio_management.portfolio.investments.stock.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record StockPerformancePointResponse(
        LocalDate date,
        BigDecimal closePrice
) {
}

