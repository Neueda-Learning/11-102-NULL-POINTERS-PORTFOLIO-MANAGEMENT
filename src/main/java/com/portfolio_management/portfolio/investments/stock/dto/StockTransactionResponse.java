package com.portfolio_management.portfolio.investments.stock.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record StockTransactionResponse(
        Long transactionId,
        String symbol,
        String action,
        BigDecimal quantity,
        BigDecimal transactionPrice,
        Instant transactionDate
) {
}

