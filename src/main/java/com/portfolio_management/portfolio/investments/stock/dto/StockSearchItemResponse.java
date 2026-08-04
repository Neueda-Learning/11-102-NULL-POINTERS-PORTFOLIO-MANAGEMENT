package com.portfolio_management.portfolio.investments.stock.dto;

public record StockSearchItemResponse(
        String symbol,
        String displaySymbol,
        String companyName,
        String type
) {
}

