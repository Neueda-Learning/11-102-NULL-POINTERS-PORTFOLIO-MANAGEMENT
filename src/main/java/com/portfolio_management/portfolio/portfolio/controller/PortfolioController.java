package com.portfolio_management.portfolio.portfolio.controller;

import com.portfolio_management.portfolio.portfolio.service.PortfolioService;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController("mainPortfolioController")
@RequestMapping("/api/v1/portfolio")
public class PortfolioController {

    private final PortfolioService portfolioService;

    public PortfolioController(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    @GetMapping("/summary")
    public Map<String, Object> getSummary(@RequestParam(defaultValue = "1") Long portfolioId) {
        return portfolioService.getSummary(portfolioId);
    }

    @GetMapping("/cash-balance")
    public Map<String, Object> getCashBalance(@RequestParam(defaultValue = "1") Long portfolioId) {
        return portfolioService.getCashBalance(portfolioId);
    }

    @GetMapping("/performance-history")
    public Map<String, Object> getPerformanceHistory(@RequestParam(defaultValue = "1") Long portfolioId) {
        return portfolioService.getPerformanceHistory(portfolioId);
    }

    @GetMapping("/holdings")
    public List<Map<String, Object>> getHoldings(
            @RequestParam(defaultValue = "1") Long portfolioId,
            @RequestParam(defaultValue = "ALL") String type) {
        return portfolioService.getHoldings(portfolioId, type);
    }

    @PostMapping("/holdings")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> addHolding(@RequestBody Map<String, Object> request) {
        return portfolioService.addHolding(request);
    }

    @PostMapping("/sell/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void sellHolding(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") Long portfolioId,
            @RequestBody(required = false) Map<String, Object> request
    ) {
        java.math.BigDecimal quantity = null;
        if (request != null && request.get("quantity") != null) {
            quantity = new java.math.BigDecimal(request.get("quantity").toString());
        }
        portfolioService.sellHolding(portfolioId, id, quantity);
    }

    @GetMapping("/transactions")
    public List<Map<String, Object>> getTransactions(@RequestParam(defaultValue = "1") Long portfolioId) {
        return portfolioService.getAllTransactions(portfolioId);
    }
}

