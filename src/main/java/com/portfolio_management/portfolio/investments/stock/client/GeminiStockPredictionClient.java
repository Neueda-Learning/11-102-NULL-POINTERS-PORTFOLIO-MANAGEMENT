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
import java.util.List;
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
            @Value("${gemini.api-key:}") String apiKey, // fixed: was gemini.api.key
            @Value("${gemini.model:gemini-2.5-flash}") String model, // fixed: was 3.6
            ObjectMapper objectMapper
    ) {
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model == null || model.isBlank() ? "gemini-2.5-flash" : model.trim();
        this.objectMapper = objectMapper;

        HttpClient httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
        this.restClient = RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory(httpClient))
                .build();
    }

    public String generateTodayPredictions(String prompt) {
        if (apiKey.isBlank()) {
            throw new StockModuleException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Gemini API key is not configured. Set GEMINI_API_KEY or gemini.api-key");
        }

        // FIXED: Correct Gemini endpoint
        URI uri = UriComponentsBuilder.fromUriString(baseUrl)
                .path("/models/{model}:generateContent")
                .queryParam("key", apiKey) // pass key as query param
                .buildAndExpand(model)
                .encode()
                .toUri();

        // FIXED: Correct request body format for Gemini
        Map<String, Object> payload = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(Map.of("text", prompt)))
                )
        );

        try {
            String responseBody = restClient.post()
                    .uri(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .onStatus(status -> !status.is2xxSuccessful(), (request, response) -> {
                        int code = response.getStatusCode().value();
                        throw new StockModuleException(HttpStatus.BAD_GATEWAY,
                                "Gemini API error: HTTP " + code + " Body: " + response.getBody().toString());
                    })
                    .body(String.class);

            JsonNode root = objectMapper.readTree(responseBody);
            return extractText(root);

        } catch (StockModuleException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new StockModuleException(HttpStatus.BAD_GATEWAY,
                    "Failed to fetch predictions from Gemini: " + ex.getMessage());
        }
    }

    private String extractText(JsonNode root) {
        // Standard Gemini response format
        JsonNode candidates = root.path("candidates");
        if (candidates.isArray() && !candidates.isEmpty()) {
            JsonNode parts = candidates.get(0).path("content").path("parts");
            if (parts
