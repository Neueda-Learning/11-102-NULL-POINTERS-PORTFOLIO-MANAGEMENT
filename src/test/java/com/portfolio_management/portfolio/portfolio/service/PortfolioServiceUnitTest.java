package com.portfolio_management.portfolio.portfolio.service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioServiceUnitTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private PortfolioService portfolioService;

    @BeforeEach
    void setUp() {
        portfolioService = new PortfolioService(jdbcTemplate);
    }

    @Test
    void getSummary_calculatesPortfolioTotalsAndPercentages() {
        when(jdbcTemplate.queryForObject(contains("SUM(amount_invested),0) FROM bonds"), eq(BigDecimal.class))).thenReturn(new BigDecimal("1000"));
        when(jdbcTemplate.queryForObject(contains("SUM(amount_invested +"), eq(BigDecimal.class))).thenReturn(new BigDecimal("1200"));
        when(jdbcTemplate.queryForObject(contains("SUM(quantity*purchase_price),0) FROM stock"), eq(BigDecimal.class))).thenReturn(new BigDecimal("500"));
        when(jdbcTemplate.queryForObject(contains("SUM(invested_amount),0) FROM crypto"), eq(BigDecimal.class))).thenReturn(new BigDecimal("300"));
        when(jdbcTemplate.queryForObject(contains("SUM(current_value),0) FROM crypto"), eq(BigDecimal.class))).thenReturn(new BigDecimal("330"));

        Map<String, Object> summary = portfolioService.getSummary();

        assertEquals(new BigDecimal("2030.00"), summary.get("totalPortfolioValue"));
        assertEquals(new BigDecimal("1800.00"), summary.get("totalInvested"));
        assertEquals(new BigDecimal("230.00"), summary.get("totalReturns"));
        assertEquals(new BigDecimal("12.78"), summary.get("totalReturnsPercent"));
    }

    @Test
    void getPerformanceHistory_returnsDefaultPoint_whenNoRows() {
        when(jdbcTemplate.queryForList(contains("FROM transaction_history"))).thenReturn(List.of());

        Map<String, Object> history = portfolioService.getPerformanceHistory();

        List<?> labels = (List<?>) history.get("labels");
        List<?> values = (List<?>) history.get("values");
        assertFalse(labels.isEmpty());
        assertEquals(1, values.size());
        assertEquals(BigDecimal.ZERO, values.get(0));
    }

    @Test
    void getPerformanceHistory_returnsRunningTotals_forRows() {
        Map<String, Object> r1 = new LinkedHashMap<>();
        r1.put("txn_date", "2026-08-01");
        r1.put("net_amount", new BigDecimal("100.00"));
        Map<String, Object> r2 = new LinkedHashMap<>();
        r2.put("txn_date", "2026-08-02");
        r2.put("net_amount", new BigDecimal("-25.00"));
        when(jdbcTemplate.queryForList(contains("FROM transaction_history"))).thenReturn(List.of(r1, r2));

        Map<String, Object> history = portfolioService.getPerformanceHistory();

        List<BigDecimal> values = (List<BigDecimal>) history.get("values");
        assertEquals(new BigDecimal("100.00"), values.get(0));
        assertEquals(new BigDecimal("75.00"), values.get(1));
    }

    @Test
    void getHoldings_all_returnsMergedTypes() {
        when(jdbcTemplate.queryForList(contains("JOIN stock s"))).thenReturn(List.of(Map.of("asset_type", "STOCK")));
        when(jdbcTemplate.queryForList(contains("JOIN bonds b"))).thenReturn(List.of(Map.of("asset_type", "BOND")));
        when(jdbcTemplate.queryForList(contains("JOIN crypto c"))).thenReturn(List.of(Map.of("asset_type", "CRYPTO")));

        List<Map<String, Object>> rows = portfolioService.getHoldings("ALL");

        assertEquals(3, rows.size());
    }

    @Test
    void addHolding_stockUpdatesExistingPosition_whenSymbolExists() {
        when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Long>>any())).thenReturn(List.of(1L));
        when(jdbcTemplate.queryForList(contains("WHERE a.portfolio_id = ?"), eq(1L), eq("AAPL"))).thenReturn(
                List.of(Map.of("asset_id", 10L, "quantity", new BigDecimal("1.0000"), "purchase_price", new BigDecimal("100.00")))
        );

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("type", "STOCK");
        request.put("symbol", "AAPL");
        request.put("assetName", "Apple Inc.");
        request.put("quantity", "1.0000");
        request.put("purchasePrice", "120.00");
        request.put("purchaseDate", "2026-08-06");

        Map<String, Object> result = portfolioService.addHolding(request);

        assertEquals(true, result.get("success"));
        verify(jdbcTemplate, times(1)).update(contains("UPDATE stock SET quantity"), any(), any(), any(), eq(10L));
    }

    @Test
    void addHolding_bondInsertsBondAndBuyTransaction() {
        when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Long>>any())).thenReturn(List.of(1L));
        when(jdbcTemplate.queryForObject(contains("SELECT COUNT(*) FROM asset WHERE symbol LIKE"), eq(Integer.class), any())).thenReturn(0);
        when(jdbcTemplate.queryForObject(contains("SELECT LAST_INSERT_ID()"), eq(Long.class))).thenReturn(55L);

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("type", "BOND");
        request.put("issuer", "HDFC Bank");
        request.put("assetName", "HDFC Bank");
        request.put("amountInvested", "1000.00");
        request.put("interestRate", "7.25");
        request.put("startDate", "2026-08-01");
        request.put("tenureMonths", 12);

        Map<String, Object> result = portfolioService.addHolding(request);

        assertEquals(true, result.get("success"));
        verify(jdbcTemplate, times(1)).update(contains("INSERT INTO bonds"), eq(55L), eq("HDFC Bank"), any(), any(), any(), eq(12), eq("2027-08-01"));
        verify(jdbcTemplate, times(1)).update(contains("transaction_history"), eq(1L), eq(55L), eq(BigDecimal.ONE), eq(new BigDecimal("1000.00")));
    }

    @Test
    void sellHolding_stockPartialSell_updatesQuantityWithoutDeletingAsset() {
        when(jdbcTemplate.queryForList(contains("SELECT * FROM asset WHERE asset_id = ?"), eq(10L))).thenReturn(
                List.of(Map.of("asset_id", 10L, "asset_type", "STOCK", "portfolio_id", 1L))
        );
        when(jdbcTemplate.queryForMap(contains("SELECT * FROM stock WHERE asset_id=?"), eq(10L))).thenReturn(
                Map.of("quantity", new BigDecimal("5.0000"), "purchase_price", new BigDecimal("100.00"))
        );

        portfolioService.sellHolding(10L, new BigDecimal("2.0000"));

        verify(jdbcTemplate, times(1)).update(contains("UPDATE stock SET quantity=?"), eq(new BigDecimal("3.0000")), eq(10L));
        verify(jdbcTemplate, never()).update(contains("DELETE FROM asset"), eq(10L));
    }

    @Test
    void sellHolding_stockThrows_whenSellQtyExceedsAvailable() {
        when(jdbcTemplate.queryForList(contains("SELECT * FROM asset WHERE asset_id = ?"), eq(10L))).thenReturn(
                List.of(Map.of("asset_id", 10L, "asset_type", "STOCK", "portfolio_id", 1L))
        );
        when(jdbcTemplate.queryForMap(contains("SELECT * FROM stock WHERE asset_id=?"), eq(10L))).thenReturn(
                Map.of("quantity", new BigDecimal("1.0000"), "purchase_price", new BigDecimal("100.00"))
        );

        RuntimeException ex = assertThrows(RuntimeException.class, () -> portfolioService.sellHolding(10L, new BigDecimal("2.0000")));

        assertTrue(ex.getMessage().contains("cannot exceed"));
    }

    @Test
    void sellHolding_cryptoDeletesAssetAfterSellTransaction() {
        when(jdbcTemplate.queryForList(contains("SELECT * FROM asset WHERE asset_id = ?"), eq(99L))).thenReturn(
                List.of(Map.of("asset_id", 99L, "asset_type", "CRYPTO", "portfolio_id", 1L))
        );
        when(jdbcTemplate.queryForMap(contains("SELECT * FROM crypto WHERE asset_id=?"), eq(99L))).thenReturn(
                Map.of("quantity", new BigDecimal("3.0000"), "current_price", new BigDecimal("50.00"))
        );

        portfolioService.sellHolding(99L, null);

        verify(jdbcTemplate, times(1)).update(contains("transaction_history"), eq(1L), eq(99L), eq(new BigDecimal("3.0000")), eq(new BigDecimal("50.00")));
        verify(jdbcTemplate, times(1)).update(contains("DELETE FROM asset WHERE asset_id=?"), eq(99L));
    }
}



