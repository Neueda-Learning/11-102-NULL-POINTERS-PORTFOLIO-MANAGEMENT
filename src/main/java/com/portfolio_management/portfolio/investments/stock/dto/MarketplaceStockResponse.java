package com.portfolio_management.portfolio.investments.stock.dto;

import java.math.BigDecimal;

public record MarketplaceStockResponse(
        String symbol,
        String companyName,
        String exchange,
        BigDecimal currentPrice,
        BigDecimal dailyChangePercent
) {
}

