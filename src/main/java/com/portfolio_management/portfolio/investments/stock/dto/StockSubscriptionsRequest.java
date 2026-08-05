package com.portfolio_management.portfolio.investments.stock.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record StockSubscriptionsRequest(
        @NotEmpty(message = "symbols must not be empty")
        List<String> symbols
) {
}

