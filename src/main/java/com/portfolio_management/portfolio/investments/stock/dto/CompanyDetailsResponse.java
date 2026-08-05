package com.portfolio_management.portfolio.investments.stock.dto;

public record CompanyDetailsResponse(
        String symbol,
        String companyName,
        String exchange,
        String country,
        String sector,
        String currency,
        String website,
        StockQuoteResponse quote
) {
}

