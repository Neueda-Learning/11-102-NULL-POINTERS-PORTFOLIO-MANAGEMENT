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
    public Map<String, Object> getSummary() {
        return portfolioService.getSummary();
    }

    @GetMapping("/performance-history")
    public Map<String, Object> getPerformanceHistory() {
        return portfolioService.getPerformanceHistory();
    }

    @GetMapping("/holdings")
    public List<Map<String, Object>> getHoldings(
            @RequestParam(defaultValue = "ALL") String type) {
        return portfolioService.getHoldings(type);
    }

    @PostMapping("/holdings")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> addHolding(@RequestBody Map<String, Object> request) {
        return portfolioService.addHolding(request);
    }

    @PostMapping("/sell/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void sellHolding(@PathVariable Long id) {
        portfolioService.sellHolding(id);
    }

    @GetMapping("/transactions")
    public List<Map<String, Object>> getTransactions() {
        return portfolioService.getAllTransactions();
    }
}

