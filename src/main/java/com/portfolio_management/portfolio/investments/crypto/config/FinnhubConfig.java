package com.portfolio_management.portfolio.investments.crypto.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import org.springframework.context.annotation.Bean;

@Configuration
public class FinnhubConfig {

    @Value("${finnhub.base-url:https://finnhub.io/api/v1}")
    private String finnhubBaseUrl;

    @Value("${FINNHUB_API_KEY:demo}")
    private String finnhubApiKey;

    public String getFinnhubBaseUrl() {
        return finnhubBaseUrl;
    }

    public String getFinnhubApiKey() {
        return finnhubApiKey;
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
