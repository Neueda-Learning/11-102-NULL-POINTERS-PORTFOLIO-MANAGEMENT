package com.portfolio_management.portfolio.investments.stock.dto;

import java.time.Instant;
import java.util.List;

public record StockTodayPredictionsResponse(
        Long portfolioId,
        Instant generatedAt,
        List<StockTodayPredictionItemResponse> predictions
) {
}

