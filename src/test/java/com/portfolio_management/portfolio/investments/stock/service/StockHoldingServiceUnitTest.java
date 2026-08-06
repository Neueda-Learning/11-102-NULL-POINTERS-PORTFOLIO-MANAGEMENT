package com.portfolio_management.portfolio.investments.stock.service;

import com.portfolio_management.portfolio.investments.stock.client.FinnhubRestClient;
import com.portfolio_management.portfolio.investments.stock.dto.StockHoldingDetailsResponse;
import com.portfolio_management.portfolio.investments.stock.dto.StockHoldingResponse;
import com.portfolio_management.portfolio.investments.stock.dto.StockTransactionResponse;
import com.portfolio_management.portfolio.investments.stock.entity.AssetEntity;
import com.portfolio_management.portfolio.investments.stock.entity.AssetType;
import com.portfolio_management.portfolio.investments.stock.entity.PortfolioEntity;
import com.portfolio_management.portfolio.investments.stock.entity.StockEntity;
import com.portfolio_management.portfolio.investments.stock.entity.TransactionHistoryEntity;
import com.portfolio_management.portfolio.investments.stock.entity.TransactionType;
import com.portfolio_management.portfolio.investments.stock.exceptions.StockModuleException;
import com.portfolio_management.portfolio.investments.stock.repository.StockRepository;
import com.portfolio_management.portfolio.investments.stock.repository.TransactionHistoryRepository;
import com.portfolio_management.portfolio.investments.stock.websocket.FinnhubWebSocketClient;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockHoldingServiceUnitTest {

    @Mock
    private StockRepository stockRepository;

    @Mock
    private TransactionHistoryRepository transactionHistoryRepository;

    @Mock
    private FinnhubRestClient finnhubRestClient;

    @Mock
    private FinnhubWebSocketClient finnhubWebSocketClient;

    private StockHoldingService stockHoldingService;

    @BeforeEach
    void setUp() {
        stockHoldingService = new StockHoldingService(stockRepository, transactionHistoryRepository, finnhubRestClient, finnhubWebSocketClient);
    }

    @Test
    void getHoldings_returnsEmptyList_whenNoRows() {
        when(stockRepository.findByAssetPortfolioPortfolioIdAndQuantityGreaterThan(1L, BigDecimal.ZERO)).thenReturn(List.of());

        List<StockHoldingResponse> result = stockHoldingService.getHoldings(1L);

        assertTrue(result.isEmpty());
    }

    @Test
    void getHoldings_usesWebSocketPrice_whenAvailable() {
        StockEntity stock = stock(sampleAsset("AAPL", "Apple"), "2.0000", "100.00");
        when(stockRepository.findByAssetPortfolioPortfolioIdAndQuantityGreaterThan(1L, BigDecimal.ZERO)).thenReturn(List.of(stock));
        when(finnhubWebSocketClient.getLatestPrice("AAPL")).thenReturn(Optional.of(new BigDecimal("120.00")));

        StockHoldingResponse response = stockHoldingService.getHoldings(1L).get(0);

        assertEquals(new BigDecimal("120.00"), response.currentPrice());
        assertEquals(new BigDecimal("240.00"), response.marketValue());
        verify(finnhubWebSocketClient, times(1)).subscribeSymbol("AAPL");
    }

    @Test
    void getHoldings_fallsBackToRestQuote_whenWebSocketPriceMissing() {
        StockEntity stock = stock(sampleAsset("MSFT", "Microsoft"), "1.5000", "200.00");
        when(stockRepository.findByAssetPortfolioPortfolioIdAndQuantityGreaterThan(1L, BigDecimal.ZERO)).thenReturn(List.of(stock));
        when(finnhubWebSocketClient.getLatestPrice("MSFT")).thenReturn(Optional.empty());
        when(finnhubRestClient.getQuote("MSFT")).thenReturn(quote("210.55"));

        StockHoldingResponse response = stockHoldingService.getHoldings(1L).get(0);

        assertEquals(new BigDecimal("210.55"), response.currentPrice());
        assertEquals(new BigDecimal("315.83"), response.marketValue());
    }

    @Test
    void getHoldings_calculatesProfitAndPercent() {
        StockEntity stock = stock(sampleAsset("NVDA", "NVIDIA"), "3.0000", "100.00");
        when(stockRepository.findByAssetPortfolioPortfolioIdAndQuantityGreaterThan(1L, BigDecimal.ZERO)).thenReturn(List.of(stock));
        when(finnhubWebSocketClient.getLatestPrice("NVDA")).thenReturn(Optional.of(new BigDecimal("120.00")));

        StockHoldingResponse response = stockHoldingService.getHoldings(1L).get(0);

        assertEquals(new BigDecimal("300.00"), response.costBasis());
        assertEquals(new BigDecimal("360.00"), response.marketValue());
        assertEquals(new BigDecimal("60.00"), response.unrealizedProfitLoss());
        assertEquals(new BigDecimal("20.0000"), response.unrealizedProfitLossPercent());
    }

    @Test
    void getHoldingDetails_returnsHoldingAndTransactions_whenFound() {
        AssetEntity asset = sampleAsset("TSLA", "Tesla");
        StockEntity stock = stock(asset, "1.0000", "250.00");
        TransactionHistoryEntity tx = transaction(asset, 1L, "1.0000", "300.00");

        when(stockRepository.findByAssetPortfolioPortfolioIdAndAssetSymbol(1L, "TSLA")).thenReturn(Optional.of(stock));
        when(finnhubWebSocketClient.getLatestPrice("TSLA")).thenReturn(Optional.of(new BigDecimal("300.00")));
        when(transactionHistoryRepository.findByPortfolioPortfolioIdAndAssetSymbolOrderByTransactionDateDesc(1L, "TSLA")).thenReturn(List.of(tx));

        StockHoldingDetailsResponse response = stockHoldingService.getHoldingDetails(1L, "TSLA");

        assertEquals("TSLA", response.holding().symbol());
        assertEquals(1, response.transactions().size());
        assertEquals("BUY", response.transactions().get(0).action());
    }

    @Test
    void getHoldingDetails_returnsNotFound_whenMissing() {
        when(stockRepository.findByAssetPortfolioPortfolioIdAndAssetSymbol(1L, "IBM")).thenReturn(Optional.empty());

        StockModuleException ex = assertThrows(StockModuleException.class, () -> stockHoldingService.getHoldingDetails(1L, "IBM"));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void getTransactionsBySymbol_returnsMappedRows() {
        AssetEntity asset = sampleAsset("META", "Meta");
        TransactionHistoryEntity tx1 = transaction(asset, 11L, "2.0000", "300.00");
        TransactionHistoryEntity tx2 = transaction(asset, 12L, "1.0000", "320.00");

        when(transactionHistoryRepository.findByPortfolioPortfolioIdAndAssetSymbolOrderByTransactionDateDesc(1L, "META")).thenReturn(List.of(tx1, tx2));

        List<StockTransactionResponse> result = stockHoldingService.getTransactionsBySymbol(1L, "META");

        assertEquals(2, result.size());
        assertEquals(11L, result.get(0).transactionId());
        assertEquals("META", result.get(0).symbol());
    }

    @Test
    void getTransactionsBySymbol_normalizesSymbolToUppercase() {
        when(transactionHistoryRepository.findByPortfolioPortfolioIdAndAssetSymbolOrderByTransactionDateDesc(1L, "AAPL")).thenReturn(List.of());

        stockHoldingService.getTransactionsBySymbol(1L, "aapl");

        verify(transactionHistoryRepository, times(1)).findByPortfolioPortfolioIdAndAssetSymbolOrderByTransactionDateDesc(1L, "AAPL");
    }

    @Test
    void getTransactionsBySymbol_throwsBadRequest_whenSymbolBlank() {
        StockModuleException ex = assertThrows(StockModuleException.class, () -> stockHoldingService.getTransactionsBySymbol(1L, " "));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void getHoldingDetails_normalizesSymbolToUppercase() {
        AssetEntity asset = sampleAsset("AAPL", "Apple");
        StockEntity stock = stock(asset, "1.0000", "200.00");
        when(stockRepository.findByAssetPortfolioPortfolioIdAndAssetSymbol(1L, "AAPL")).thenReturn(Optional.of(stock));
        when(finnhubWebSocketClient.getLatestPrice("AAPL")).thenReturn(Optional.of(new BigDecimal("220.00")));
        when(transactionHistoryRepository.findByPortfolioPortfolioIdAndAssetSymbolOrderByTransactionDateDesc(1L, "AAPL")).thenReturn(List.of());

        stockHoldingService.getHoldingDetails(1L, "aapl");

        verify(stockRepository, times(1)).findByAssetPortfolioPortfolioIdAndAssetSymbol(1L, "AAPL");
    }

    private AssetEntity sampleAsset(String symbol, String name) {
        PortfolioEntity p = new PortfolioEntity();
        p.setPortfolioId(1L);
        p.setPortfolioName("Default");
        p.setCashBalance(new BigDecimal("1000.00"));
        p.setCreatedAt(Instant.parse("2026-08-06T00:00:00Z"));

        AssetEntity asset = new AssetEntity();
        asset.setAssetId(10L);
        asset.setPortfolio(p);
        asset.setAssetType(AssetType.STOCK);
        asset.setSymbol(symbol);
        asset.setAssetName(name);
        asset.setCurrency("USD");
        return asset;
    }

    private StockEntity stock(AssetEntity asset, String qty, String buyPrice) {
        StockEntity stock = new StockEntity();
        stock.setAssetId(asset.getAssetId());
        stock.setAsset(asset);
        stock.setQuantity(new BigDecimal(qty));
        stock.setPurchasePrice(new BigDecimal(buyPrice));
        stock.setPurchaseDate(Instant.parse("2026-08-06T00:00:00Z"));
        return stock;
    }

    private TransactionHistoryEntity transaction(AssetEntity asset, Long id, String qty, String price) {
        TransactionHistoryEntity tx = new TransactionHistoryEntity();
        tx.setTransactionId(id);
        tx.setAsset(asset);
        tx.setPortfolio(asset.getPortfolio());
        tx.setTransactionType(TransactionType.BUY);
        tx.setQuantity(new BigDecimal(qty));
        tx.setTransactionPrice(new BigDecimal(price));
        tx.setTransactionDate(Instant.parse("2026-08-06T00:00:00Z"));
        return tx;
    }

    private FinnhubRestClient.FinnhubQuote quote(String currentPrice) {
        return new FinnhubRestClient.FinnhubQuote(
                new BigDecimal(currentPrice),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                1722900000L
        );
    }
}

