package com.portfolio_management.portfolio.investments.stock.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio_management.portfolio.investments.stock.dto.LivePriceUpdateResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Instant;
import java.util.Collection;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class FinnhubWebSocketClient {

    private final ObjectMapper objectMapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final String websocketUrl;
    private final String apiKey;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final Set<String> subscribedSymbols = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<String, BigDecimal> latestPrices = new ConcurrentHashMap<>();

    private volatile WebSocket webSocket;

    public FinnhubWebSocketClient(
            ObjectMapper objectMapper,
            SimpMessagingTemplate messagingTemplate,
            @Value("${finnhub.websocket-url:wss://ws.finnhub.io}") String websocketUrl,
            @Value("${finnhub.api.key}") String apiKey
    ) {
        this.objectMapper = objectMapper;
        this.messagingTemplate = messagingTemplate;
        this.websocketUrl = websocketUrl;
        this.apiKey = apiKey;
    }

    @PostConstruct
    public void init() {
        connect();
    }

    public void subscribeSymbols(Collection<String> symbols) {
        for (String symbol : symbols) {
            subscribeSymbol(symbol);
        }
    }

    public void subscribeSymbol(String rawSymbol) {
        if (rawSymbol == null || rawSymbol.isBlank()) {
            return;
        }

        String symbol = rawSymbol.trim().toUpperCase(Locale.ROOT);
        boolean added = subscribedSymbols.add(symbol);
        if (!added) {
            return;
        }

        if (webSocket == null) {
            connect();
        }

        sendSubscription(symbol);
    }

    public Optional<BigDecimal> getLatestPrice(String rawSymbol) {
        if (rawSymbol == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(latestPrices.get(rawSymbol.toUpperCase(Locale.ROOT)));
    }

    @PreDestroy
    public void close() {
        if (webSocket != null) {
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "shutdown");
        }
        scheduler.shutdownNow();
    }

    private synchronized void connect() {
        try {
            URI uri = URI.create(websocketUrl + "?token=" + apiKey);
            HttpClient client = HttpClient.newHttpClient();
            CompletableFuture<WebSocket> future = client.newWebSocketBuilder()
                    .buildAsync(uri, new FinnhubListener());
            this.webSocket = future.join();

            for (String symbol : subscribedSymbols) {
                sendSubscription(symbol);
            }
        } catch (Exception ignored) {
            scheduleReconnect();
        }
    }

    private void sendSubscription(String symbol) {
        WebSocket current = webSocket;
        if (current == null) {
            return;
        }
        String payload = "{\"type\":\"subscribe\",\"symbol\":\"" + symbol + "\"}";
        current.sendText(payload, true);
    }

    private void scheduleReconnect() {
        scheduler.schedule(this::connect, 5, TimeUnit.SECONDS);
    }

    private void processMessage(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            if (!"trade".equals(root.path("type").asText())) {
                return;
            }

            for (JsonNode trade : root.path("data")) {
                String symbol = trade.path("s").asText("").toUpperCase(Locale.ROOT);
                if (symbol.isBlank()) {
                    continue;
                }

                BigDecimal price = trade.path("p").decimalValue();
                long timestamp = trade.path("t").asLong(System.currentTimeMillis());
                latestPrices.put(symbol, price);

                LivePriceUpdateResponse update = new LivePriceUpdateResponse(symbol, price, Instant.ofEpochMilli(timestamp));
                messagingTemplate.convertAndSend("/topic/stocks/" + symbol, update);
                messagingTemplate.convertAndSend("/topic/stocks/prices", update);
            }
        } catch (Exception ignored) {
            // Ignore malformed events and continue processing the stream.
        }
    }

    private class FinnhubListener implements WebSocket.Listener {

        private final StringBuilder buffer = new StringBuilder();

        @Override
        public void onOpen(WebSocket webSocket) {
            WebSocket.Listener.super.onOpen(webSocket);
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                String message = buffer.toString();
                buffer.setLength(0);
                processMessage(message);
            }
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            FinnhubWebSocketClient.this.webSocket = null;
            scheduleReconnect();
            return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            FinnhubWebSocketClient.this.webSocket = null;
            scheduleReconnect();
        }
    }
}

