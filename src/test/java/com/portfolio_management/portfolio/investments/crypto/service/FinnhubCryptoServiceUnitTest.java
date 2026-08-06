package com.portfolio_management.portfolio.investments.crypto.service;

import com.portfolio_management.portfolio.investments.crypto.Entity.Asset;
import com.portfolio_management.portfolio.investments.crypto.Entity.Crypto;
import com.portfolio_management.portfolio.investments.crypto.Entity.Portfolio;
import com.portfolio_management.portfolio.investments.crypto.Entity.Transaction;
import com.portfolio_management.portfolio.investments.crypto.client.FinnhubClient;
import com.portfolio_management.portfolio.investments.crypto.dto.CryptoPriceResponseDTO;
import com.portfolio_management.portfolio.investments.crypto.dto.CryptoRequestDTO;
import com.portfolio_management.portfolio.investments.crypto.dto.CryptoResponseDTO;
import com.portfolio_management.portfolio.investments.crypto.exception.CryptoNotFoundException;
import com.portfolio_management.portfolio.investments.crypto.repository.AssetRepository;
import com.portfolio_management.portfolio.investments.crypto.repository.CryptoRepository;
import com.portfolio_management.portfolio.investments.crypto.repository.PortfolioRepository;
import com.portfolio_management.portfolio.investments.crypto.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinnhubCryptoServiceUnitTest {

    @Mock
    private CryptoRepository cryptoRepository;
    @Mock
    private AssetRepository assetRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private PortfolioRepository portfolioRepository;
    @Mock
    private FinnhubClient finnhubClient;

    private FinnhubCryptoService service;

    @BeforeEach
    void setUp() {
        service = new FinnhubCryptoService(cryptoRepository, assetRepository, transactionRepository, portfolioRepository, finnhubClient);
    }

    @Test
    void getAllCryptos_returnsMappedDtos() {
        Crypto c = crypto(1L, 10L, "2.0000", "100.00", "120.00");
        when(cryptoRepository.findAll()).thenReturn(List.of(c));
        when(assetRepository.findById(10L)).thenReturn(Optional.of(asset(10L, 1L, "BTCUSD", "Bitcoin")));
        when(finnhubClient.getCryptoQuote("BTCUSD")).thenReturn(price("BTCUSD", "130.00"));
        when(cryptoRepository.save(any(Crypto.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<CryptoResponseDTO> result = service.getAllCryptos();

        assertEquals(1, result.size());
        assertEquals("BTCUSD", result.get(0).getSymbol());
    }

    @Test
    void getCryptoBySymbol_returnsEmpty_whenAssetNotFound() {
        when(assetRepository.findBySymbol("XRPUSD")).thenReturn(Optional.empty());

        Optional<CryptoResponseDTO> result = service.getCryptoBySymbol("XRPUSD");

        assertEquals(Optional.empty(), result);
    }

    @Test
    void saveCrypto_buyCreatesAssetAndTransaction() {
        CryptoRequestDTO request = req("TRXUSD", "TRON", 1L, "BUY", "2.0000", "0.30", "0.31");
        when(portfolioRepository.existsById(1L)).thenReturn(true);
        when(assetRepository.findBySymbol("TRXUSD")).thenReturn(Optional.empty());
        when(assetRepository.save(any(Asset.class))).thenReturn(asset(100L, 1L, "TRXUSD", "TRON"));
        when(assetRepository.findById(100L)).thenReturn(Optional.of(asset(100L, 1L, "TRXUSD", "TRON")));
        when(cryptoRepository.findByAssetId(100L)).thenReturn(Optional.empty());
        when(finnhubClient.getCryptoQuote("TRXUSD")).thenReturn(price("TRXUSD", "0.32"));
        when(cryptoRepository.save(any(Crypto.class))).thenAnswer(invocation -> {
            Crypto c = invocation.getArgument(0);
            if (c.getCryptoId() == null) c.setCryptoId(55L);
            return c;
        });
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CryptoResponseDTO result = service.saveCrypto(request);

        assertEquals("TRXUSD", result.getSymbol());
        verify(transactionRepository, times(1)).save(any(Transaction.class));
    }

    @Test
    void saveCrypto_sellReducesQuantity() {
        CryptoRequestDTO request = req("ADAUSD", "Cardano", 1L, "SELL", "1.0000", "0.40", "0.45");
        when(portfolioRepository.existsById(1L)).thenReturn(true);
        Asset asset = asset(200L, 1L, "ADAUSD", "Cardano");
        when(assetRepository.findBySymbol("ADAUSD")).thenReturn(Optional.of(asset));
        when(assetRepository.findById(200L)).thenReturn(Optional.of(asset));
        Crypto existing = crypto(77L, 200L, "3.0000", "0.40", "0.45");
        when(cryptoRepository.findByAssetId(200L)).thenReturn(Optional.of(existing));
        when(finnhubClient.getCryptoQuote("ADAUSD")).thenReturn(price("ADAUSD", "0.45"));
        when(cryptoRepository.save(any(Crypto.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CryptoResponseDTO result = service.saveCrypto(request);

        assertEquals(new BigDecimal("2.0000"), result.getQuantity());
    }

    @Test
    void saveCrypto_throwsIllegalArgument_whenQuantityInvalid() {
        CryptoRequestDTO request = req("BTCUSD", "Bitcoin", 1L, "BUY", "0", "100.00", "110.00");

        assertThrows(IllegalArgumentException.class, () -> service.saveCrypto(request));
        verify(cryptoRepository, never()).save(any(Crypto.class));
    }

    @Test
    void updateCryptoPrice_createsMissingRecords_andReturnsDto() {
        when(portfolioRepository.findByPortfolioName("Default Portfolio")).thenReturn(Optional.of(portfolio(1L, "Default Portfolio")));
        when(assetRepository.findBySymbol("DOGEUSD")).thenReturn(Optional.empty());
        when(assetRepository.save(any(Asset.class))).thenReturn(asset(300L, 1L, "DOGEUSD", "DOGEUSD"));
        when(cryptoRepository.findByAssetId(300L)).thenReturn(Optional.empty());
        when(finnhubClient.getCryptoQuote("DOGEUSD")).thenReturn(price("DOGEUSD", "0.20"));
        when(cryptoRepository.save(any(Crypto.class))).thenAnswer(invocation -> {
            Crypto c = invocation.getArgument(0);
            if (c.getCryptoId() == null) c.setCryptoId(999L);
            return c;
        });
        when(assetRepository.findById(300L)).thenReturn(Optional.of(asset(300L, 1L, "DOGEUSD", "DOGEUSD")));

        CryptoResponseDTO dto = service.updateCryptoPrice("DOGEUSD");

        assertEquals("DOGEUSD", dto.getSymbol());
        assertNotNull(dto.getCurrentPrice());
    }

    @Test
    void deleteCrypto_throwsWhenNotFound() {
        when(cryptoRepository.existsById(404L)).thenReturn(false);

        assertThrows(CryptoNotFoundException.class, () -> service.deleteCrypto(404L));
    }

    @Test
    void deleteCrypto_deletesWhenFound() {
        when(cryptoRepository.existsById(7L)).thenReturn(true);

        service.deleteCrypto(7L);

        verify(cryptoRepository, times(1)).deleteById(7L);
    }

    @Test
    void lookupSymbol_returnsNameAndZeroPrice_whenFetchFails() {
        when(assetRepository.findBySymbol("TONUSD")).thenReturn(Optional.empty());
        when(finnhubClient.getCryptoQuote("TONUSD")).thenThrow(new RuntimeException("rate limited"));

        Map<String, Object> result = service.lookupSymbol("TONUSD");

        assertEquals("TONUSD", result.get("symbol"));
        assertEquals("Toncoin", result.get("name"));
        assertEquals(BigDecimal.ZERO, result.get("currentPrice"));
    }

    @Test
    void getCryptosBySymbols_returnsMappedRows_forExistingSymbols() {
        Asset btc = asset(10L, 1L, "BTCUSD", "Bitcoin");
        when(assetRepository.findBySymbol("BTCUSD")).thenReturn(Optional.of(btc));
        when(finnhubClient.getCryptoQuote("BTCUSD")).thenReturn(price("BTCUSD", "62000.00"));

        List<CryptoResponseDTO> result = service.getCryptosBySymbols(List.of("BTCUSD"));

        assertEquals(1, result.size());
        assertEquals("BTCUSD", result.get(0).getSymbol());
    }

    private CryptoRequestDTO req(String symbol, String name, Long portfolioId, String type, String qty, String buy, String current) {
        return new CryptoRequestDTO(symbol, name, portfolioId, type, new BigDecimal(qty), new BigDecimal(buy), new BigDecimal(current));
    }

    private Asset asset(Long id, Long portfolioId, String symbol, String name) {
        return new Asset(id, portfolioId, symbol, name, "CRYPTO", "USD");
    }

    private Crypto crypto(Long cryptoId, Long assetId, String qty, String buy, String current) {
        Crypto c = new Crypto();
        c.setCryptoId(cryptoId);
        c.setAssetId(assetId);
        c.setQuantity(new BigDecimal(qty));
        c.setBuyPrice(new BigDecimal(buy));
        c.setCurrentPrice(new BigDecimal(current));
        c.setInvestedAmount(BigDecimal.ZERO);
        c.setCurrentValue(BigDecimal.ZERO);
        c.setProfitLoss(BigDecimal.ZERO);
        return c;
    }

    private Portfolio portfolio(Long id, String name) {
        return new Portfolio(id, name, "Default", BigDecimal.ZERO, LocalDateTime.of(2026, 8, 6, 0, 0));
    }

    private CryptoPriceResponseDTO price(String symbol, String current) {
        CryptoPriceResponseDTO dto = new CryptoPriceResponseDTO();
        dto.setSymbol(symbol);
        dto.setDisplayName(symbol);
        dto.setCurrentPrice(new BigDecimal(current));
        return dto;
    }
}

