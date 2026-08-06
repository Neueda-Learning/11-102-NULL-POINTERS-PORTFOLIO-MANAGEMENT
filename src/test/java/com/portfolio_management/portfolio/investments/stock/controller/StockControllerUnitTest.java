package com.portfolio_management.portfolio.investments.stock.controller;

import com.portfolio_management.portfolio.investments.stock.dto.BuyStockRequest;
import com.portfolio_management.portfolio.investments.stock.dto.CompanyDetailsResponse;
import com.portfolio_management.portfolio.investments.stock.dto.MarketplacePageResponse;
import com.portfolio_management.portfolio.investments.stock.dto.MarketplaceStockResponse;
import com.portfolio_management.portfolio.investments.stock.dto.SellStockRequest;
import com.portfolio_management.portfolio.investments.stock.dto.StockHoldingDetailsResponse;
import com.portfolio_management.portfolio.investments.stock.dto.StockHoldingResponse;
import com.portfolio_management.portfolio.investments.stock.dto.StockPerformancePointResponse;
import com.portfolio_management.portfolio.investments.stock.dto.StockPerformanceResponse;
import com.portfolio_management.portfolio.investments.stock.dto.StockQuoteResponse;
import com.portfolio_management.portfolio.investments.stock.dto.StockSearchItemResponse;
import com.portfolio_management.portfolio.investments.stock.dto.StockSubscriptionsRequest;
import com.portfolio_management.portfolio.investments.stock.dto.StockTransactionResponse;
import com.portfolio_management.portfolio.investments.stock.dto.TradeResponse;
import com.portfolio_management.portfolio.investments.stock.service.StockHoldingService;
import com.portfolio_management.portfolio.investments.stock.service.StockMarketService;
import com.portfolio_management.portfolio.investments.stock.service.StockTradingService;
import com.portfolio_management.portfolio.investments.stock.websocket.FinnhubWebSocketClient;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockControllerUnitTest {

    @Mock
    private StockMarketService stockMarketService;

    @Mock
    private StockTradingService stockTradingService;

    @Mock
    private StockHoldingService stockHoldingService;

    @Mock
    private FinnhubWebSocketClient finnhubWebSocketClient;

    private StockController stockController;

    @BeforeEach
    void setUp() {
        stockController = new StockController(stockMarketService, stockTradingService, stockHoldingService, finnhubWebSocketClient);
    }

    @Test
    void searchStocks_returnsOkWithResults() {
        List<StockSearchItemResponse> list = List.of(new StockSearchItemResponse("AAPL", "AAPL", "Apple Inc.", "Common Stock"));
        when(stockMarketService.searchStocks("AAPL")).thenReturn(list);

        ResponseEntity<List<StockSearchItemResponse>> response = stockController.searchStocks("AAPL");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(list, response.getBody());
    }

    @Test
    void searchStocks_forwardsQueryToService() {
        when(stockMarketService.searchStocks("MSFT")).thenReturn(List.of());

        stockController.searchStocks("MSFT");

        verify(stockMarketService, times(1)).searchStocks("MSFT");
    }

    @Test
    void getCompanyDetails_returnsOkWithBody() {
        CompanyDetailsResponse details = new CompanyDetailsResponse(
                "AAPL", "Apple Inc.", "NASDAQ", "US", "Technology", "USD", "https://apple.com",
                new StockQuoteResponse(new BigDecimal("220.10"), BigDecimal.ONE, new BigDecimal("0.45"), new BigDecimal("221.00"), new BigDecimal("218.50"), new BigDecimal("219.00"), new BigDecimal("219.10"), 1722900000L)
        );
        when(stockMarketService.getCompanyDetails("AAPL")).thenReturn(details);

        ResponseEntity<CompanyDetailsResponse> response = stockController.getCompanyDetails("AAPL");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(details, response.getBody());
    }

    @Test
    void getMarketplacePage_returnsOkAndForwardsPaging() {
        MarketplacePageResponse page = new MarketplacePageResponse(
                1,
                10,
                5,
                50,
                List.of(new MarketplaceStockResponse("AAPL", "Apple Inc.", "NASDAQ", new BigDecimal("220.10"), new BigDecimal("0.45")))
        );
        when(stockMarketService.getMarketplacePage(1, 10)).thenReturn(page);

        ResponseEntity<MarketplacePageResponse> response = stockController.getMarketplacePage(1, 10);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(page, response.getBody());
        verify(stockMarketService, times(1)).getMarketplacePage(1, 10);
    }

    @Test
    void getStockPerformance_returnsOkWithBody() {
        StockPerformanceResponse perf = new StockPerformanceResponse(
                "AAPL",
                "Apple Inc.",
                List.of(new StockPerformancePointResponse(LocalDate.of(2026, 8, 1), new BigDecimal("220.10")))
        );
        when(stockMarketService.getPerformance("AAPL")).thenReturn(perf);

        ResponseEntity<StockPerformanceResponse> response = stockController.getStockPerformance("AAPL");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(perf, response.getBody());
    }

    @Test
    void buyStock_returnsOkAndCallsTradingService() {
        BuyStockRequest request = new BuyStockRequest("AAPL", new BigDecimal("2.0000"));
        TradeResponse trade = sampleTrade("AAPL", "BUY");
        when(stockTradingService.buyStock(1L, request)).thenReturn(trade);

        ResponseEntity<TradeResponse> response = stockController.buyStock(1L, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(trade, response.getBody());
        verify(stockTradingService, times(1)).buyStock(1L, request);
    }

    @Test
    void sellStock_returnsOkAndCallsTradingService() {
        SellStockRequest request = new SellStockRequest("AAPL", new BigDecimal("1.0000"));
        TradeResponse trade = sampleTrade("AAPL", "SELL");
        when(stockTradingService.sellStock(1L, request)).thenReturn(trade);

        ResponseEntity<TradeResponse> response = stockController.sellStock(1L, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(trade, response.getBody());
        verify(stockTradingService, times(1)).sellStock(1L, request);
    }

    @Test
    void getStockHoldings_returnsOkWithBody() {
        List<StockHoldingResponse> holdings = List.of(
                new StockHoldingResponse("AAPL", "Apple Inc.", new BigDecimal("2.0000"), new BigDecimal("200.00"), new BigDecimal("400.00"), new BigDecimal("220.00"), new BigDecimal("440.00"), new BigDecimal("40.00"), new BigDecimal("10.00"))
        );
        when(stockHoldingService.getHoldings(1L)).thenReturn(holdings);

        ResponseEntity<List<StockHoldingResponse>> response = stockController.getStockHoldings(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(holdings, response.getBody());
    }

    @Test
    void getStockHoldingDetails_returnsOkWithBody() {
        StockHoldingDetailsResponse details = new StockHoldingDetailsResponse(
                new StockHoldingResponse("AAPL", "Apple Inc.", new BigDecimal("2.0000"), new BigDecimal("200.00"), new BigDecimal("400.00"), new BigDecimal("220.00"), new BigDecimal("440.00"), new BigDecimal("40.00"), new BigDecimal("10.00")),
                List.of(new StockTransactionResponse(1L, "AAPL", "BUY", new BigDecimal("2.0000"), new BigDecimal("200.00"), Instant.parse("2026-08-06T00:00:00Z")))
        );
        when(stockHoldingService.getHoldingDetails(1L, "AAPL")).thenReturn(details);

        ResponseEntity<StockHoldingDetailsResponse> response = stockController.getStockHoldingDetails(1L, "AAPL");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(details, response.getBody());
    }

    @Test
    void subscribeSymbols_returnsAcceptedAndSubscribes() {
        StockSubscriptionsRequest request = new StockSubscriptionsRequest(List.of("AAPL", "MSFT"));

        ResponseEntity<Void> response = stockController.subscribeSymbols(request);

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        verify(finnhubWebSocketClient, times(1)).subscribeSymbols(request.symbols());
    }

    private TradeResponse sampleTrade(String symbol, String action) {
        return new TradeResponse(
                1L,
                symbol,
                "Sample Co",
                action,
                new BigDecimal("1.0000"),
                new BigDecimal("220.00"),
                new BigDecimal("220.00"),
                new BigDecimal("1000.00"),
                new BigDecimal("2.0000"),
                new BigDecimal("210.00"),
                Instant.parse("2026-08-06T00:00:00Z")
        );
    }
}

