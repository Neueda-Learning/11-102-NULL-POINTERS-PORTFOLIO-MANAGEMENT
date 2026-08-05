package com.portfolio_management.portfolio.investments.stock.dto;

import java.util.List;

public record StockHoldingDetailsResponse(
        StockHoldingResponse holding,
        List<StockTransactionResponse> transactions
) {
}

