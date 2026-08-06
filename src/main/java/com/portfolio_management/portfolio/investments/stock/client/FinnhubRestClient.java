package com.portfolio_management.portfolio.investments.stock.client;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.portfolio_management.portfolio.investments.stock.exceptions.StockModuleException;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Component
public class FinnhubRestClient {

    private static final Logger log = LoggerFactory.getLogger(FinnhubRestClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final boolean mockMode;

    public FinnhubRestClient(
            @Value("${finnhub.base-url}") String baseUrl,
            @Value("${finnhub.api.key}") String apiKey,
            @Value("${finnhub.mock-mode:false}") boolean mockMode,
            ObjectMapper objectMapper
    ) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.mockMode = mockMode;
        if (mockMode) {
            log.info("⚠️ Finnhub MOCK MODE ENABLED - Using demo data instead of real API");
        }
    }

    public List<FinnhubSearchItem> searchStocks(String query, int limit) {
        if (mockMode) {
            return mockSearchStocks(query, limit);
        }
        JsonNode root = getJson("/search", queryParam("q", query));
        JsonNode resultNode = root.path("result");
        List<FinnhubSearchItem> items = new ArrayList<>();
        if (!resultNode.isArray()) {
            return items;
        }

        for (JsonNode item : resultNode) {
            String symbol = item.path("symbol").asText("");
            if (symbol.isBlank()) {
                continue;
            }
            items.add(new FinnhubSearchItem(
                    symbol,
                    item.path("displaySymbol").asText(symbol),
                    item.path("description").asText(""),
                    item.path("type").asText("")
            ));
            if (items.size() >= limit) {
                break;
            }
        }
        return items;
    }

    public FinnhubProfile getCompanyProfile(String symbol) {
        if (mockMode) {
            return mockGetCompanyProfile(symbol);
        }
        JsonNode root = getJson("/stock/profile2", queryParam("symbol", symbol));
        String ticker = root.path("ticker").asText(symbol);
        return new FinnhubProfile(
                ticker,
                root.path("name").asText(symbol),
                root.path("exchange").asText(""),
                root.path("country").asText(""),
                root.path("finnhubIndustry").asText(""),
                root.path("currency").asText("USD"),
                root.path("weburl").asText("")
        );
    }

    public FinnhubQuote getQuote(String symbol) {
        if (mockMode) {
            return mockGetQuote(symbol);
        }
        JsonNode root = getJson("/quote", queryParam("symbol", symbol));
        BigDecimal currentPrice = decimal(root, "c");
        if (currentPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new StockModuleException(HttpStatus.BAD_GATEWAY,
                    "Finnhub returned an invalid market price for symbol " + symbol);
        }

        return new FinnhubQuote(
                currentPrice,
                decimal(root, "d"),
                decimal(root, "dp"),
                decimal(root, "h"),
                decimal(root, "l"),
                decimal(root, "o"),
                decimal(root, "pc"),
                root.path("t").asLong(0L)
        );
    }

    public List<FinnhubCandlePoint> getDailyPerformance(String symbol, int days) {
        if (mockMode) {
            return mockGetDailyPerformance(symbol, days);
        }

        long to = Instant.now().getEpochSecond();
        long from = Instant.now().minusSeconds(60L * 60 * 24 * (days + 10L)).getEpochSecond();

        JsonNode root = getJson(
                "/stock/candle",
                queryParam("symbol", symbol),
                queryParam("resolution", "D"),
                queryParam("from", String.valueOf(from)),
                queryParam("to", String.valueOf(to))
        );

        if (!"ok".equalsIgnoreCase(root.path("s").asText())) {
            throw new StockModuleException(HttpStatus.BAD_GATEWAY,
                    "Finnhub did not return enough candle data for symbol " + symbol);
        }

        JsonNode closeNode = root.path("c");
        JsonNode timeNode = root.path("t");
        int size = Math.min(closeNode.size(), timeNode.size());
        List<FinnhubCandlePoint> points = new ArrayList<>();

        for (int i = 0; i < size; i++) {
            BigDecimal close = closeNode.get(i).isNumber() ? closeNode.get(i).decimalValue() : BigDecimal.ZERO;
            long epochSecond = timeNode.get(i).asLong(0L);
            if (close.compareTo(BigDecimal.ZERO) <= 0 || epochSecond <= 0) {
                continue;
            }
            points.add(new FinnhubCandlePoint(LocalDate.ofInstant(Instant.ofEpochSecond(epochSecond), ZoneOffset.UTC), close));
        }

        if (points.isEmpty()) {
            throw new StockModuleException(HttpStatus.BAD_GATEWAY,
                    "No valid candle points returned for symbol " + symbol);
        }

        points.sort(Comparator.comparing(FinnhubCandlePoint::date));
        if (points.size() <= days) {
            return points;
        }
        return points.subList(points.size() - days, points.size());
    }

    public List<FinnhubNewsItem> getCompanyNews(String symbol, int daysBack, int limit) {
        String normalizedSymbol = symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
        int safeLimit = Math.max(1, Math.min(limit, 10));
        if (mockMode) {
            return mockGetCompanyNews(normalizedSymbol, safeLimit);
        }

        LocalDate to = LocalDate.now(ZoneOffset.UTC);
        LocalDate from = to.minusDays(Math.max(daysBack, 1));
        JsonNode root = getJson(
                "/company-news",
                queryParam("symbol", normalizedSymbol),
                queryParam("from", from.toString()),
                queryParam("to", to.toString())
        );

        List<FinnhubNewsItem> items = new ArrayList<>();
        if (!root.isArray()) {
            return items;
        }

        for (JsonNode item : root) {
            String headline = item.path("headline").asText("");
            String url = item.path("url").asText("");
            if (headline.isBlank() || url.isBlank()) {
                continue;
            }
            long epoch = item.path("datetime").asLong(0L);
            LocalDate publishedDate = epoch > 0
                    ? Instant.ofEpochSecond(epoch).atZone(ZoneOffset.UTC).toLocalDate()
                    : to;
            items.add(new FinnhubNewsItem(
                    normalizedSymbol,
                    headline,
                    item.path("source").asText("Finnhub"),
                    url,
                    item.path("summary").asText(""),
                    publishedDate
            ));
            if (items.size() >= safeLimit) {
                break;
            }
        }
        return items;
    }

    private QueryParam queryParam(String name, String value) {
        return new QueryParam(name, value);
    }

    private JsonNode getJson(String path, QueryParam... params) {
        try {
            UriComponentsBuilder builder = UriComponentsBuilder.fromPath(path);
            for (QueryParam param : params) {
                builder.queryParam(param.name(), param.value());
            }
            URI uri = builder.queryParam("token", apiKey).build(true).toUri();

            String body = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(String.class);

            if (body == null || body.isBlank()) {
                throw new StockModuleException(HttpStatus.BAD_GATEWAY, "Finnhub returned an empty response");
            }
            return objectMapper.readTree(body);
        } catch (StockModuleException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new StockModuleException(HttpStatus.BAD_GATEWAY,
                    "Failed to fetch market data from Finnhub: " + ex.getMessage());
        }
    }

    private BigDecimal decimal(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return BigDecimal.ZERO;
        }
        if (value.isNumber()) {
            return value.decimalValue();
        }
        try {
            return new BigDecimal(value.asText("0"));
        } catch (NumberFormatException ignored) {
            return BigDecimal.ZERO;
        }
    }

    private record QueryParam(String name, String value) {
    }

    private List<FinnhubSearchItem> mockSearchStocks(String query, int limit) {
        query = query.toUpperCase();
        List<FinnhubSearchItem> items = new ArrayList<>();
        String[][] data = {
            {"AAPL", "AAPL", "Apple Inc", "Common Stock"},
            {"TSLA", "TSLA", "Tesla Inc", "Common Stock"},
            {"MSFT", "MSFT", "Microsoft Corporation", "Common Stock"},
            {"GOOGL", "GOOGL", "Alphabet Inc", "Common Stock"},
            {"AMZN", "AMZN", "Amazon.com Inc", "Common Stock"}
        };
        for (String[] row : data) {
            if (row[0].contains(query) || row[2].toUpperCase().contains(query)) {
                items.add(new FinnhubSearchItem(row[0], row[1], row[2], row[3]));
                if (items.size() >= limit) break;
            }
        }
        return items;
    }

    private FinnhubProfile mockGetCompanyProfile(String symbol) {
        return switch (symbol.toUpperCase()) {
            case "AAPL" -> new FinnhubProfile("AAPL", "Apple Inc", "NASDAQ", "US", "Technology", "USD", "https://www.apple.com");
            case "TSLA" -> new FinnhubProfile("TSLA", "Tesla Inc", "NASDAQ", "US", "Consumer Cyclical", "USD", "https://www.tesla.com");
            case "MSFT" -> new FinnhubProfile("MSFT", "Microsoft Corporation", "NASDAQ", "US", "Technology", "USD", "https://www.microsoft.com");
            case "GOOGL" -> new FinnhubProfile("GOOGL", "Alphabet Inc", "NASDAQ", "US", "Communication Services", "USD", "https://www.google.com");
            case "AMZN" -> new FinnhubProfile("AMZN", "Amazon.com Inc", "NASDAQ", "US", "Consumer Cyclical", "USD", "https://www.amazon.com");
            default -> new FinnhubProfile(symbol, symbol + " Corp", "NASDAQ", "US", "Technology", "USD", "https://company.com");
        };
    }

    private FinnhubQuote mockGetQuote(String symbol) {
        return switch (symbol.toUpperCase()) {
            case "AAPL" -> new FinnhubQuote(
                new java.math.BigDecimal("192.11"),
                new java.math.BigDecimal("1.22"),
                new java.math.BigDecimal("0.64"),
                new java.math.BigDecimal("193.00"),
                new java.math.BigDecimal("189.71"),
                new java.math.BigDecimal("190.20"),
                new java.math.BigDecimal("190.89"),
                System.currentTimeMillis() / 1000
            );
            case "TSLA" -> new FinnhubQuote(
                new java.math.BigDecimal("245.50"),
                new java.math.BigDecimal("-2.15"),
                new java.math.BigDecimal("-0.87"),
                new java.math.BigDecimal("248.99"),
                new java.math.BigDecimal("244.00"),
                new java.math.BigDecimal("247.80"),
                new java.math.BigDecimal("247.65"),
                System.currentTimeMillis() / 1000
            );
            default -> new FinnhubQuote(
                new java.math.BigDecimal("150.00"),
                new java.math.BigDecimal("0.00"),
                new java.math.BigDecimal("0.00"),
                new java.math.BigDecimal("150.50"),
                new java.math.BigDecimal("149.50"),
                new java.math.BigDecimal("149.75"),
                new java.math.BigDecimal("150.00"),
                System.currentTimeMillis() / 1000
            );
        };
    }

    private List<FinnhubCandlePoint> mockGetDailyPerformance(String symbol, int days) {
        List<FinnhubCandlePoint> points = new ArrayList<>();
        BigDecimal base = mockGetQuote(symbol).currentPrice();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = LocalDate.now(ZoneOffset.UTC).minusDays(i);
            BigDecimal drift = BigDecimal.valueOf((days - i) * 0.35);
            BigDecimal wave = BigDecimal.valueOf((i % 3) - 1).multiply(BigDecimal.valueOf(0.9));
            points.add(new FinnhubCandlePoint(date, base.subtract(drift).add(wave).setScale(2, java.math.RoundingMode.HALF_UP)));
        }
        return points;
    }

    private List<FinnhubNewsItem> mockGetCompanyNews(String symbol, int limit) {
        List<FinnhubNewsItem> items = List.of(
                new FinnhubNewsItem(symbol, symbol + " launches new AI platform", "MockWire", "https://example.com/news/" + symbol + "/1", "Analysts expect stronger cloud growth after the launch.", LocalDate.now().minusDays(1)),
                new FinnhubNewsItem(symbol, symbol + " reports stronger than expected revenue", "MockWire", "https://example.com/news/" + symbol + "/2", "Quarterly results beat consensus estimates across key segments.", LocalDate.now().minusDays(2)),
                new FinnhubNewsItem(symbol, symbol + " faces regulatory review in key market", "MockWire", "https://example.com/news/" + symbol + "/3", "Investors are watching for impacts on margin guidance and expansion plans.", LocalDate.now().minusDays(3))
        );
        return items.subList(0, Math.min(limit, items.size()));
    }

    public record FinnhubSearchItem(String symbol, String displaySymbol, String description, String type) {
    }

    public record FinnhubProfile(
            String symbol,
            String companyName,
            String exchange,
            String country,
            String sector,
            String currency,
            String website
    ) {
    }

    public record FinnhubQuote(
            BigDecimal currentPrice,
            BigDecimal change,
            BigDecimal changePercent,
            BigDecimal high,
            BigDecimal low,
            BigDecimal open,
            BigDecimal previousClose,
            Long timestamp
    ) {
    }

    public record FinnhubCandlePoint(
            LocalDate date,
            BigDecimal closePrice
    ) {
    }

    public record FinnhubNewsItem(
            String symbol,
            String headline,
            String source,
            String url,
            String summary,
            LocalDate publishedDate
    ) {
    }
}


