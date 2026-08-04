package com.portfolio_management.portfolio.investments.stock.controller;

import com.portfolio_management.portfolio.investments.stock.client.FinnhubRestClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/debug")
public class DebugController {

    private final FinnhubRestClient finnhubRestClient;

    public DebugController(FinnhubRestClient finnhubRestClient) {
        this.finnhubRestClient = finnhubRestClient;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "OK"));
    }

    @GetMapping("/test-search/{query}")
    public ResponseEntity<List<FinnhubRestClient.FinnhubSearchItem>> testSearch(@PathVariable String query) {
        return ResponseEntity.ok(finnhubRestClient.searchStocks(query, 5));
    }

    @GetMapping("/test-profile/{symbol}")
    public ResponseEntity<FinnhubRestClient.FinnhubProfile> testProfile(@PathVariable String symbol) {
        return ResponseEntity.ok(finnhubRestClient.getCompanyProfile(symbol));
    }

    @GetMapping("/test-quote/{symbol}")
    public ResponseEntity<FinnhubRestClient.FinnhubQuote> testQuote(@PathVariable String symbol) {
        return ResponseEntity.ok(finnhubRestClient.getQuote(symbol));
    }

    @GetMapping("/test-all")
    public ResponseEntity<Map<String, Object>> testAll() {
        Map<String, Object> result = new HashMap<>();
        result.put("search", finnhubRestClient.searchStocks("AAPL", 3));
        result.put("profile", finnhubRestClient.getCompanyProfile("AAPL"));
        result.put("quote", finnhubRestClient.getQuote("AAPL"));
        return ResponseEntity.ok(result);
    }
}

