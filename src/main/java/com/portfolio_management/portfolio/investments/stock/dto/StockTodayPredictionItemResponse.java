package com.portfolio_management.portfolio.investments.stock.dto;

import java.math.BigDecimal;

public record StockTodayPredictionItemResponse(
        String symbol,
        String companyName,
        BigDecimal sharesOwned,
        BigDecimal currentPrice,
        BigDecimal predictedChangePercentToday,
        BigDecimal predictedProfitLossToday,
        String recommendation,
        String reasoning
) {
}

