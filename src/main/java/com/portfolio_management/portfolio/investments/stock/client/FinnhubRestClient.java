package com.portfolio_management.portfolio.investments.stock.client;

import com.portfolio_management.portfolio.investments.stock.exceptions.StockModuleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class FinnhubRestClient {

    private static final Logger log = LoggerFactory.getLogger(FinnhubRestClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String apiKey;

    public FinnhubRestClient(
            @Value("${finnhub.base-url}") String baseUrl,
            @Value("${finnhub.api.key}") String apiKey,
            ObjectMapper objectMapper
    ) {
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        if (this.apiKey.isBlank()) {
            throw new IllegalStateException("finnhub.api.key is required for live market data.");
        }

        HttpClient httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        this.restClient = RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory(httpClient))
                .build();
        this.objectMapper = objectMapper;

        log.info("Finnhub REST client initialized in LIVE mode.");
    }

    public List<FinnhubSearchItem> searchStocks(String query, int limit) {
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

    private QueryParam queryParam(String name, String value) {
        return new QueryParam(name, value);
    }

    private JsonNode getJson(String path, QueryParam... params) {
        try {
            String normalizedPath = path.startsWith("/") ? path : "/" + path;
            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl).path(normalizedPath);
            for (QueryParam param : params) {
                builder.queryParam(param.name(), param.value());
            }
            URI uri = builder.queryParam("token", apiKey).build(true).toUri();

            String body = restClient.get()
                    .uri(uri)
                    .header("Accept", "application/json")
                    .header("User-Agent", "portfolio-management/1.0")
                    .retrieve()
                    .onStatus(status -> !status.is2xxSuccessful(), (req, resp) -> {
                        int code = resp.getStatusCode().value();
                        if (code == 401 || code == 403) {
                            throw new StockModuleException(HttpStatus.BAD_GATEWAY,
                                    "Finnhub rejected authentication (HTTP " + code + "). Check finnhub.api.key/quota.");
                        }
                        if (code == 429) {
                            throw new StockModuleException(HttpStatus.TOO_MANY_REQUESTS,
                                    "Finnhub rate limit exceeded (HTTP 429). Please retry shortly.");
                        }
                        throw new StockModuleException(HttpStatus.BAD_GATEWAY,
                                "Finnhub API error: HTTP " + code);
                    })
                    .body(String.class);

            if (body == null || body.isBlank()) {
                throw new StockModuleException(HttpStatus.BAD_GATEWAY, "Finnhub returned an empty response");
            }
            if (body.trim().charAt(0) == '<') {
                log.warn("Finnhub returned non-JSON payload for path {}. Verify key/quota. Body prefix: {}",
                        normalizedPath,
                        body.substring(0, Math.min(body.length(), 120)).replace('\n', ' '));
                throw new StockModuleException(HttpStatus.BAD_GATEWAY,
                        "Finnhub returned HTML instead of JSON. Check API key/quota.");
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

    private String normalizeBaseUrl(String rawBaseUrl) {
        if (rawBaseUrl == null || rawBaseUrl.isBlank()) {
            throw new IllegalStateException("finnhub.base-url is required for live market data.");
        }
        String trimmed = rawBaseUrl.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
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
}

