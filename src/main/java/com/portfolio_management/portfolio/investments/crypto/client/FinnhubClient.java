package com.portfolio_management.portfolio.investments.crypto.client;

import com.portfolio_management.portfolio.investments.crypto.config.FinnhubConfig;
import com.portfolio_management.portfolio.investments.crypto.dto.CryptoPriceResponseDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
public class FinnhubClient {

    private final RestTemplate restTemplate;
    private final FinnhubConfig finnhubConfig;

    public FinnhubClient(RestTemplate restTemplate, FinnhubConfig finnhubConfig) {
        this.restTemplate = restTemplate;
        this.finnhubConfig = finnhubConfig;
    }

    /**
     * Fetch cryptocurrency quote from Finnhub API
     *
     * @param symbol Cryptocurrency symbol (e.g., "BTCUSD", "ETHUSD")
     * @return CryptoPriceResponseDTO with current price and market data
     */
    public CryptoPriceResponseDTO getCryptoQuote(String symbol) {
        try {
            String finnhubSymbol = normalizeForFinnhub(symbol);
            String url = finnhubConfig.getFinnhubBaseUrl() + "/quote" +
                    "?symbol=" + finnhubSymbol +
                    "&token=" + finnhubConfig.getFinnhubApiKey();

            log.info("Fetching crypto price for symbol: {} (finnhub: {})", symbol, finnhubSymbol);
            CryptoPriceResponseDTO response = restTemplate.getForObject(url, CryptoPriceResponseDTO.class);

            if (response == null || response.getCurrentPrice() == null || response.getCurrentPrice().signum() <= 0) {
                throw new RuntimeException("Finnhub returned empty price payload for symbol: " + finnhubSymbol);
            }

            log.info("Successfully fetched price for {}: {}", finnhubSymbol, response.getCurrentPrice());

            return response;
        } catch (Exception e) {
            log.error("Error fetching crypto quote for symbol: {}", symbol, e);
            throw new RuntimeException("Failed to fetch crypto price for symbol: " + symbol, e);
        }
    }

    private String normalizeForFinnhub(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return symbol;
        }

        String normalized = symbol.toUpperCase().trim();
        if (normalized.contains(":")) {
            return normalized;
        }

        if (normalized.endsWith("USDT")) {
            return "BINANCE:" + normalized;
        }

        if (normalized.endsWith("USD")) {
            String base = normalized.substring(0, normalized.length() - 3);
            return "BINANCE:" + base + "USDT";
        }

        // Bare symbols like ETH/BTC are mapped to the common USDT quote pair.
        return "BINANCE:" + normalized + "USDT";
    }

    /**
     * Fetch company profile for a cryptocurrency
     *
     * @param symbol Cryptocurrency symbol
     * @return Company profile data
     */
    public String getCryptoProfile(String symbol) {
        try {
            String url = finnhubConfig.getFinnhubBaseUrl() + "/stock/profile2" +
                    "?symbol=" + symbol +
                    "&token=" + finnhubConfig.getFinnhubApiKey();

            log.info("Fetching crypto profile for symbol: {}", symbol);
            return restTemplate.getForObject(url, String.class);
        } catch (Exception e) {
            log.error("Error fetching crypto profile for symbol: {}", symbol, e);
            throw new RuntimeException("Failed to fetch crypto profile for symbol: " + symbol, e);
        }
    }

    /**
     * Check if API connectivity is available
     *
     * @return true if Finnhub API is reachable, false otherwise
     */
    public boolean isApiAvailable() {
        try {
            String url = finnhubConfig.getFinnhubBaseUrl() + "/quote" +
                    "?symbol=BTCUSD" +
                    "&token=" + finnhubConfig.getFinnhubApiKey();

            restTemplate.getForObject(url, Object.class);
            return true;
        } catch (Exception e) {
            log.warn("Finnhub API is not available: {}", e.getMessage());
            return false;
        }
    }
}

