package com.portfolio_management.portfolio.investments.stock.service;

import com.portfolio_management.portfolio.investments.stock.client.FinnhubRestClient;
import com.portfolio_management.portfolio.investments.stock.dto.StockHoldingDetailsResponse;
import com.portfolio_management.portfolio.investments.stock.dto.StockHoldingResponse;
import com.portfolio_management.portfolio.investments.stock.dto.StockTransactionResponse;
import com.portfolio_management.portfolio.investments.stock.entity.StockEntity;
import com.portfolio_management.portfolio.investments.stock.entity.TransactionHistoryEntity;
import com.portfolio_management.portfolio.investments.stock.exceptions.StockModuleException;
import com.portfolio_management.portfolio.investments.stock.repository.StockRepository;
import com.portfolio_management.portfolio.investments.stock.repository.TransactionHistoryRepository;
import com.portfolio_management.portfolio.investments.stock.websocket.FinnhubWebSocketClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;

@Service
public class StockHoldingService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final StockRepository stockRepository;
    private final TransactionHistoryRepository transactionHistoryRepository;
    private final FinnhubRestClient finnhubRestClient;
    private final FinnhubWebSocketClient finnhubWebSocketClient;

    public StockHoldingService(
            StockRepository stockRepository,
            TransactionHistoryRepository transactionHistoryRepository,
            FinnhubRestClient finnhubRestClient,
            FinnhubWebSocketClient finnhubWebSocketClient
    ) {
        this.stockRepository = stockRepository;
        this.transactionHistoryRepository = transactionHistoryRepository;
        this.finnhubRestClient = finnhubRestClient;
        this.finnhubWebSocketClient = finnhubWebSocketClient;
    }

    public List<StockHoldingResponse> getHoldings(Long portfolioId) {
        List<StockEntity> holdings = stockRepository.findByAssetPortfolioPortfolioIdAndQuantityGreaterThan(portfolioId, ZERO);
        return holdings.stream().map(this::toHoldingResponse).toList();
    }

    public StockHoldingDetailsResponse getHoldingDetails(Long portfolioId, String symbol) {
        String normalizedSymbol = normalizeSymbol(symbol);
        StockEntity holding = stockRepository.findByAssetPortfolioPortfolioIdAndAssetSymbol(portfolioId, normalizedSymbol)
                .orElseThrow(() -> new StockModuleException(HttpStatus.NOT_FOUND,
                        "Holding not found for symbol " + normalizedSymbol));

        StockHoldingResponse response = toHoldingResponse(holding);
        List<StockTransactionResponse> transactions = getTransactionsBySymbol(portfolioId, normalizedSymbol);
        return new StockHoldingDetailsResponse(response, transactions);
    }

    public List<StockTransactionResponse> getTransactionsBySymbol(Long portfolioId, String symbol) {
        String normalizedSymbol = normalizeSymbol(symbol);

        List<TransactionHistoryEntity> history = transactionHistoryRepository
                .findByPortfolioPortfolioIdAndAssetSymbolOrderByTransactionDateDesc(portfolioId, normalizedSymbol);

        return history.stream().map(tx -> new StockTransactionResponse(
                tx.getTransactionId(),
                tx.getAsset().getSymbol(),
                tx.getTransactionType().name(),
                tx.getQuantity(),
                tx.getTransactionPrice(),
                tx.getTransactionDate()
        )).toList();
    }

    private StockHoldingResponse toHoldingResponse(StockEntity stock) {
        String symbol = stock.getAsset().getSymbol();
        finnhubWebSocketClient.subscribeSymbol(symbol);

        BigDecimal shares = stock.getQuantity();
        BigDecimal avgCost = stock.getPurchasePrice();
        BigDecimal currentPrice = getLiveOrLatestPrice(symbol);

        BigDecimal costBasis = scaleMoney(shares.multiply(avgCost));
        BigDecimal marketValue = scaleMoney(shares.multiply(currentPrice));
        BigDecimal unrealizedPnL = scaleMoney(marketValue.subtract(costBasis));
        BigDecimal unrealizedPnLPercent = costBasis.compareTo(ZERO) == 0
                ? ZERO
                : unrealizedPnL.multiply(BigDecimal.valueOf(100)).divide(costBasis, 4, RoundingMode.HALF_UP);

        return new StockHoldingResponse(
                symbol,
                stock.getAsset().getAssetName(),
                shares,
                avgCost,
                costBasis,
                currentPrice,
                marketValue,
                unrealizedPnL,
                unrealizedPnLPercent
        );
    }

    private BigDecimal getLiveOrLatestPrice(String symbol) {
        return finnhubWebSocketClient.getLatestPrice(symbol)
                .orElseGet(() -> finnhubRestClient.getQuote(symbol).currentPrice())
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal scaleMoney(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private String normalizeSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            throw new StockModuleException(HttpStatus.BAD_REQUEST, "symbol is required");
        }
        return symbol.trim().toUpperCase(Locale.ROOT);
    }
}

