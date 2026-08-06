package com.portfolio_management.portfolio.investments.stock.dto;

import java.time.LocalDate;

public record StockNewsItemResponse(
        String symbol,
        String headline,
        String source,
        String url,
        String summary,
        LocalDate publishedDate
) {
}

