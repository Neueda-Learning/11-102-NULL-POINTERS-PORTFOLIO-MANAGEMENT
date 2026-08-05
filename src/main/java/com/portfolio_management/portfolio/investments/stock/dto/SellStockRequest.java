package com.portfolio_management.portfolio.investments.stock.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record SellStockRequest(
        @NotBlank(message = "symbol is required")
        String symbol,
        @NotNull(message = "quantity is required")
        @DecimalMin(value = "0.0001", message = "quantity must be greater than zero")
        BigDecimal quantity
) {
}

