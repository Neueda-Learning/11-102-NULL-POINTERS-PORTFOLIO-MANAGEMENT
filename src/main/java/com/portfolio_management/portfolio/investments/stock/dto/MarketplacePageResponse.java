package com.portfolio_management.portfolio.investments.stock.dto;

import java.util.List;

public record MarketplacePageResponse(
        int page,
        int size,
        int totalPages,
        long totalItems,
        List<MarketplaceStockResponse> items
) {
}

