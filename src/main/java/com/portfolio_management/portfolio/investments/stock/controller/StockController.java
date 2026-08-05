package com.portfolio_management.portfolio.investments.stock.controller;

import com.portfolio_management.portfolio.investments.stock.dto.BuyStockRequest;
import com.portfolio_management.portfolio.investments.stock.dto.CompanyDetailsResponse;
import com.portfolio_management.portfolio.investments.stock.dto.MarketplacePageResponse;
import com.portfolio_management.portfolio.investments.stock.dto.StockHoldingDetailsResponse;
import com.portfolio_management.portfolio.investments.stock.dto.StockHoldingResponse;
import com.portfolio_management.portfolio.investments.stock.dto.StockPerformanceResponse;
import com.portfolio_management.portfolio.investments.stock.dto.StockSearchItemResponse;
import com.portfolio_management.portfolio.investments.stock.dto.StockSubscriptionsRequest;
import com.portfolio_management.portfolio.investments.stock.dto.StockTransactionResponse;
import com.portfolio_management.portfolio.investments.stock.dto.TradeResponse;
import com.portfolio_management.portfolio.investments.stock.dto.SellStockRequest;
import com.portfolio_management.portfolio.investments.stock.service.StockHoldingService;
import com.portfolio_management.portfolio.investments.stock.service.StockMarketService;
import com.portfolio_management.portfolio.investments.stock.service.StockTradingService;
import com.portfolio_management.portfolio.investments.stock.websocket.FinnhubWebSocketClient;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class StockController {

    private final StockMarketService stockMarketService;
    private final StockTradingService stockTradingService;
    private final StockHoldingService stockHoldingService;
    private final FinnhubWebSocketClient finnhubWebSocketClient;

    public StockController(
            StockMarketService stockMarketService,
            StockTradingService stockTradingService,
            StockHoldingService stockHoldingService,
            FinnhubWebSocketClient finnhubWebSocketClient
    ) {
        this.stockMarketService = stockMarketService;
        this.stockTradingService = stockTradingService;
        this.stockHoldingService = stockHoldingService;
        this.finnhubWebSocketClient = finnhubWebSocketClient;
    }

    @GetMapping("/stocks/search")
    public ResponseEntity<List<StockSearchItemResponse>> searchStocks(@RequestParam("query") String query) {
        return ResponseEntity.ok(stockMarketService.searchStocks(query));
    }

    @GetMapping("/stocks/{symbol}")
    public ResponseEntity<CompanyDetailsResponse> getCompanyDetails(@PathVariable String symbol) {

        return ResponseEntity.ok(stockMarketService.getCompanyDetails(symbol));
    }

    @GetMapping("/stocks/marketplace")
    public ResponseEntity<MarketplacePageResponse> getMarketplacePage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(stockMarketService.getMarketplacePage(page, size));
    }

    @GetMapping("/stocks/{symbol}/performance")
    public ResponseEntity<StockPerformanceResponse> getStockPerformance(@PathVariable String symbol) {
        return ResponseEntity.ok(stockMarketService.getPerformance(symbol));
    }

    @PostMapping("/portfolios/{portfolioId}/stocks/buy")
    public ResponseEntity<TradeResponse> buyStock(
            @PathVariable Long portfolioId,
            @Valid @RequestBody BuyStockRequest request
    ) {
        return ResponseEntity.ok(stockTradingService.buyStock(portfolioId, request));
    }

    @PostMapping("/portfolios/{portfolioId}/stocks/sell")
    public ResponseEntity<TradeResponse> sellStock(
            @PathVariable Long portfolioId,
            @Valid @RequestBody SellStockRequest request
    ) {
        return ResponseEntity.ok(stockTradingService.sellStock(portfolioId, request));
    }

    @GetMapping("/portfolios/{portfolioId}/stocks/holdings")
    public ResponseEntity<List<StockHoldingResponse>> getStockHoldings(@PathVariable Long portfolioId) {
        return ResponseEntity.ok(stockHoldingService.getHoldings(portfolioId));
    }

    @GetMapping("/portfolios/{portfolioId}/stocks/holdings/{symbol}")
    public ResponseEntity<StockHoldingDetailsResponse> getStockHoldingDetails(
            @PathVariable Long portfolioId,
            @PathVariable String symbol
    ) {
        return ResponseEntity.ok(stockHoldingService.getHoldingDetails(portfolioId, symbol));
    }

    @GetMapping("/portfolios/{portfolioId}/stocks/{symbol}/transactions")
    public ResponseEntity<List<StockTransactionResponse>> getStockTransactions(
            @PathVariable Long portfolioId,
            @PathVariable String symbol
    ) {
        return ResponseEntity.ok(stockHoldingService.getTransactionsBySymbol(portfolioId, symbol));
    }

    @PostMapping("/stocks/live/subscriptions")
    public ResponseEntity<Void> subscribeSymbols(@Valid @RequestBody StockSubscriptionsRequest request) {
        finnhubWebSocketClient.subscribeSymbols(request.symbols());
        return ResponseEntity.accepted().build();
    }
}

