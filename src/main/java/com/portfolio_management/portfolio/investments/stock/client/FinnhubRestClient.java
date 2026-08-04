package com.portfolio_management.portfolio.investments.stock.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio_management.portfolio.investments.stock.exceptions.StockModuleException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@Component
public class FinnhubRestClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public FinnhubRestClient(
            @Value("${finnhub.base-url}") String baseUrl,
            @Value("${finnhub.api.key}") String apiKey,
            ObjectMapper objectMapper
    ) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
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

    private QueryParam queryParam(String name, String value) {
        return new QueryParam(name, value);
    }

    private JsonNode getJson(String path, QueryParam primaryParam) {
        try {
            URI uri = UriComponentsBuilder.fromPath(path)
                    .queryParam(primaryParam.name(), primaryParam.value())
                    .queryParam("token", apiKey)
                    .build(true)
                    .toUri();

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
}

