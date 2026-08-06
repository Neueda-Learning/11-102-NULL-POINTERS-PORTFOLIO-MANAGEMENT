package com.portfolio_management.portfolio.investments.stock.client;

import com.portfolio_management.portfolio.investments.stock.exceptions.StockModuleException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.util.Map;

@Component
public class GeminiStockPredictionClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String apiKey;
    private final String model;

    public GeminiStockPredictionClient(
            @Value("${gemini.base-url:https://generativelanguage.googleapis.com/v1beta}") String baseUrl,
            @Value("${gemini.api.key:}") String apiKey,
            @Value("${gemini.model:gemini-3.6-flash}") String model,
            ObjectMapper objectMapper
    ) {
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model == null || model.isBlank() ? "gemini-3.6-flash" : model.trim();
        this.objectMapper = objectMapper;

        HttpClient httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
        this.restClient = RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory(httpClient))
                .build();
    }

    public String generateTodayPredictions(String prompt) {
        if (apiKey.isBlank()) {
            throw new StockModuleException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Gemini API key is not configured. Set GEMINI_API_KEY or gemini.api.key");
        }

        URI uri = UriComponentsBuilder.fromUriString(baseUrl)
                .path("/interactions")
                .build()
                .encode()
                .toUri();

        Map<String, Object> payload = Map.of(
                "model", model,
                "input", prompt
        );

        try {
            String responseBody = restClient.post()
                    .uri(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("x-goog-api-key", apiKey)
                    .body(payload)
                    .retrieve()
                    .onStatus(status -> !status.is2xxSuccessful(), (request, response) -> {
                        int code = response.getStatusCode().value();
                        if (code == 401 || code == 403) {
                            throw new StockModuleException(HttpStatus.BAD_GATEWAY,
                                    "Gemini rejected authentication (HTTP " + code + "). Check API key/quota.");
                        }
                        if (code == 404) {
                            throw new StockModuleException(HttpStatus.BAD_GATEWAY,
                                    "Gemini API error: HTTP 404. Verify gemini.base-url and gemini.model.");
                        }
                        if (code == 429) {
                            throw new StockModuleException(HttpStatus.TOO_MANY_REQUESTS,
                                    "Gemini rate limit exceeded (HTTP 429). Please retry shortly.");
                        }
                        throw new StockModuleException(HttpStatus.BAD_GATEWAY,
                                "Gemini API error: HTTP " + code);
                    })
                    .body(String.class);

            if (responseBody == null || responseBody.isBlank()) {
                throw new StockModuleException(HttpStatus.BAD_GATEWAY,
                        "Gemini interactions response is empty");
            }

            JsonNode root = objectMapper.readTree(responseBody);
            String text = extractText(root);
            if (text.isBlank()) {
                throw new StockModuleException(HttpStatus.BAD_GATEWAY,
                        "Gemini interactions response did not include prediction text");
            }
            return text;
        } catch (StockModuleException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new StockModuleException(HttpStatus.BAD_GATEWAY,
                    "Failed to fetch predictions from Gemini: " + ex.getMessage());
        }
    }

    private String extractText(JsonNode root) {
        // If API already returns the target schema directly, pass it through.
        if (root.path("predictions").isArray()) {
            return root.toString();
        }

        String fromOutputText = textual(root.path("output_text"));
        if (!fromOutputText.isBlank()) {
            return fromOutputText;
        }

        String fromOutputTextCamel = textual(root.path("outputText"));
        if (!fromOutputTextCamel.isBlank()) {
            return fromOutputTextCamel;
        }

        String fromResult = textual(root.path("result").path("output_text"));
        if (!fromResult.isBlank()) {
            return fromResult;
        }

        String fromResponse = textual(root.path("response").path("output_text"));
        if (!fromResponse.isBlank()) {
            return fromResponse;
        }

        String fromMessage = textual(root.path("message").path("content"));
        if (!fromMessage.isBlank()) {
            return fromMessage;
        }

        JsonNode output = root.path("output");
        String directOutput = textual(output);
        if (!directOutput.isBlank()) {
            return directOutput;
        }
        if (output.isArray()) {
            for (JsonNode item : output) {
                String text = textual(item.path("text"));
                if (!text.isBlank()) {
                    return text;
                }
                JsonNode content = item.path("content");
                if (content.isArray()) {
                    for (JsonNode part : content) {
                        text = textual(part.path("text"));
                        if (!text.isBlank()) {
                            return text;
                        }
                    }
                }
            }
        }

        JsonNode candidates = root.path("candidates");
        if (candidates.isArray()) {
            for (JsonNode candidate : candidates) {
                JsonNode parts = candidate.path("content").path("parts");
                if (parts.isArray()) {
                    for (JsonNode part : parts) {
                        String text = textual(part.path("text"));
                        if (!text.isBlank()) {
                            return text;
                        }
                    }
                }
            }
        }

        JsonNode choices = root.path("choices");
        if (choices.isArray()) {
            for (JsonNode choice : choices) {
                String content = textual(choice.path("message").path("content"));
                if (!content.isBlank()) {
                    return content;
                }
            }
        }

        String discovered = findPredictionJsonRecursively(root);
        if (!discovered.isBlank()) {
            return discovered;
        }

        return "";
    }

    private String findPredictionJsonRecursively(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        if (node.isTextual()) {
            String value = node.asText().trim();
            if (looksLikePredictionJson(value)) {
                return value;
            }
            return "";
        }
        for (JsonNode child : node) {
            String found = findPredictionJsonRecursively(child);
            if (!found.isBlank()) {
                return found;
            }
        }
        return "";
    }

    private boolean looksLikePredictionJson(String text) {
        if (text == null) {
            return false;
        }
        String trimmed = text.trim();
        return trimmed.startsWith("{")
                && trimmed.endsWith("}")
                && trimmed.contains("\"predictions\"");
    }

    private String textual(JsonNode node) {
        return node != null && node.isTextual() ? node.asText().trim() : "";
    }

    private String normalizeBaseUrl(String rawBaseUrl) {
        if (rawBaseUrl == null || rawBaseUrl.isBlank()) {
            return "https://generativelanguage.googleapis.com/v1beta";
        }
        String normalized = rawBaseUrl.trim();
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.endsWith("/models")) {
            normalized = normalized.substring(0, normalized.length() - "/models".length());
        }
        if (!normalized.matches(".*/v\\d+(beta)?$")) {
            normalized = normalized + "/v1beta";
        }
        return normalized;
    }
}


