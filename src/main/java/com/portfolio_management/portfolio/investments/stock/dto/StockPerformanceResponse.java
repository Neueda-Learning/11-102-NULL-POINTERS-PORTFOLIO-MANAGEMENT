package com.portfolio_management.portfolio.investments.stock.dto;

import java.util.List;

public record StockPerformanceResponse(
        String symbol,
        String companyName,
        List<StockPerformancePointResponse> points
) {
}

