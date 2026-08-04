package com.portfolio_management.portfolio.investments.stock.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record TradeResponse(
        Long portfolioId,
        String symbol,
        String companyName,
        String action,
        BigDecimal quantity,
        BigDecimal executedPrice,
        BigDecimal totalAmount,
        BigDecimal remainingCashBalance,
        BigDecimal totalShares,
        BigDecimal averagePurchasePrice,
        Instant transactionDate
) {
}

