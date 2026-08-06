package com.portfolio_management.portfolio.investments.stock.service;

import com.portfolio_management.portfolio.investments.stock.client.GeminiStockPredictionClient;
import com.portfolio_management.portfolio.investments.stock.dto.StockHoldingResponse;
import com.portfolio_management.portfolio.investments.stock.dto.StockTodayPredictionItemResponse;
import com.portfolio_management.portfolio.investments.stock.dto.StockTodayPredictionsResponse;
import com.portfolio_management.portfolio.investments.stock.exceptions.StockModuleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class StockPredictionService {

    private static final Logger log = LoggerFactory.getLogger(StockPredictionService.class);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final StockHoldingService stockHoldingService;
    private final GeminiStockPredictionClient geminiStockPredictionClient;
    private final ObjectMapper objectMapper;

    public StockPredictionService(
            StockHoldingService stockHoldingService,
            GeminiStockPredictionClient geminiStockPredictionClient,
            ObjectMapper objectMapper
    ) {
        this.stockHoldingService = stockHoldingService;
        this.geminiStockPredictionClient = geminiStockPredictionClient;
        this.objectMapper = objectMapper;
    }

    public StockTodayPredictionsResponse getTodayPredictions(Long portfolioId) {
        List<StockHoldingResponse> holdings = stockHoldingService.getHoldings(portfolioId);
        if (holdings.isEmpty()) {
            throw new StockModuleException(HttpStatus.BAD_REQUEST, "No stock holdings found for this portfolio");
        }

        Map<String, ParsedPrediction> predictionsBySymbol;
        try {
            String prompt = buildPrompt(holdings);
            String aiText = geminiStockPredictionClient.generateTodayPredictions(prompt);
            predictionsBySymbol = parsePredictionsBySymbol(aiText);
        } catch (StockModuleException ex) {
            log.warn("Gemini predictions unavailable for portfolio {}: {}", portfolioId, ex.getMessage());
            predictionsBySymbol = Map.of();
        }

        List<StockTodayPredictionItemResponse> items = new ArrayList<>();
        for (StockHoldingResponse holding : holdings) {
            String symbol = normalizeSymbol(holding.symbol());
            ParsedPrediction parsed = predictionsBySymbol.get(symbol);

            BigDecimal predictedChange = parsed == null
                    ? BigDecimal.ZERO
                    : parsed.predictedChangePercentToday().setScale(2, RoundingMode.HALF_UP);
            BigDecimal predictedProfitLoss = holding.marketValue()
                    .multiply(predictedChange)
                    .divide(HUNDRED, 2, RoundingMode.HALF_UP);

            items.add(new StockTodayPredictionItemResponse(
                    symbol,
                    holding.companyName(),
                    holding.shares(),
                    holding.currentPrice(),
                    predictedChange,
                    predictedProfitLoss,
                    parsed == null ? "HOLD" : parsed.recommendation(),
                    parsed == null ? "No valid AI prediction for this symbol. Defaulting to HOLD." : parsed.reasoning()
            ));
        }

        return new StockTodayPredictionsResponse(portfolioId, Instant.now(), items);
    }

    private String buildPrompt(List<StockHoldingResponse> holdings) {
        StringBuilder builder = new StringBuilder();
        builder.append("You are a stock analyst assistant. ")
                .append("Today is ").append(LocalDate.now()).append(". ")
                .append("Return only JSON with this exact schema: ")
                .append("{\"predictions\":[{\"symbol\":\"AAPL\",\"predictedChangePercentToday\":1.2,\"recommendation\":\"BUY|SELL|HOLD\",\"reasoning\":\"short reason\"}]}. ")
                .append("Only include symbols from this list and no others: ");

        for (StockHoldingResponse holding : holdings) {
            builder.append("[")
                    .append(normalizeSymbol(holding.symbol()))
                    .append(", currentPrice=")
                    .append(holding.currentPrice())
                    .append(", marketValue=")
                    .append(holding.marketValue())
                    .append(", unrealizedPnLPercent=")
                    .append(holding.unrealizedProfitLossPercent())
                    .append("] ");
        }
        return builder.toString();
    }

    private Map<String, ParsedPrediction> parsePredictionsBySymbol(String aiText) {
        try {
            JsonNode root = objectMapper.readTree(stripCodeFences(aiText));
            JsonNode predictionsNode = root.path("predictions");
            if (!predictionsNode.isArray()) {
                throw new StockModuleException(HttpStatus.BAD_GATEWAY,
                        "Gemini response did not include a valid predictions array");
            }

            Map<String, ParsedPrediction> predictionsBySymbol = new HashMap<>();
            for (JsonNode item : predictionsNode) {
                String symbol = normalizeSymbol(item.path("symbol").asText());
                if (symbol.isBlank()) {
                    continue;
                }
                BigDecimal change = parseDecimal(item.path("predictedChangePercentToday"));
                String recommendation = normalizeRecommendation(item.path("recommendation").asText());
                String reasoning = item.path("reasoning").asText().trim();
                if (reasoning.isBlank()) {
                    reasoning = "No rationale provided";
                }

                predictionsBySymbol.put(symbol,
                        new ParsedPrediction(symbol, change, recommendation, reasoning));
            }
            return predictionsBySymbol;
        } catch (StockModuleException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new StockModuleException(HttpStatus.BAD_GATEWAY,
                    "Failed to parse Gemini prediction response: " + ex.getMessage());
        }
    }

    private BigDecimal parseDecimal(JsonNode valueNode) {
        if (valueNode == null || valueNode.isNull() || valueNode.isMissingNode()) {
            return BigDecimal.ZERO;
        }
        if (valueNode.isNumber()) {
            return valueNode.decimalValue();
        }
        try {
            return new BigDecimal(valueNode.asText());
        } catch (Exception ignored) {
            return BigDecimal.ZERO;
        }
    }

    private String normalizeRecommendation(String value) {
        String upper = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        return switch (upper) {
            case "BUY", "SELL", "HOLD" -> upper;
            default -> "HOLD";
        };
    }

    private String normalizeSymbol(String symbol) {
        return symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
    }

    private String stripCodeFences(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.startsWith("```") && trimmed.endsWith("```")) {
            int newline = trimmed.indexOf('\n');
            if (newline > 0) {
                return trimmed.substring(newline + 1, trimmed.length() - 3).trim();
            }
        }
        return trimmed;
    }

    private record ParsedPrediction(
            String symbol,
            BigDecimal predictedChangePercentToday,
            String recommendation,
            String reasoning
    ) {
    }
}

