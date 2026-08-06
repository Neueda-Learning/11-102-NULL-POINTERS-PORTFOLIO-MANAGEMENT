package com.portfolio_management.portfolio.portfolio.controller;

import com.portfolio_management.portfolio.portfolio.service.PortfolioService;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioControllerUnitTest {

    @Mock
    private PortfolioService portfolioService;

    private PortfolioController portfolioController;

    @BeforeEach
    void setUp() {
        portfolioController = new PortfolioController(portfolioService);
    }

    @Test
    void getSummary_returnsServicePayload() {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalPortfolioValue", new BigDecimal("12345.67"));
        when(portfolioService.getSummary()).thenReturn(summary);

        Map<String, Object> result = portfolioController.getSummary();

        assertSame(summary, result);
    }

    @Test
    void getSummary_callsServiceOnce() {
        when(portfolioService.getSummary()).thenReturn(Map.of());

        portfolioController.getSummary();

        verify(portfolioService, times(1)).getSummary();
    }

    @Test
    void getPerformanceHistory_returnsServicePayload() {
        Map<String, Object> perf = new LinkedHashMap<>();
        perf.put("labels", List.of("2026-08-01"));
        perf.put("values", List.of(new BigDecimal("1000.00")));
        when(portfolioService.getPerformanceHistory()).thenReturn(perf);

        Map<String, Object> result = portfolioController.getPerformanceHistory();

        assertSame(perf, result);
    }

    @Test
    void getPerformanceHistory_callsServiceOnce() {
        when(portfolioService.getPerformanceHistory()).thenReturn(Map.of());

        portfolioController.getPerformanceHistory();

        verify(portfolioService, times(1)).getPerformanceHistory();
    }

    @Test
    void getHoldings_returnsServicePayload_forBondType() {
        List<Map<String, Object>> holdings = List.of(Map.of("asset_type", "BOND", "asset_id", 7L));
        when(portfolioService.getHoldings("BOND")).thenReturn(holdings);

        List<Map<String, Object>> result = portfolioController.getHoldings("BOND");

        assertSame(holdings, result);
    }

    @Test
    void getHoldings_forwardsTypeArgument_toService() {
        when(portfolioService.getHoldings("CRYPTO")).thenReturn(List.of());

        portfolioController.getHoldings("CRYPTO");

        verify(portfolioService, times(1)).getHoldings("CRYPTO");
    }

    @Test
    void addHolding_returnsServicePayload() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("type", "STOCK");
        request.put("symbol", "AAPL");
        Map<String, Object> response = Map.of("success", true, "assetId", 101L);
        when(portfolioService.addHolding(request)).thenReturn(response);

        Map<String, Object> result = portfolioController.addHolding(request);

        assertSame(response, result);
    }

    @Test
    void addHolding_forwardsExactRequestMap_toService() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("type", "BOND");
        request.put("issuer", "HDFC Bank");
        request.put("amountInvested", "1000.00");
        when(portfolioService.addHolding(request)).thenReturn(Map.of("success", true));

        portfolioController.addHolding(request);

        verify(portfolioService, times(1)).addHolding(request);
    }

    @Test
    void sellHolding_callsServiceOnce_withPathId() {
        portfolioController.sellHolding(55L);

        verify(portfolioService, times(1)).sellHolding(55L);
    }

    @Test
    void getTransactions_returnsServicePayload_andCallsServiceOnce() {
        List<Map<String, Object>> tx = List.of(Map.of("transaction_id", 1L, "transaction_type", "BUY"));
        when(portfolioService.getAllTransactions()).thenReturn(tx);

        List<Map<String, Object>> result = portfolioController.getTransactions();

        assertEquals(tx, result);
        verify(portfolioService, times(1)).getAllTransactions();
    }
}

