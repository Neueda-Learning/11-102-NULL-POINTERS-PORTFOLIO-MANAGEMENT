package com.portfolio_management.portfolio.investments.crypto.client;

import com.portfolio_management.portfolio.investments.crypto.config.FinnhubConfig;
import com.portfolio_management.portfolio.investments.crypto.dto.CryptoPriceResponseDTO;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinnhubClientUnitTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private FinnhubConfig finnhubConfig;

    private FinnhubClient finnhubClient;

    @BeforeEach
    void setUp() {
        when(finnhubConfig.getFinnhubBaseUrl()).thenReturn("https://finnhub.io/api/v1");
        when(finnhubConfig.getFinnhubApiKey()).thenReturn("test-token");
        finnhubClient = new FinnhubClient(restTemplate, finnhubConfig);
    }

    @Test
    void getCryptoQuote_returnsResponse_whenPriceIsValid() {
        CryptoPriceResponseDTO dto = quote("BTCUSD", "65000.10");
        when(restTemplate.getForObject(contains("symbol=BINANCE:BTCUSDT"), eq(CryptoPriceResponseDTO.class))).thenReturn(dto);

        CryptoPriceResponseDTO result = finnhubClient.getCryptoQuote("BTCUSD");

        assertNotNull(result);
        assertEquals(new BigDecimal("65000.10"), result.getCurrentPrice());
    }

    @Test
    void getCryptoQuote_normalizesUsdSymbol_toBinanceUsdtPair() {
        CryptoPriceResponseDTO dto = quote("TRXUSD", "0.3283");
        when(restTemplate.getForObject(contains("symbol=BINANCE:TRXUSDT"), eq(CryptoPriceResponseDTO.class))).thenReturn(dto);

        finnhubClient.getCryptoQuote("TRXUSD");

        verify(restTemplate, times(1)).getForObject(contains("symbol=BINANCE:TRXUSDT"), eq(CryptoPriceResponseDTO.class));
    }

    @Test
    void getCryptoQuote_keepsUsdtSymbol_withBinancePrefix() {
        CryptoPriceResponseDTO dto = quote("TRXUSDT", "0.3283");
        when(restTemplate.getForObject(contains("symbol=BINANCE:TRXUSDT"), eq(CryptoPriceResponseDTO.class))).thenReturn(dto);

        finnhubClient.getCryptoQuote("TRXUSDT");

        verify(restTemplate, times(1)).getForObject(contains("symbol=BINANCE:TRXUSDT"), eq(CryptoPriceResponseDTO.class));
    }

    @Test
    void getCryptoQuote_keepsPrefixedSymbol_asIs() {
        CryptoPriceResponseDTO dto = quote("BINANCE:ETHUSDT", "3500.00");
        when(restTemplate.getForObject(contains("symbol=BINANCE:ETHUSDT"), eq(CryptoPriceResponseDTO.class))).thenReturn(dto);

        finnhubClient.getCryptoQuote("BINANCE:ETHUSDT");

        verify(restTemplate, times(1)).getForObject(contains("symbol=BINANCE:ETHUSDT"), eq(CryptoPriceResponseDTO.class));
    }

    @Test
    void getCryptoQuote_usesDefaultUsdtMapping_forBareSymbol() {
        CryptoPriceResponseDTO dto = quote("ETH", "3500.00");
        when(restTemplate.getForObject(contains("symbol=BINANCE:ETHUSDT"), eq(CryptoPriceResponseDTO.class))).thenReturn(dto);

        finnhubClient.getCryptoQuote("ETH");

        verify(restTemplate, times(1)).getForObject(contains("symbol=BINANCE:ETHUSDT"), eq(CryptoPriceResponseDTO.class));
    }

    @Test
    void getCryptoQuote_throws_whenResponseIsNull() {
        when(restTemplate.getForObject(contains("symbol=BINANCE:ADAUSDT"), eq(CryptoPriceResponseDTO.class))).thenReturn(null);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> finnhubClient.getCryptoQuote("ADAUSD"));

        assertTrue(ex.getMessage().contains("Failed to fetch crypto price"));
    }

    @Test
    void getCryptoQuote_throws_whenCurrentPriceIsNull() {
        CryptoPriceResponseDTO dto = quote("ADAUSD", null);
        when(restTemplate.getForObject(contains("symbol=BINANCE:ADAUSDT"), eq(CryptoPriceResponseDTO.class))).thenReturn(dto);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> finnhubClient.getCryptoQuote("ADAUSD"));

        assertTrue(ex.getMessage().contains("Failed to fetch crypto price"));
    }

    @Test
    void getCryptoQuote_throws_whenCurrentPriceIsZeroOrNegative() {
        CryptoPriceResponseDTO dto = quote("DOGEUSD", "0");
        when(restTemplate.getForObject(contains("symbol=BINANCE:DOGEUSDT"), eq(CryptoPriceResponseDTO.class))).thenReturn(dto);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> finnhubClient.getCryptoQuote("DOGEUSD"));

        assertTrue(ex.getMessage().contains("Failed to fetch crypto price"));
    }

    @Test
    void getCryptoProfile_returnsStringPayload() {
        when(restTemplate.getForObject(contains("/stock/profile2"), eq(String.class))).thenReturn("{\"name\":\"Bitcoin\"}");

        String result = finnhubClient.getCryptoProfile("BTCUSD");

        assertEquals("{\"name\":\"Bitcoin\"}", result);
        verify(restTemplate, times(1)).getForObject(contains("symbol=BTCUSD"), eq(String.class));
    }

    @Test
    void isApiAvailable_returnsFalse_whenRestCallFails() {
        when(restTemplate.getForObject(contains("/quote"), eq(Object.class))).thenThrow(new RuntimeException("down"));

        boolean result = finnhubClient.isApiAvailable();

        assertFalse(result);
    }

    private CryptoPriceResponseDTO quote(String symbol, String currentPrice) {
        CryptoPriceResponseDTO dto = new CryptoPriceResponseDTO();
        dto.setSymbol(symbol);
        dto.setDisplayName(symbol);
        dto.setCurrentPrice(currentPrice == null ? null : new BigDecimal(currentPrice));
        return dto;
    }
}

