package com.portfolio_management.portfolio.investments.stock.dto;

import java.math.BigDecimal;

public record StockHoldingResponse(
        String symbol,
        String companyName,
        BigDecimal shares,
        BigDecimal averagePurchasePrice,
        BigDecimal costBasis,
        BigDecimal currentPrice,
        BigDecimal marketValue,
        BigDecimal unrealizedProfitLoss,
        BigDecimal unrealizedProfitLossPercent
) {
}

