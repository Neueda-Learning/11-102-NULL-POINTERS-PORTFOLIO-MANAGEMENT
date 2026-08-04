package com.portfolio_management.portfolio.investments.stock.dto;

import java.math.BigDecimal;

public record StockQuoteResponse(
        BigDecimal currentPrice,
        BigDecimal change,
        BigDecimal changePercent,
        BigDecimal high,
        BigDecimal low,
        BigDecimal open,
        BigDecimal previousClose,
        Long timestamp
) {
}

