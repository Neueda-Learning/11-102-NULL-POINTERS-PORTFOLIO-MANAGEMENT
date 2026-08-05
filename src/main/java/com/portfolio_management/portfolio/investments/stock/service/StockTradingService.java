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
import com.portfolio_management.portfolio.investments.stock.entity.TransactionType;
import com.portfolio_management.portfolio.investments.stock.exceptions.StockModuleException;
import com.portfolio_management.portfolio.investments.stock.repository.AssetRepository;
import com.portfolio_management.portfolio.investments.stock.repository.PortfolioRepository;
import com.portfolio_management.portfolio.investments.stock.repository.StockRepository;
import com.portfolio_management.portfolio.investments.stock.repository.TransactionHistoryRepository;
import com.portfolio_management.portfolio.investments.stock.websocket.FinnhubWebSocketClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Locale;

@Service
public class StockTradingService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final PortfolioRepository portfolioRepository;
    private final AssetRepository assetRepository;
    private final StockRepository stockRepository;
    private final TransactionHistoryRepository transactionHistoryRepository;
    private final FinnhubRestClient finnhubRestClient;
    private final FinnhubWebSocketClient finnhubWebSocketClient;

    public StockTradingService(
            PortfolioRepository portfolioRepository,
            AssetRepository assetRepository,
            StockRepository stockRepository,
            TransactionHistoryRepository transactionHistoryRepository,
            FinnhubRestClient finnhubRestClient,
            FinnhubWebSocketClient finnhubWebSocketClient
    ) {
        this.portfolioRepository = portfolioRepository;
        this.assetRepository = assetRepository;
        this.stockRepository = stockRepository;
        this.transactionHistoryRepository = transactionHistoryRepository;
        this.finnhubRestClient = finnhubRestClient;
        this.finnhubWebSocketClient = finnhubWebSocketClient;
    }

    @Transactional
    public TradeResponse buyStock(Long portfolioId, BuyStockRequest request) {
        String symbol = normalizeSymbol(request.symbol());
        BigDecimal quantity = scaleQuantity(request.quantity());
        validatePositiveQuantity(quantity);

        PortfolioEntity portfolio = getPortfolio(portfolioId);
        FinnhubRestClient.FinnhubProfile profile = finnhubRestClient.getCompanyProfile(symbol);
        BigDecimal marketPrice = scalePrice(finnhubRestClient.getQuote(symbol).currentPrice());
        BigDecimal totalCost = scaleMoney(marketPrice.multiply(quantity));

        BigDecimal currentCash = safeCash(portfolio.getCashBalance());
        if (currentCash.compareTo(totalCost) < 0) {
            throw new StockModuleException(HttpStatus.BAD_REQUEST,
                    "Insufficient cash balance. Required: " + totalCost + ", available: " + currentCash);
        }

        AssetEntity asset = assetRepository
                .findByPortfolioPortfolioIdAndAssetTypeAndSymbol(portfolioId, AssetType.STOCK, symbol)
                .orElseGet(() -> createStockAsset(portfolio, symbol, profile));

        StockEntity stock = stockRepository.findByAsset(asset).orElseGet(() -> createStockHolding(asset));

        BigDecimal currentQty = safeQuantity(stock.getQuantity());
        BigDecimal newQty = currentQty.add(quantity);
        BigDecimal newAveragePrice = currentQty.compareTo(ZERO) == 0
                ? marketPrice
                : scalePrice(
                stock.getPurchasePrice().multiply(currentQty)
                        .add(marketPrice.multiply(quantity))
                        .divide(newQty, 6, RoundingMode.HALF_UP)
        );

        stock.setQuantity(newQty);
        stock.setPurchasePrice(newAveragePrice);
        stock.setPurchaseDate(Instant.now());

        portfolio.setCashBalance(scaleMoney(currentCash.subtract(totalCost)));

        stockRepository.save(stock);
        portfolioRepository.save(portfolio);

        TransactionHistoryEntity transaction = recordTransaction(
                portfolio,
                asset,
                TransactionType.BUY,
                quantity,
                marketPrice
        );

        finnhubWebSocketClient.subscribeSymbol(symbol);

        return new TradeResponse(
                portfolioId,
                symbol,
                asset.getAssetName(),
                TransactionType.BUY.name(),
                quantity,
                marketPrice,
                totalCost,
                portfolio.getCashBalance(),
                stock.getQuantity(),
                stock.getPurchasePrice(),
                transaction.getTransactionDate()
        );
    }

    @Transactional
    public TradeResponse sellStock(Long portfolioId, SellStockRequest request) {
        String symbol = normalizeSymbol(request.symbol());
        BigDecimal quantityToSell = scaleQuantity(request.quantity());
        validatePositiveQuantity(quantityToSell);

        PortfolioEntity portfolio = getPortfolio(portfolioId);
        AssetEntity asset = assetRepository
                .findByPortfolioPortfolioIdAndAssetTypeAndSymbol(portfolioId, AssetType.STOCK, symbol)
                .orElseThrow(() -> new StockModuleException(HttpStatus.NOT_FOUND,
                        "No stock holding found for symbol " + symbol));

        StockEntity stock = stockRepository.findByAsset(asset)
                .orElseThrow(() -> new StockModuleException(HttpStatus.NOT_FOUND,
                        "No stock position found for symbol " + symbol));

        BigDecimal currentQty = safeQuantity(stock.getQuantity());
        if (currentQty.compareTo(quantityToSell) < 0) {
            throw new StockModuleException(HttpStatus.BAD_REQUEST,
                    "Insufficient shares. Trying to sell " + quantityToSell + " but only " + currentQty + " available");
        }

        BigDecimal marketPrice = scalePrice(finnhubRestClient.getQuote(symbol).currentPrice());
        BigDecimal proceeds = scaleMoney(marketPrice.multiply(quantityToSell));
        BigDecimal remainingQty = currentQty.subtract(quantityToSell);

        stock.setQuantity(remainingQty);
        portfolio.setCashBalance(scaleMoney(safeCash(portfolio.getCashBalance()).add(proceeds)));

        stockRepository.save(stock);
        portfolioRepository.save(portfolio);

        TransactionHistoryEntity transaction = recordTransaction(
                portfolio,
                asset,
                TransactionType.SELL,
                quantityToSell,
                marketPrice
        );

        return new TradeResponse(
                portfolioId,
                symbol,
                asset.getAssetName(),
                TransactionType.SELL.name(),
                quantityToSell,
                marketPrice,
                proceeds,
                portfolio.getCashBalance(),
                stock.getQuantity(),
                stock.getPurchasePrice(),
                transaction.getTransactionDate()
        );
    }

    private PortfolioEntity getPortfolio(Long portfolioId) {
        return portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new StockModuleException(HttpStatus.NOT_FOUND,
                        "Portfolio not found: " + portfolioId));
    }

    private AssetEntity createStockAsset(PortfolioEntity portfolio, String symbol, FinnhubRestClient.FinnhubProfile profile) {
        AssetEntity asset = new AssetEntity();
        asset.setPortfolio(portfolio);
        asset.setAssetType(AssetType.STOCK);
        asset.setSymbol(symbol);
        asset.setAssetName(profile.companyName());
        asset.setCurrency(profile.currency() == null || profile.currency().isBlank() ? "USD" : profile.currency());
        return assetRepository.save(asset);
    }

    private StockEntity createStockHolding(AssetEntity asset) {
        StockEntity stock = new StockEntity();
        stock.setAsset(asset);
        stock.setQuantity(ZERO);
        stock.setPurchasePrice(ZERO);
        stock.setPurchaseDate(Instant.now());
        return stock;
    }

    private TransactionHistoryEntity recordTransaction(
            PortfolioEntity portfolio,
            AssetEntity asset,
            TransactionType transactionType,
            BigDecimal quantity,
            BigDecimal marketPrice
    ) {
        TransactionHistoryEntity transaction = new TransactionHistoryEntity();
        transaction.setPortfolio(portfolio);
        transaction.setAsset(asset);
        transaction.setTransactionType(transactionType);
        transaction.setQuantity(quantity);
        transaction.setTransactionPrice(marketPrice);
        transaction.setTransactionDate(Instant.now());
        return transactionHistoryRepository.save(transaction);
    }

    private String normalizeSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            throw new StockModuleException(HttpStatus.BAD_REQUEST, "symbol is required");
        }
        return symbol.trim().toUpperCase(Locale.ROOT);
    }

    private BigDecimal scalePrice(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal scaleMoney(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal scaleQuantity(BigDecimal value) {
        return value.setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal safeCash(BigDecimal value) {
        return value == null ? ZERO : value;
    }

    private BigDecimal safeQuantity(BigDecimal value) {
        return value == null ? ZERO : value;
    }

    private void validatePositiveQuantity(BigDecimal quantity) {
        if (quantity.compareTo(ZERO) <= 0) {
            throw new StockModuleException(HttpStatus.BAD_REQUEST, "quantity must be greater than zero");
        }
    }
}

