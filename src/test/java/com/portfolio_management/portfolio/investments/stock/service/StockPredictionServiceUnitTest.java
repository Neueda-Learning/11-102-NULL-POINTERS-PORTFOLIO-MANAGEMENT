package com.portfolio_management.portfolio.investments.stock.service;

import com.portfolio_management.portfolio.investments.stock.client.GeminiStockPredictionClient;
import com.portfolio_management.portfolio.investments.stock.dto.StockHoldingResponse;
import com.portfolio_management.portfolio.investments.stock.dto.StockTodayPredictionsResponse;
import com.portfolio_management.portfolio.investments.stock.exceptions.StockModuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockPredictionServiceUnitTest {

    @Mock
    private StockHoldingService stockHoldingService;

    @Mock
    private GeminiStockPredictionClient geminiStockPredictionClient;

    private StockPredictionService stockPredictionService;

    @BeforeEach
    void setUp() {
        stockPredictionService = new StockPredictionService(stockHoldingService, geminiStockPredictionClient, new ObjectMapper());
    }

    @Test
    void getTodayPredictions_returnsOwnedStocksOnly() {
        List<StockHoldingResponse> holdings = List.of(
                new StockHoldingResponse("AAPL", "Apple Inc.", new BigDecimal("2.0000"), new BigDecimal("200.00"), new BigDecimal("400.00"), new BigDecimal("220.00"), new BigDecimal("440.00"), new BigDecimal("40.00"), new BigDecimal("10.0000")),
                new StockHoldingResponse("MSFT", "Microsoft", new BigDecimal("1.0000"), new BigDecimal("300.00"), new BigDecimal("300.00"), new BigDecimal("310.00"), new BigDecimal("310.00"), new BigDecimal("10.00"), new BigDecimal("3.3333"))
        );
        String aiJson = """
                {
                  "predictions": [
                    {"symbol": "AAPL", "predictedChangePercentToday": 2.5, "recommendation": "BUY", "reasoning": "Strong momentum"},
                    {"symbol": "TSLA", "predictedChangePercentToday": -1.2, "recommendation": "SELL", "reasoning": "Weak day"}
                  ]
                }
                """;

        when(stockHoldingService.getHoldings(1L)).thenReturn(holdings);
        when(geminiStockPredictionClient.generateTodayPredictions(anyString())).thenReturn(aiJson);

        StockTodayPredictionsResponse response = stockPredictionService.getTodayPredictions(1L);

        assertEquals(2, response.predictions().size());
        assertEquals("AAPL", response.predictions().get(0).symbol());
        assertEquals("BUY", response.predictions().get(0).recommendation());
        assertEquals("MSFT", response.predictions().get(1).symbol());
        assertEquals("HOLD", response.predictions().get(1).recommendation());
    }

    @Test
    void getTodayPredictions_returnsHoldFallback_whenGeminiFails() {
        List<StockHoldingResponse> holdings = List.of(
                new StockHoldingResponse("NVDA", "NVIDIA", new BigDecimal("1.0000"), new BigDecimal("100.00"), new BigDecimal("100.00"), new BigDecimal("120.00"), new BigDecimal("120.00"), new BigDecimal("20.00"), new BigDecimal("20.0000"))
        );

        when(stockHoldingService.getHoldings(1L)).thenReturn(holdings);
        when(geminiStockPredictionClient.generateTodayPredictions(anyString()))
                .thenThrow(new StockModuleException(HttpStatus.BAD_GATEWAY, "Gemini API error: HTTP 404"));

        StockTodayPredictionsResponse response = stockPredictionService.getTodayPredictions(1L);

        assertEquals(1, response.predictions().size());
        assertEquals("HOLD", response.predictions().get(0).recommendation());
        assertEquals(new BigDecimal("0.00"), response.predictions().get(0).predictedProfitLossToday());
    }

    @Test
    void getTodayPredictions_throwsBadRequest_whenNoStockHoldings() {
        when(stockHoldingService.getHoldings(1L)).thenReturn(List.of());

        StockModuleException ex = assertThrows(StockModuleException.class,
                () -> stockPredictionService.getTodayPredictions(1L));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }
}

