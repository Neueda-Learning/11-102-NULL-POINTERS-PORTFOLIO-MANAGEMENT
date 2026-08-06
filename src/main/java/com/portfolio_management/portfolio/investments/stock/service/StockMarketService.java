package com.portfolio_management.portfolio.investments.stock.service;

import com.portfolio_management.portfolio.investments.stock.client.FinnhubRestClient;
import com.portfolio_management.portfolio.investments.stock.dto.CompanyDetailsResponse;
import com.portfolio_management.portfolio.investments.stock.dto.MarketplacePageResponse;
import com.portfolio_management.portfolio.investments.stock.dto.MarketplaceStockResponse;
import com.portfolio_management.portfolio.investments.stock.dto.StockNewsItemResponse;
import com.portfolio_management.portfolio.investments.stock.dto.StockPerformancePointResponse;
import com.portfolio_management.portfolio.investments.stock.dto.StockPerformanceResponse;
import com.portfolio_management.portfolio.investments.stock.dto.StockQuoteResponse;
import com.portfolio_management.portfolio.investments.stock.dto.StockSearchItemResponse;
import com.portfolio_management.portfolio.investments.stock.exceptions.StockModuleException;
import com.portfolio_management.portfolio.investments.stock.websocket.FinnhubWebSocketClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class StockMarketService {

    private static final Logger log = LoggerFactory.getLogger(StockMarketService.class);

    private static final List<String> MARKET_SYMBOLS = List.of(
            "AAPL", "MSFT", "GOOGL", "AMZN", "NVDA", "META", "TSLA", "BRK.B", "UNH", "JNJ",
            "V", "XOM", "JPM", "WMT", "PG", "MA", "HD", "CVX", "LLY", "ABBV",
            "BAC", "KO", "PFE", "PEP", "COST", "MRK", "AVGO", "TMO", "CSCO", "MCD",
            "DHR", "ACN", "ADBE", "CRM", "ABT", "NKE", "VZ", "WFC", "DIS", "TXN",
            "LIN", "CMCSA", "ORCL", "PM", "QCOM", "INTU", "UPS", "AMGN", "IBM", "LOW",
            "RTX", "MS", "SPGI", "CAT", "MDT", "GS", "INTC", "BLK", "NOW", "PLD",
            "DE", "AXP", "GILD", "LMT", "AMAT", "SYK", "T", "BKNG", "ISRG", "ADI",
            "C", "CB", "MMC", "ELV", "MO", "VRTX", "ZTS", "MDLZ", "DUK", "SO",
            "PNC", "TGT", "CL", "SHW", "REGN", "EOG", "CSX", "FIS", "USB", "CI",
            "AON", "FDX", "NSC", "APD", "HUM", "ITW", "MAR", "GD", "MCO", "FISV"
    );

    private final FinnhubRestClient finnhubRestClient;
    private final FinnhubWebSocketClient finnhubWebSocketClient;
    private final Map<String, FinnhubRestClient.FinnhubProfile> profileCache = new ConcurrentHashMap<>();

    public StockMarketService(FinnhubRestClient finnhubRestClient, FinnhubWebSocketClient finnhubWebSocketClient) {
        this.finnhubRestClient = finnhubRestClient;
        this.finnhubWebSocketClient = finnhubWebSocketClient;
    }

    public List<StockSearchItemResponse> searchStocks(String query) {
        String normalized = normalizeSymbolOrQuery(query);
        return finnhubRestClient.searchStocks(normalized, 15)
                .stream()
                .map(item -> new StockSearchItemResponse(item.symbol(), item.displaySymbol(), item.description(), item.type()))
                .toList();
    }

    public CompanyDetailsResponse getCompanyDetails(String symbol) {
        String normalizedSymbol = normalizeSymbolOrQuery(symbol);
        finnhubWebSocketClient.subscribeSymbol(normalizedSymbol);

        FinnhubRestClient.FinnhubProfile profile = finnhubRestClient.getCompanyProfile(normalizedSymbol);
        FinnhubRestClient.FinnhubQuote quote = finnhubRestClient.getQuote(normalizedSymbol);

        return new CompanyDetailsResponse(
                profile.symbol(),
                profile.companyName(),
                profile.exchange(),
                profile.country(),
                profile.sector(),
                profile.currency(),
                profile.website(),
                toQuoteResponse(quote)
        );
    }

    public MarketplacePageResponse getMarketplacePage(int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 10);
        int safePage = Math.max(page, 1);

        int from = (safePage - 1) * safeSize;
        int to = Math.min(from + safeSize, MARKET_SYMBOLS.size());

        if (from >= MARKET_SYMBOLS.size()) {
            return new MarketplacePageResponse(safePage, safeSize, totalPages(safeSize), MARKET_SYMBOLS.size(), List.of());
        }

        List<String> symbols = MARKET_SYMBOLS.subList(from, to);
        finnhubWebSocketClient.subscribeSymbols(symbols);

        List<MarketplaceStockResponse> items = symbols.stream()
                .map(this::loadMarketplaceItem)
                .toList();

        return new MarketplacePageResponse(safePage, safeSize, totalPages(safeSize), MARKET_SYMBOLS.size(), items);
    }

    public StockPerformanceResponse getPerformance(String symbol) {
        String normalizedSymbol = normalizeSymbolOrQuery(symbol);
        FinnhubRestClient.FinnhubProfile profile = loadProfileOrFallback(normalizedSymbol);
        List<StockPerformancePointResponse> points = getPerformanceWithFallback(normalizedSymbol, 10)
                .stream()
                .map(point -> new StockPerformancePointResponse(point.date(), point.closePrice()))
                .toList();
        return new StockPerformanceResponse(normalizedSymbol, profile.companyName(), points);
    }

    public List<StockNewsItemResponse> getNews(String symbol, int limit) {
        String normalizedSymbol = normalizeSymbolOrQuery(symbol);
        return finnhubRestClient.getCompanyNews(normalizedSymbol, 7, limit)
                .stream()
                .map(item -> new StockNewsItemResponse(
                        item.symbol(),
                        item.headline(),
                        item.source(),
                        item.url(),
                        item.summary(),
                        item.publishedDate()
                ))
                .toList();
    }

    private StockQuoteResponse toQuoteResponse(FinnhubRestClient.FinnhubQuote quote) {
        return new StockQuoteResponse(
                quote.currentPrice(),
                quote.change(),
                quote.changePercent(),
                quote.high(),
                quote.low(),
                quote.open(),
                quote.previousClose(),
                quote.timestamp()
        );
    }

    private int totalPages(int size) {
        return (int) Math.ceil((double) MARKET_SYMBOLS.size() / size);
    }

    private MarketplaceStockResponse loadMarketplaceItem(String symbol) {
        try {
            FinnhubRestClient.FinnhubQuote quote = getQuoteWithFallback(symbol);
            FinnhubRestClient.FinnhubProfile profile = profileCache.computeIfAbsent(symbol, this::loadProfileOrFallback);
            return new MarketplaceStockResponse(
                    symbol,
                    profile.companyName(),
                    profile.exchange(),
                    quote.currentPrice(),
                    quote.changePercent()
            );
        } catch (Exception ex) {
            log.warn("Quote lookup failed for symbol {}: {}", symbol, ex.getMessage());
            return new MarketplaceStockResponse(symbol, symbol, "N/A", BigDecimal.ZERO, BigDecimal.ZERO);
        }
    }

    private FinnhubRestClient.FinnhubProfile loadProfileOrFallback(String symbol) {
        try {
            return finnhubRestClient.getCompanyProfile(symbol);
        } catch (Exception ex) {
            log.warn("Profile lookup failed for symbol {}: {}", symbol, ex.getMessage());
            return new FinnhubRestClient.FinnhubProfile(symbol, symbol, "N/A", "", "", "USD", "");
        }
    }

    private FinnhubRestClient.FinnhubQuote getQuoteWithFallback(String symbol) {
        try {
            return finnhubRestClient.getQuote(symbol);
        } catch (Exception primaryError) {
            if (!symbol.contains(".")) {
                throw primaryError;
            }
            String alternate = symbol.replace('.', '-');
            log.debug("Retrying quote lookup for {} using alternate symbol {}", symbol, alternate);
            return finnhubRestClient.getQuote(alternate);
        }
    }

    private List<FinnhubRestClient.FinnhubCandlePoint> getPerformanceWithFallback(String symbol, int days) {
        try {
            return finnhubRestClient.getDailyPerformance(symbol, days);
        } catch (Exception primaryError) {
            if (!symbol.contains(".")) {
                throw primaryError;
            }
            String alternate = symbol.replace('.', '-');
            log.debug("Retrying performance lookup for {} using alternate symbol {}", symbol, alternate);
            return finnhubRestClient.getDailyPerformance(alternate, days);
        }
    }

    private String normalizeSymbolOrQuery(String value) {
        if (value == null || value.isBlank()) {
            throw new StockModuleException(HttpStatus.BAD_REQUEST, "query/symbol is required");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }
}


