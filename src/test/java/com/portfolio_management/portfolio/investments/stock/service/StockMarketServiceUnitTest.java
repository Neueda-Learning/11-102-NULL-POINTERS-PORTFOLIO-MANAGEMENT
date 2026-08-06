package com.portfolio_management.portfolio.investments.stock.service;

import com.portfolio_management.portfolio.investments.stock.client.FinnhubRestClient;
import com.portfolio_management.portfolio.investments.stock.dto.CompanyDetailsResponse;
import com.portfolio_management.portfolio.investments.stock.dto.MarketplacePageResponse;
import com.portfolio_management.portfolio.investments.stock.dto.MarketplaceStockResponse;
import com.portfolio_management.portfolio.investments.stock.dto.StockPerformancePointResponse;
import com.portfolio_management.portfolio.investments.stock.dto.StockPerformanceResponse;
import com.portfolio_management.portfolio.investments.stock.dto.StockSearchItemResponse;
import com.portfolio_management.portfolio.investments.stock.exceptions.StockModuleException;
import com.portfolio_management.portfolio.investments.stock.websocket.FinnhubWebSocketClient;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockMarketServiceUnitTest {

    @Mock
    private FinnhubRestClient finnhubRestClient;

    @Mock
    private FinnhubWebSocketClient finnhubWebSocketClient;

    private StockMarketService stockMarketService;

    @BeforeEach
    void setUp() {
        stockMarketService = new StockMarketService(finnhubRestClient, finnhubWebSocketClient);
    }

    @Test
    void searchStocks_normalizesQuery_andMapsResults() {
        when(finnhubRestClient.searchStocks("AAPL", 15)).thenReturn(
                List.of(new FinnhubRestClient.FinnhubSearchItem("AAPL", "AAPL", "Apple Inc.", "Common Stock"))
        );

        List<StockSearchItemResponse> result = stockMarketService.searchStocks(" aapl ");

        assertEquals(1, result.size());
        assertEquals("AAPL", result.get(0).symbol());
        assertEquals("Apple Inc.", result.get(0).companyName());
    }

    @Test
    void searchStocks_throwsBadRequest_whenBlankQuery() {
        StockModuleException ex = assertThrows(StockModuleException.class, () -> stockMarketService.searchStocks(" "));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void getCompanyDetails_subscribesAndReturnsMappedResponse() {
        when(finnhubRestClient.getCompanyProfile("AAPL")).thenReturn(profile("AAPL", "Apple Inc.", "NASDAQ"));
        when(finnhubRestClient.getQuote("AAPL")).thenReturn(quote("220.10", "1.20", "0.55"));

        CompanyDetailsResponse result = stockMarketService.getCompanyDetails("aapl");

        assertEquals("AAPL", result.symbol());
        assertEquals("Apple Inc.", result.companyName());
        assertEquals(new BigDecimal("220.10"), result.quote().currentPrice());
        verify(finnhubWebSocketClient, times(1)).subscribeSymbol("AAPL");
    }

    @Test
    void getMarketplacePage_appliesPageAndSizeBounds() {
        when(finnhubRestClient.getQuote("AAPL")).thenReturn(quote("100.00", "0.00", "0.00"));
        when(finnhubRestClient.getCompanyProfile("AAPL")).thenReturn(profile("AAPL", "Apple Inc.", "NASDAQ"));

        MarketplacePageResponse result = stockMarketService.getMarketplacePage(0, 50);

        assertEquals(1, result.page());
        assertEquals(10, result.size());
        assertEquals(10, result.items().size());
        verify(finnhubWebSocketClient, times(1)).subscribeSymbols(anyList());
    }

    @Test
    void getMarketplacePage_returnsEmptyItems_whenPageOutOfRange() {
        MarketplacePageResponse result = stockMarketService.getMarketplacePage(999, 10);

        assertTrue(result.items().isEmpty());
        assertEquals(999, result.page());
    }

    @Test
    void getMarketplacePage_returnsFallbackItem_whenQuoteFails() {
        when(finnhubRestClient.getQuote("AAPL")).thenThrow(new RuntimeException("quote failed"));

        MarketplacePageResponse result = stockMarketService.getMarketplacePage(1, 1);

        MarketplaceStockResponse item = result.items().get(0);
        assertEquals("AAPL", item.symbol());
        assertEquals(new BigDecimal("0"), item.currentPrice());
        assertEquals(new BigDecimal("0"), item.dailyChangePercent());
    }

    @Test
    void getMarketplacePage_retriesDotSymbolUsingDash_whenPrimaryQuoteFails() {
        when(finnhubRestClient.getQuote("BRK.B")).thenThrow(new RuntimeException("primary failed"));
        when(finnhubRestClient.getQuote("BRK-B")).thenReturn(quote("400.00", "1.00", "0.25"));
        when(finnhubRestClient.getCompanyProfile("BRK.B")).thenReturn(profile("BRK.B", "Berkshire Hathaway", "NYSE"));

        MarketplacePageResponse result = stockMarketService.getMarketplacePage(8, 1);

        assertEquals(1, result.items().size());
        assertEquals("BRK.B", result.items().get(0).symbol());
        assertEquals(new BigDecimal("400.00"), result.items().get(0).currentPrice());
        verify(finnhubRestClient, times(1)).getQuote("BRK-B");
    }

    @Test
    void getPerformance_returnsMappedPoints() {
        when(finnhubRestClient.getCompanyProfile("MSFT")).thenReturn(profile("MSFT", "Microsoft", "NASDAQ"));
        when(finnhubRestClient.getDailyPerformance("MSFT", 10)).thenReturn(List.of(
                new FinnhubRestClient.FinnhubCandlePoint(LocalDate.of(2026, 8, 5), new BigDecimal("330.10"))
        ));

        StockPerformanceResponse result = stockMarketService.getPerformance("msft");

        assertEquals("MSFT", result.symbol());
        assertEquals("Microsoft", result.companyName());
        assertEquals(1, result.points().size());
        StockPerformancePointResponse point = result.points().get(0);
        assertEquals(LocalDate.of(2026, 8, 5), point.date());
    }

    @Test
    void getPerformance_retriesDotSymbolUsingDash_whenPrimaryFails() {
        when(finnhubRestClient.getCompanyProfile("BRK.B")).thenReturn(profile("BRK.B", "Berkshire Hathaway", "NYSE"));
        when(finnhubRestClient.getDailyPerformance("BRK.B", 10)).thenThrow(new RuntimeException("primary failed"));
        when(finnhubRestClient.getDailyPerformance("BRK-B", 10)).thenReturn(List.of(
                new FinnhubRestClient.FinnhubCandlePoint(LocalDate.of(2026, 8, 5), new BigDecimal("400.00"))
        ));

        StockPerformanceResponse result = stockMarketService.getPerformance("brk.b");

        assertEquals("BRK.B", result.symbol());
        assertEquals(1, result.points().size());
        verify(finnhubRestClient, times(1)).getDailyPerformance("BRK-B", 10);
    }

    @Test
    void getPerformance_usesProfileFallback_whenProfileLookupFails() {
        when(finnhubRestClient.getCompanyProfile("TSLA")).thenThrow(new RuntimeException("profile failed"));
        when(finnhubRestClient.getDailyPerformance("TSLA", 10)).thenReturn(List.of(
                new FinnhubRestClient.FinnhubCandlePoint(LocalDate.of(2026, 8, 5), new BigDecimal("250.00"))
        ));

        StockPerformanceResponse result = stockMarketService.getPerformance("tsla");

        assertEquals("TSLA", result.symbol());
        assertEquals("TSLA", result.companyName());
        assertEquals(1, result.points().size());
    }

    private FinnhubRestClient.FinnhubProfile profile(String symbol, String name, String exchange) {
        return new FinnhubRestClient.FinnhubProfile(symbol, name, exchange, "US", "Technology", "USD", "https://example.com");
    }

    private FinnhubRestClient.FinnhubQuote quote(String currentPrice, String change, String changePercent) {
        return new FinnhubRestClient.FinnhubQuote(
                new BigDecimal(currentPrice),
                new BigDecimal(change),
                new BigDecimal(changePercent),
                new BigDecimal(currentPrice),
                new BigDecimal(currentPrice),
                new BigDecimal(currentPrice),
                new BigDecimal(currentPrice),
                1722900000L
        );
    }
}


