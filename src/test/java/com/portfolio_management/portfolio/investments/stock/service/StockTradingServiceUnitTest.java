package com.portfolio_management.portfolio.investments.stock.service;

import com.portfolio_management.portfolio.investments.stock.client.FinnhubRestClient;
import com.portfolio_management.portfolio.investments.stock.dto.BuyStockRequest;
import com.portfolio_management.portfolio.investments.stock.dto.SellStockRequest;
import com.portfolio_management.portfolio.investments.stock.dto.TradeResponse;
import com.portfolio_management.portfolio.investments.stock.entity.AssetEntity;
import com.portfolio_management.portfolio.investments.stock.entity.AssetType;
import com.portfolio_management.portfolio.investments.stock.entity.PortfolioEntity;
import com.portfolio_management.portfolio.investments.stock.entity.StockEntity;
import com.portfolio_management.portfolio.investments.stock.entity.TransactionHistoryEntity;
import com.portfolio_management.portfolio.investments.stock.exceptions.StockModuleException;
import com.portfolio_management.portfolio.investments.stock.repository.AssetRepository;
import com.portfolio_management.portfolio.investments.stock.repository.PortfolioRepository;
import com.portfolio_management.portfolio.investments.stock.repository.StockRepository;
import com.portfolio_management.portfolio.investments.stock.repository.TransactionHistoryRepository;
import com.portfolio_management.portfolio.investments.stock.websocket.FinnhubWebSocketClient;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockTradingServiceUnitTest {

    @Mock
    private PortfolioRepository portfolioRepository;
    @Mock
    private AssetRepository assetRepository;
    @Mock
    private StockRepository stockRepository;
    @Mock
    private TransactionHistoryRepository transactionHistoryRepository;
    @Mock
    private FinnhubRestClient finnhubRestClient;
    @Mock
    private FinnhubWebSocketClient finnhubWebSocketClient;

    private StockTradingService stockTradingService;

    @BeforeEach
    void setUp() {
        stockTradingService = new StockTradingService(
                portfolioRepository,
                assetRepository,
                stockRepository,
                transactionHistoryRepository,
                finnhubRestClient,
                finnhubWebSocketClient
        );
    }

    @Test
    void buyStock_createsNewAssetAndHolding_recordsBuy_andSubscribes() {
        PortfolioEntity portfolio = portfolio(1L, "1000.00");
        AssetEntity createdAsset = stockAsset(11L, portfolio, "AAPL", "Apple", "USD");

        when(portfolioRepository.findById(1L)).thenReturn(Optional.of(portfolio));
        when(finnhubRestClient.getCompanyProfile("AAPL")).thenReturn(profile("AAPL", "Apple", "USD"));
        when(finnhubRestClient.getQuote("AAPL")).thenReturn(quote("100.00"));
        when(assetRepository.findByPortfolioPortfolioIdAndAssetTypeAndSymbol(1L, AssetType.STOCK, "AAPL")).thenReturn(Optional.empty());
        when(assetRepository.save(any(AssetEntity.class))).thenReturn(createdAsset);
        when(stockRepository.findByAsset(createdAsset)).thenReturn(Optional.empty());
        when(stockRepository.save(any(StockEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(portfolioRepository.save(any(PortfolioEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionHistoryRepository.save(any(TransactionHistoryEntity.class))).thenAnswer(invocation -> {
            TransactionHistoryEntity tx = invocation.getArgument(0);
            if (tx.getTransactionDate() == null) tx.setTransactionDate(Instant.parse("2026-08-06T00:00:00Z"));
            return tx;
        });

        TradeResponse response = stockTradingService.buyStock(1L, new BuyStockRequest("AAPL", new BigDecimal("2.0000")));

        assertEquals("BUY", response.action());
        assertEquals(new BigDecimal("200.00"), response.totalAmount());
        assertEquals(new BigDecimal("800.00"), response.remainingCashBalance());
        verify(finnhubWebSocketClient, times(1)).subscribeSymbol("AAPL");
        verify(transactionHistoryRepository, times(1)).save(any(TransactionHistoryEntity.class));
    }

    @Test
    void buyStock_recalculatesWeightedAverage_whenHoldingExists() {
        PortfolioEntity portfolio = portfolio(1L, "5000.00");
        AssetEntity asset = stockAsset(22L, portfolio, "MSFT", "Microsoft", "USD");
        StockEntity existing = stock(asset, "2.0000", "100.00");

        when(portfolioRepository.findById(1L)).thenReturn(Optional.of(portfolio));
        when(finnhubRestClient.getCompanyProfile("MSFT")).thenReturn(profile("MSFT", "Microsoft", "USD"));
        when(finnhubRestClient.getQuote("MSFT")).thenReturn(quote("200.00"));
        when(assetRepository.findByPortfolioPortfolioIdAndAssetTypeAndSymbol(1L, AssetType.STOCK, "MSFT")).thenReturn(Optional.of(asset));
        when(stockRepository.findByAsset(asset)).thenReturn(Optional.of(existing));
        when(stockRepository.save(any(StockEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(portfolioRepository.save(any(PortfolioEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionHistoryRepository.save(any(TransactionHistoryEntity.class))).thenAnswer(invocation -> {
            TransactionHistoryEntity tx = invocation.getArgument(0);
            if (tx.getTransactionDate() == null) tx.setTransactionDate(Instant.parse("2026-08-06T00:00:00Z"));
            return tx;
        });

        TradeResponse response = stockTradingService.buyStock(1L, new BuyStockRequest("MSFT", new BigDecimal("1.0000")));

        assertEquals(new BigDecimal("3.0000"), response.totalShares());
        assertEquals(new BigDecimal("133.33"), response.averagePurchasePrice());
    }

    @Test
    void buyStock_appliesScaleRules_forQuantityPriceTotalAndCash() {
        PortfolioEntity portfolio = portfolio(1L, "1000.00");
        AssetEntity asset = stockAsset(33L, portfolio, "NVDA", "NVIDIA", "USD");

        when(portfolioRepository.findById(1L)).thenReturn(Optional.of(portfolio));
        when(finnhubRestClient.getCompanyProfile("NVDA")).thenReturn(profile("NVDA", "NVIDIA", "USD"));
        when(finnhubRestClient.getQuote("NVDA")).thenReturn(quote("12.345"));
        when(assetRepository.findByPortfolioPortfolioIdAndAssetTypeAndSymbol(1L, AssetType.STOCK, "NVDA")).thenReturn(Optional.of(asset));
        when(stockRepository.findByAsset(asset)).thenReturn(Optional.empty());
        when(stockRepository.save(any(StockEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(portfolioRepository.save(any(PortfolioEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionHistoryRepository.save(any(TransactionHistoryEntity.class))).thenAnswer(invocation -> {
            TransactionHistoryEntity tx = invocation.getArgument(0);
            if (tx.getTransactionDate() == null) tx.setTransactionDate(Instant.parse("2026-08-06T00:00:00Z"));
            return tx;
        });

        TradeResponse response = stockTradingService.buyStock(1L, new BuyStockRequest("NVDA", new BigDecimal("1.23456")));

        assertEquals(new BigDecimal("1.2346"), response.quantity());
        assertEquals(new BigDecimal("12.35"), response.executedPrice());
        assertEquals(new BigDecimal("15.25"), response.totalAmount());
        assertEquals(new BigDecimal("984.75"), response.remainingCashBalance());
    }

    @Test
    void buyStock_throwsBadRequest_whenSymbolBlank() {
        StockModuleException ex = assertThrows(StockModuleException.class,
                () -> stockTradingService.buyStock(1L, new BuyStockRequest(" ", new BigDecimal("1.0"))));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void buyStock_throwsBadRequest_whenQuantityNotPositive() {
        StockModuleException ex = assertThrows(StockModuleException.class,
                () -> stockTradingService.buyStock(1L, new BuyStockRequest("AAPL", BigDecimal.ZERO)));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void buyStock_throwsNotFound_whenPortfolioMissing() {
        when(portfolioRepository.findById(99L)).thenReturn(Optional.empty());

        StockModuleException ex = assertThrows(StockModuleException.class,
                () -> stockTradingService.buyStock(99L, new BuyStockRequest("AAPL", new BigDecimal("1.0"))));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void buyStock_throwsBadRequest_whenCashIsInsufficient_andSkipsWrites() {
        PortfolioEntity portfolio = portfolio(1L, "50.00");
        when(portfolioRepository.findById(1L)).thenReturn(Optional.of(portfolio));
        when(finnhubRestClient.getCompanyProfile("AAPL")).thenReturn(profile("AAPL", "Apple", "USD"));
        when(finnhubRestClient.getQuote("AAPL")).thenReturn(quote("100.00"));

        StockModuleException ex = assertThrows(StockModuleException.class,
                () -> stockTradingService.buyStock(1L, new BuyStockRequest("AAPL", new BigDecimal("1.0000"))));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verify(assetRepository, never()).save(any(AssetEntity.class));
        verify(stockRepository, never()).save(any(StockEntity.class));
        verify(transactionHistoryRepository, never()).save(any(TransactionHistoryEntity.class));
        verify(finnhubWebSocketClient, never()).subscribeSymbol(any());
    }

    @Test
    void sellStock_succeeds_updatesCashAndShares_andRecordsTransaction() {
        PortfolioEntity portfolio = portfolio(1L, "100.00");
        AssetEntity asset = stockAsset(77L, portfolio, "TSLA", "Tesla", "USD");
        StockEntity stock = stock(asset, "5.0000", "250.00");

        when(portfolioRepository.findById(1L)).thenReturn(Optional.of(portfolio));
        when(assetRepository.findByPortfolioPortfolioIdAndAssetTypeAndSymbol(1L, AssetType.STOCK, "TSLA")).thenReturn(Optional.of(asset));
        when(stockRepository.findByAsset(asset)).thenReturn(Optional.of(stock));
        when(finnhubRestClient.getQuote("TSLA")).thenReturn(quote("300.00"));
        when(stockRepository.save(any(StockEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(portfolioRepository.save(any(PortfolioEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionHistoryRepository.save(any(TransactionHistoryEntity.class))).thenAnswer(invocation -> {
            TransactionHistoryEntity tx = invocation.getArgument(0);
            if (tx.getTransactionDate() == null) tx.setTransactionDate(Instant.parse("2026-08-06T00:00:00Z"));
            return tx;
        });

        TradeResponse response = stockTradingService.sellStock(1L, new SellStockRequest("TSLA", new BigDecimal("2.0000")));

        assertEquals("SELL", response.action());
        assertEquals(new BigDecimal("3.0000"), response.totalShares());
        assertEquals(new BigDecimal("700.00"), response.remainingCashBalance());
        verify(transactionHistoryRepository, times(1)).save(any(TransactionHistoryEntity.class));
    }

    @Test
    void sellStock_throwsNotFound_whenAssetMissing() {
        PortfolioEntity portfolio = portfolio(1L, "500.00");
        when(portfolioRepository.findById(1L)).thenReturn(Optional.of(portfolio));
        when(assetRepository.findByPortfolioPortfolioIdAndAssetTypeAndSymbol(1L, AssetType.STOCK, "IBM")).thenReturn(Optional.empty());

        StockModuleException ex = assertThrows(StockModuleException.class,
                () -> stockTradingService.sellStock(1L, new SellStockRequest("IBM", new BigDecimal("1.0000"))));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void sellStock_throwsBadRequest_whenSellQuantityExceedsHoldings() {
        PortfolioEntity portfolio = portfolio(1L, "500.00");
        AssetEntity asset = stockAsset(88L, portfolio, "META", "Meta", "USD");
        StockEntity stock = stock(asset, "1.0000", "100.00");

        when(portfolioRepository.findById(1L)).thenReturn(Optional.of(portfolio));
        when(assetRepository.findByPortfolioPortfolioIdAndAssetTypeAndSymbol(1L, AssetType.STOCK, "META")).thenReturn(Optional.of(asset));
        when(stockRepository.findByAsset(asset)).thenReturn(Optional.of(stock));

        StockModuleException ex = assertThrows(StockModuleException.class,
                () -> stockTradingService.sellStock(1L, new SellStockRequest("META", new BigDecimal("2.0000"))));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verify(stockRepository, never()).save(any(StockEntity.class));
    }

    private PortfolioEntity portfolio(Long id, String cash) {
        PortfolioEntity p = new PortfolioEntity();
        p.setPortfolioId(id);
        p.setPortfolioName("Test");
        p.setDescription("Test portfolio");
        p.setCashBalance(new BigDecimal(cash));
        p.setCreatedAt(Instant.parse("2026-08-06T00:00:00Z"));
        return p;
    }

    private AssetEntity stockAsset(Long assetId, PortfolioEntity portfolio, String symbol, String name, String currency) {
        AssetEntity a = new AssetEntity();
        a.setAssetId(assetId);
        a.setPortfolio(portfolio);
        a.setAssetType(AssetType.STOCK);
        a.setSymbol(symbol);
        a.setAssetName(name);
        a.setCurrency(currency);
        return a;
    }

    private StockEntity stock(AssetEntity asset, String quantity, String purchasePrice) {
        StockEntity s = new StockEntity();
        s.setAssetId(asset.getAssetId());
        s.setAsset(asset);
        s.setQuantity(new BigDecimal(quantity));
        s.setPurchasePrice(new BigDecimal(purchasePrice));
        s.setPurchaseDate(Instant.parse("2026-08-06T00:00:00Z"));
        return s;
    }

    private FinnhubRestClient.FinnhubProfile profile(String symbol, String name, String currency) {
        return new FinnhubRestClient.FinnhubProfile(symbol, name, "NASDAQ", "US", "Tech", currency, "https://example.com");
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


