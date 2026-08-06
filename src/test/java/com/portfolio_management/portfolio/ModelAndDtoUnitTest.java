package com.portfolio_management.portfolio;

import com.portfolio_management.portfolio.core.currency.Currency;
import com.portfolio_management.portfolio.core.money.Money;
import com.portfolio_management.portfolio.investments.asset.model.Asset;
import com.portfolio_management.portfolio.investments.asset.model.AssetType;
import com.portfolio_management.portfolio.investments.bond.dto.BondRequestDTO;
import com.portfolio_management.portfolio.investments.bond.dto.BondResponseDTO;
import com.portfolio_management.portfolio.investments.bond.model.Bond;
import com.portfolio_management.portfolio.investments.crypto.Entity.Crypto;
import com.portfolio_management.portfolio.investments.crypto.Entity.CryptoHolding;
import com.portfolio_management.portfolio.investments.crypto.Entity.Portfolio;
import com.portfolio_management.portfolio.investments.crypto.Entity.Transaction;
import com.portfolio_management.portfolio.investments.crypto.dto.CryptoPriceResponseDTO;
import com.portfolio_management.portfolio.investments.crypto.dto.CryptoRequestDTO;
import com.portfolio_management.portfolio.investments.crypto.dto.CryptoResponseDTO;
import com.portfolio_management.portfolio.investments.crypto.dto.TransactionHistoryDTO;
import com.portfolio_management.portfolio.investments.stock.Stock;
import com.portfolio_management.portfolio.investments.stock.dto.*;
import com.portfolio_management.portfolio.investments.stock.entity.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers all model, entity, and DTO classes to raise class/line coverage.
 */
class ModelAndDtoUnitTest {

    // ── Bond model ──────────────────────────────────────────────────────────

    @Test
    void bond_gettersSetters() {
        Asset asset = new Asset();
        asset.setAssetId(1L);
        asset.setPortfolioId(2L);
        asset.setAssetType(AssetType.BOND);
        asset.setAssetName("HDFC");
        asset.setSymbol("HDFCBOND");
        asset.setCurrency("USD");

        assertEquals(1L, asset.getAssetId());
        assertEquals(2L, asset.getPortfolioId());
        assertEquals(AssetType.BOND, asset.getAssetType());
        assertEquals("HDFC", asset.getAssetName());
        assertEquals("HDFCBOND", asset.getSymbol());
        assertEquals("USD", asset.getCurrency());

        Bond bond = new Bond();
        bond.setAssetId(1L);
        bond.setAsset(asset);
        bond.setIssuer("HDFC");
        bond.setInterestRate(new BigDecimal("7.5000"));
        bond.setAmountInvested(new BigDecimal("10000.00"));
        bond.setStartDate(LocalDate.of(2026, 1, 1));
        bond.setTenureMonths(12);
        bond.setMaturityDate(LocalDate.of(2027, 1, 1));

        assertEquals(1L, bond.getAssetId());
        assertSame(asset, bond.getAsset());
        assertEquals("HDFC", bond.getIssuer());
        assertEquals(new BigDecimal("7.5000"), bond.getInterestRate());
        assertEquals(new BigDecimal("10000.00"), bond.getAmountInvested());
        assertEquals(LocalDate.of(2026, 1, 1), bond.getStartDate());
        assertEquals(12, bond.getTenureMonths());
        assertEquals(LocalDate.of(2027, 1, 1), bond.getMaturityDate());
    }

    // ── AssetType enum (asset model) ────────────────────────────────────────

    @Test
    void assetType_model_enumValues() {
        assertEquals(4, AssetType.values().length);
        assertEquals(AssetType.BOND, AssetType.valueOf("BOND"));
        assertEquals(AssetType.STOCK, AssetType.valueOf("STOCK"));
        assertEquals(AssetType.CRYPTO, AssetType.valueOf("CRYPTO"));
        assertEquals(AssetType.CASH, AssetType.valueOf("CASH"));
    }

    // ── Bond DTOs ───────────────────────────────────────────────────────────

    @Test
    void bondRequestDTO_construction() {
        BondRequestDTO dto = new BondRequestDTO("HDFC", new BigDecimal("7.5"), new BigDecimal("1000"), LocalDate.of(2026, 1, 1), 12);
        assertEquals("HDFC", dto.issuer());
        assertEquals(new BigDecimal("7.5"), dto.interestRate());
        assertEquals(new BigDecimal("1000"), dto.amountInvested());
        assertEquals(LocalDate.of(2026, 1, 1), dto.startDate());
        assertEquals(12, dto.tenureMonths());
    }

    @Test
    void bondResponseDTO_construction() {
        BondResponseDTO dto = new BondResponseDTO(1L, "HDFC", new BigDecimal("7.5"), new BigDecimal("1000"),
                LocalDate.of(2026, 1, 1), 12, LocalDate.of(2027, 1, 1),
                new BigDecimal("1000.00"), new BigDecimal("75.00"), new BigDecimal("7.5000"),
                new BigDecimal("7.5000"), new BigDecimal("1075.00"));
        assertEquals(1L, dto.id());
        assertEquals("HDFC", dto.issuer());
        assertEquals(new BigDecimal("75.00"), dto.annualIncome());
        assertEquals(new BigDecimal("1075.00"), dto.maturityAmount());
    }

    // ── Crypto entities ─────────────────────────────────────────────────────

    @Test
    void crypto_entity_gettersSetters() {
        Crypto c = new Crypto();
        c.setCryptoId(1L);
        c.setAssetId(2L);
        c.setQuantity(new BigDecimal("1.5"));
        c.setBuyPrice(new BigDecimal("30000"));
        c.setCurrentPrice(new BigDecimal("35000"));
        c.setInvestedAmount(new BigDecimal("45000"));
        c.setCurrentValue(new BigDecimal("52500"));
        c.setProfitLoss(new BigDecimal("7500"));

        assertEquals(1L, c.getCryptoId());
        assertEquals(2L, c.getAssetId());
        assertEquals(new BigDecimal("1.5"), c.getQuantity());
        assertEquals(new BigDecimal("35000"), c.getCurrentPrice());
        assertEquals(new BigDecimal("7500"), c.getProfitLoss());
    }

    @Test
    void crypto_allArgsConstructor() {
        Crypto c = new Crypto(1L, 2L, new BigDecimal("1"), new BigDecimal("100"),
                new BigDecimal("200"), new BigDecimal("100"), new BigDecimal("200"), new BigDecimal("100"));
        assertEquals(1L, c.getCryptoId());
        assertEquals(new BigDecimal("100"), c.getProfitLoss());
    }

    @Test
    void cryptoAsset_entity_gettersSetters() {
        com.portfolio_management.portfolio.investments.crypto.Entity.Asset a =
                new com.portfolio_management.portfolio.investments.crypto.Entity.Asset();
        a.setAssetId(5L);
        a.setPortfolioId(3L);
        a.setSymbol("BTC");
        a.setName("Bitcoin");
        a.setAssetType("CRYPTO");
        a.setCurrency("USD");

        assertEquals(5L, a.getAssetId());
        assertEquals(3L, a.getPortfolioId());
        assertEquals("BTC", a.getSymbol());
        assertEquals("Bitcoin", a.getName());
        assertEquals("CRYPTO", a.getAssetType());
        assertEquals("USD", a.getCurrency());
    }

    @Test
    void cryptoAsset_allArgsConstructor() {
        com.portfolio_management.portfolio.investments.crypto.Entity.Asset a =
                new com.portfolio_management.portfolio.investments.crypto.Entity.Asset(1L, 2L, "ETH", "Ethereum", "CRYPTO", "USD");
        assertEquals("ETH", a.getSymbol());
    }

    @Test
    void cryptoPortfolio_entity_gettersSetters() {
        Portfolio p = new Portfolio();
        p.setPortfolioId(10L);
        p.setPortfolioName("My Portfolio");
        p.setDescription("Test");
        p.setCashBalance(new BigDecimal("5000"));
        LocalDateTime now = LocalDateTime.now();
        p.setCreatedAt(now);

        assertEquals(10L, p.getPortfolioId());
        assertEquals("My Portfolio", p.getPortfolioName());
        assertEquals("Test", p.getDescription());
        assertEquals(new BigDecimal("5000"), p.getCashBalance());
        assertEquals(now, p.getCreatedAt());
    }

    @Test
    void cryptoPortfolio_allArgsConstructor() {
        Portfolio p = new Portfolio(1L, "P1", "desc", new BigDecimal("100"), LocalDateTime.now());
        assertEquals("P1", p.getPortfolioName());
    }

    @Test
    void transaction_entity_gettersSetters() {
        Transaction t = new Transaction();
        t.setTransactionId(1L);
        t.setPortfolioId(2L);
        t.setAssetId(3L);
        t.setTransactionType("BUY");
        t.setQuantity(new BigDecimal("2.0"));
        t.setTransactionPrice(new BigDecimal("30000"));
        LocalDateTime now = LocalDateTime.now();
        t.setTransactionDate(now);

        assertEquals(1L, t.getTransactionId());
        assertEquals(2L, t.getPortfolioId());
        assertEquals(3L, t.getAssetId());
        assertEquals("BUY", t.getTransactionType());
        assertEquals(new BigDecimal("2.0"), t.getQuantity());
        assertEquals(new BigDecimal("30000"), t.getTransactionPrice());
        assertEquals(now, t.getTransactionDate());
    }

    @Test
    void transaction_allArgsConstructor() {
        LocalDateTime now = LocalDateTime.now();
        Transaction t = new Transaction(1L, 2L, 3L, "SELL", new BigDecimal("1"), new BigDecimal("100"), now);
        assertEquals("SELL", t.getTransactionType());
    }

    @Test
    void cryptoHolding_lombokGenerated() {
        LocalDateTime now = LocalDateTime.now();
        CryptoHolding h = new CryptoHolding(1L, 2L, 3L, new BigDecimal("5"), new BigDecimal("1000"), now);
        assertEquals(1L, h.getHoldingId());
        assertEquals(2L, h.getPortfolioId());
        assertEquals(3L, h.getCryptoId());
        assertEquals(new BigDecimal("5"), h.getQuantity());
        assertEquals(new BigDecimal("1000"), h.getPurchasePrice());
        assertEquals(now, h.getPurchaseDate());

        CryptoHolding h2 = new CryptoHolding();
        h2.setHoldingId(99L);
        assertEquals(99L, h2.getHoldingId());
    }

    // ── Stock entities ──────────────────────────────────────────────────────

    @Test
    void portfolioEntity_gettersSetters() {
        PortfolioEntity pe = new PortfolioEntity();
        Instant now = Instant.now();
        pe.setPortfolioId(1L);
        pe.setPortfolioName("Growth");
        pe.setDescription("desc");
        pe.setCashBalance(new BigDecimal("10000"));
        pe.setCreatedAt(now);

        assertEquals(1L, pe.getPortfolioId());
        assertEquals("Growth", pe.getPortfolioName());
        assertEquals("desc", pe.getDescription());
        assertEquals(new BigDecimal("10000"), pe.getCashBalance());
        assertEquals(now, pe.getCreatedAt());
    }

    @Test
    void assetEntity_gettersSetters() {
        PortfolioEntity pe = new PortfolioEntity();
        pe.setPortfolioId(1L);

        AssetEntity ae = new AssetEntity();
        ae.setAssetId(10L);
        ae.setPortfolio(pe);
        ae.setAssetType(com.portfolio_management.portfolio.investments.stock.entity.AssetType.STOCK);
        ae.setSymbol("AAPL");
        ae.setAssetName("Apple Inc");
        ae.setCurrency("USD");

        assertEquals(10L, ae.getAssetId());
        assertSame(pe, ae.getPortfolio());
        assertEquals(com.portfolio_management.portfolio.investments.stock.entity.AssetType.STOCK, ae.getAssetType());
        assertEquals("AAPL", ae.getSymbol());
        assertEquals("Apple Inc", ae.getAssetName());
        assertEquals("USD", ae.getCurrency());
    }

    @Test
    void stockEntity_gettersSetters() {
        AssetEntity ae = new AssetEntity();
        ae.setSymbol("GOOGL");

        StockEntity se = new StockEntity();
        se.setAssetId(5L);
        se.setAsset(ae);
        se.setQuantity(new BigDecimal("10"));
        se.setPurchasePrice(new BigDecimal("2500"));
        Instant now = Instant.now();
        se.setPurchaseDate(now);

        assertEquals(5L, se.getAssetId());
        assertSame(ae, se.getAsset());
        assertEquals(new BigDecimal("10"), se.getQuantity());
        assertEquals(new BigDecimal("2500"), se.getPurchasePrice());
        assertEquals(now, se.getPurchaseDate());
    }

    @Test
    void transactionHistoryEntity_gettersSetters() {
        PortfolioEntity pe = new PortfolioEntity();
        AssetEntity ae = new AssetEntity();
        Instant now = Instant.now();

        TransactionHistoryEntity the = new TransactionHistoryEntity();
        the.setTransactionId(1L);
        the.setPortfolio(pe);
        the.setAsset(ae);
        the.setTransactionType(TransactionType.BUY);
        the.setQuantity(new BigDecimal("5"));
        the.setTransactionPrice(new BigDecimal("150"));
        the.setTransactionDate(now);

        assertEquals(1L, the.getTransactionId());
        assertSame(pe, the.getPortfolio());
        assertSame(ae, the.getAsset());
        assertEquals(TransactionType.BUY, the.getTransactionType());
        assertEquals(new BigDecimal("5"), the.getQuantity());
        assertEquals(new BigDecimal("150"), the.getTransactionPrice());
        assertEquals(now, the.getTransactionDate());
    }

    @Test
    void stockEntity_assetTypeEnum() {
        assertEquals(4, com.portfolio_management.portfolio.investments.stock.entity.AssetType.values().length);
        assertEquals(com.portfolio_management.portfolio.investments.stock.entity.AssetType.STOCK,
                com.portfolio_management.portfolio.investments.stock.entity.AssetType.valueOf("STOCK"));
    }

    @Test
    void transactionType_enum() {
        assertEquals(2, TransactionType.values().length);
        assertEquals(TransactionType.BUY, TransactionType.valueOf("BUY"));
        assertEquals(TransactionType.SELL, TransactionType.valueOf("SELL"));
    }

    // ── Stock root entity ───────────────────────────────────────────────────

    @Test
    void stock_gettersSetters() {
        Asset asset = new Asset();
        asset.setSymbol("TSLA");

        Stock s = new Stock();
        s.setAssetId(7L);
        s.setAsset(asset);
        s.setExchange("NASDAQ");
        s.setSector("Technology");

        assertEquals(7L, s.getAssetId());
        assertSame(asset, s.getAsset());
        assertEquals("NASDAQ", s.getExchange());
        assertEquals("Technology", s.getSector());
    }

    // ── Stock DTOs (all records) ────────────────────────────────────────────

    @Test
    void buyStockRequest_record() {
        BuyStockRequest r = new BuyStockRequest("AAPL", new BigDecimal("5"));
        assertEquals("AAPL", r.symbol());
        assertEquals(new BigDecimal("5"), r.quantity());
    }

    @Test
    void sellStockRequest_record() {
        SellStockRequest r = new SellStockRequest("AAPL", new BigDecimal("2"));
        assertEquals("AAPL", r.symbol());
        assertEquals(new BigDecimal("2"), r.quantity());
    }

    @Test
    void stockQuoteResponse_record() {
        StockQuoteResponse r = new StockQuoteResponse(
                new BigDecimal("150"), new BigDecimal("1.5"), new BigDecimal("1.0"),
                new BigDecimal("155"), new BigDecimal("148"), new BigDecimal("149"),
                new BigDecimal("148.5"), 1700000000L);
        assertEquals(new BigDecimal("150"), r.currentPrice());
        assertEquals(1700000000L, r.timestamp());
    }

    @Test
    void tradeResponse_record() {
        Instant now = Instant.now();
        TradeResponse r = new TradeResponse(1L, "AAPL", "Apple", "BUY",
                new BigDecimal("5"), new BigDecimal("150"), new BigDecimal("750"),
                new BigDecimal("9250"), new BigDecimal("5"), new BigDecimal("150"), now);
        assertEquals("BUY", r.action());
        assertEquals(1L, r.portfolioId());
    }

    @Test
    void stockHoldingResponse_record() {
        StockHoldingResponse r = new StockHoldingResponse("AAPL", "Apple", new BigDecimal("10"),
                new BigDecimal("140"), new BigDecimal("1400"), new BigDecimal("155"),
                new BigDecimal("1550"), new BigDecimal("150"), new BigDecimal("10.7"));
        assertEquals("AAPL", r.symbol());
        assertEquals(new BigDecimal("150"), r.unrealizedProfitLoss());
    }

    @Test
    void stockTransactionResponse_record() {
        Instant now = Instant.now();
        StockTransactionResponse r = new StockTransactionResponse(1L, "AAPL", "BUY",
                new BigDecimal("5"), new BigDecimal("150"), now);
        assertEquals(1L, r.transactionId());
        assertEquals("BUY", r.action());
    }

    @Test
    void stockHoldingDetailsResponse_record() {
        StockHoldingResponse holding = new StockHoldingResponse("AAPL", "Apple", BigDecimal.ONE,
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO);
        List<StockTransactionResponse> txns = List.of();
        StockHoldingDetailsResponse r = new StockHoldingDetailsResponse(holding, txns);
        assertSame(holding, r.holding());
        assertTrue(r.transactions().isEmpty());
    }

    @Test
    void companyDetailsResponse_record() {
        StockQuoteResponse quote = new StockQuoteResponse(new BigDecimal("150"), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0L);
        CompanyDetailsResponse r = new CompanyDetailsResponse("AAPL", "Apple", "NASDAQ",
                "US", "Tech", "USD", "https://apple.com", quote);
        assertEquals("AAPL", r.symbol());
        assertSame(quote, r.quote());
    }

    @Test
    void marketplaceStockResponse_record() {
        MarketplaceStockResponse r = new MarketplaceStockResponse("AAPL", "Apple", "NASDAQ",
                new BigDecimal("155"), new BigDecimal("1.2"));
        assertEquals("AAPL", r.symbol());
        assertEquals(new BigDecimal("1.2"), r.dailyChangePercent());
    }

    @Test
    void marketplacePageResponse_record() {
        MarketplaceStockResponse item = new MarketplaceStockResponse("AAPL", "Apple", "NASDAQ",
                BigDecimal.ONE, BigDecimal.ZERO);
        MarketplacePageResponse r = new MarketplacePageResponse(1, 10, 5, 50L, List.of(item));
        assertEquals(1, r.page());
        assertEquals(50L, r.totalItems());
        assertEquals(1, r.items().size());
    }

    @Test
    void livepriceUpdateResponse_record() {
        Instant now = Instant.now();
        LivePriceUpdateResponse r = new LivePriceUpdateResponse("AAPL", new BigDecimal("155"), now);
        assertEquals("AAPL", r.symbol());
        assertEquals(now, r.timestamp());
    }

    @Test
    void stockSearchItemResponse_record() {
        StockSearchItemResponse r = new StockSearchItemResponse("AAPL", "AAPL", "Apple Inc", "Common Stock");
        assertEquals("AAPL", r.symbol());
        assertEquals("Common Stock", r.type());
    }

    @Test
    void stockNewsItemResponse_record() {
        StockNewsItemResponse r = new StockNewsItemResponse("AAPL", "Apple hits record", "Reuters",
                "https://reuters.com", "Summary text", LocalDate.of(2026, 8, 1));
        assertEquals("AAPL", r.symbol());
        assertEquals(LocalDate.of(2026, 8, 1), r.publishedDate());
    }

    @Test
    void stockPerformancePointResponse_record() {
        StockPerformancePointResponse r = new StockPerformancePointResponse(LocalDate.of(2026, 1, 1), new BigDecimal("150"));
        assertEquals(LocalDate.of(2026, 1, 1), r.date());
        assertEquals(new BigDecimal("150"), r.closePrice());
    }

    @Test
    void stockPerformanceResponse_record() {
        StockPerformancePointResponse point = new StockPerformancePointResponse(LocalDate.now(), BigDecimal.TEN);
        StockPerformanceResponse r = new StockPerformanceResponse("AAPL", "Apple", List.of(point));
        assertEquals("AAPL", r.symbol());
        assertEquals(1, r.points().size());
    }

    @Test
    void stockSubscriptionsRequest_record() {
        StockSubscriptionsRequest r = new StockSubscriptionsRequest(List.of("AAPL", "GOOGL"));
        assertEquals(2, r.symbols().size());
    }

    // ── Crypto DTOs ─────────────────────────────────────────────────────────

    @Test
    void cryptoRequestDTO_gettersSetters() {
        CryptoRequestDTO dto = new CryptoRequestDTO("BTC", "Bitcoin", 1L, "BUY",
                new BigDecimal("0.5"), new BigDecimal("30000"), new BigDecimal("35000"));
        assertEquals("BTC", dto.getSymbol());
        assertEquals("Bitcoin", dto.getName());
        assertEquals(1L, dto.getPortfolioId());
        assertEquals("BUY", dto.getTransactionType());
        assertEquals(new BigDecimal("0.5"), dto.getQuantity());
        assertEquals(new BigDecimal("30000"), dto.getBuyPrice());
        assertEquals(new BigDecimal("35000"), dto.getCurrentPrice());

        CryptoRequestDTO empty = new CryptoRequestDTO();
        empty.setSymbol("ETH");
        assertEquals("ETH", empty.getSymbol());
    }

    @Test
    void cryptoResponseDTO_gettersSetters() {
        CryptoResponseDTO dto = new CryptoResponseDTO(1L, "BTC", "Bitcoin",
                new BigDecimal("0.5"), new BigDecimal("30000"), new BigDecimal("35000"),
                new BigDecimal("15000"), new BigDecimal("17500"), new BigDecimal("2500"));
        assertEquals(1L, dto.getCryptoId());
        assertEquals("BTC", dto.getSymbol());
        assertEquals(new BigDecimal("2500"), dto.getProfitLoss());

        CryptoResponseDTO empty = new CryptoResponseDTO();
        empty.setCryptoId(99L);
        assertEquals(99L, empty.getCryptoId());
    }

    @Test
    void cryptoPriceResponseDTO_gettersSetters() {
        CryptoPriceResponseDTO dto = new CryptoPriceResponseDTO("BINANCE:BTCUSDT", "Bitcoin USD",
                "Bitcoin", new BigDecimal("35000"), "700B", new BigDecimal("100"),
                new BigDecimal("500"), new BigDecimal("1.5"), 1700000000L);
        assertEquals("BINANCE:BTCUSDT", dto.getSymbol());
        assertEquals(new BigDecimal("35000"), dto.getCurrentPrice());
        assertEquals("700B", dto.getMarketCap());
        assertEquals(1700000000L, dto.getTimestamp());

        CryptoPriceResponseDTO empty = new CryptoPriceResponseDTO();
        empty.setDisplayName("BTC");
        assertEquals("BTC", empty.getDisplayName());
        empty.setDescription("Bitcoin");
        assertEquals("Bitcoin", empty.getDescription());
        empty.setVolume24h(new BigDecimal("99"));
        assertEquals(new BigDecimal("99"), empty.getVolume24h());
        empty.setChange24h(new BigDecimal("5"));
        assertEquals(new BigDecimal("5"), empty.getChange24h());
        empty.setChangePercentage(new BigDecimal("0.1"));
        assertEquals(new BigDecimal("0.1"), empty.getChangePercentage());
        empty.setTimestamp(999L);
        assertEquals(999L, empty.getTimestamp());
    }

    @Test
    void transactionHistoryDTO_lombokGenerated() {
        LocalDateTime now = LocalDateTime.now();
        TransactionHistoryDTO dto = new TransactionHistoryDTO(1L, 2L, 3L, "BTC", "Bitcoin",
                "BUY", new BigDecimal("0.5"), new BigDecimal("30000"), now);
        assertEquals(1L, dto.getTransactionId());
        assertEquals("BTC", dto.getSymbol());
        assertEquals("BUY", dto.getTransactionType());

        TransactionHistoryDTO empty = new TransactionHistoryDTO();
        empty.setPortfolioId(5L);
        assertEquals(5L, empty.getPortfolioId());
        empty.setAssetId(6L);
        assertEquals(6L, empty.getAssetId());
        empty.setName("Ethereum");
        assertEquals("Ethereum", empty.getName());
        empty.setQuantity(new BigDecimal("1"));
        assertEquals(new BigDecimal("1"), empty.getQuantity());
        empty.setTransactionPrice(new BigDecimal("2000"));
        assertEquals(new BigDecimal("2000"), empty.getTransactionPrice());
        empty.setTransactionDate(now);
        assertEquals(now, empty.getTransactionDate());
    }

    // ── Core classes ─────────────────────────────────────────────────────────

    @Test
    void currency_instantiation() {
        Currency c = new Currency();
        assertNotNull(c);
    }

    @Test
    void money_instantiation() {
        Money m = new Money();
        assertNotNull(m);
    }
}

