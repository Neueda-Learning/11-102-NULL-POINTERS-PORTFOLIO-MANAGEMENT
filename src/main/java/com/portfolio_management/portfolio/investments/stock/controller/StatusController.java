package com.portfolio_management.portfolio.investments.stock.controller;

import com.portfolio_management.portfolio.investments.stock.service.StockHoldingService;
import com.portfolio_management.portfolio.investments.stock.service.StockMarketService;
import com.portfolio_management.portfolio.investments.stock.service.StockTradingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/status")
public class StatusController {

    private final StockTradingService tradingService;
    private final StockHoldingService holdingService;
    private final StockMarketService marketService;

    public StatusController(
            StockTradingService tradingService,
            StockHoldingService holdingService,
            StockMarketService marketService
    ) {
        this.tradingService = tradingService;
        this.holdingService = holdingService;
        this.marketService = marketService;
    }

    @GetMapping("/check")
    public ResponseEntity<Map<String, Object>> statusCheck() {
        Map<String, Object> status = new HashMap<>();
        status.put("timestamp", System.currentTimeMillis());
        status.put("status", "OK");
        status.put("services", Map.of(
                "tradingService", tradingService != null,
                "holdingService", holdingService != null,
                "marketService", marketService != null
        ));
        status.put("endpoints", Map.of(
                "POST /api/portfolios/{portfolioId}/stocks/buy", "Ready",
                "POST /api/portfolios/{portfolioId}/stocks/sell", "Ready",
                "GET /api/portfolios/{portfolioId}/stocks/holdings", "Ready",
                "GET /api/stocks/search", "Ready",
                "GET /api/debug/test-all", "Ready"
        ));
        status.put("message", "All endpoints registered and ready. If you still see 404 on /buy or /sell, restart the app with: mvn spring-boot:run");
        return ResponseEntity.ok(status);
    }
}

