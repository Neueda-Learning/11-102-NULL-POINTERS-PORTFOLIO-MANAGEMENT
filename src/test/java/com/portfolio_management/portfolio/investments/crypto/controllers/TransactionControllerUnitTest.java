package com.portfolio_management.portfolio.investments.crypto.controllers;

import com.portfolio_management.portfolio.investments.crypto.Entity.Asset;
import com.portfolio_management.portfolio.investments.crypto.Entity.Crypto;
import com.portfolio_management.portfolio.investments.crypto.Entity.Transaction;
import com.portfolio_management.portfolio.investments.crypto.dto.TransactionHistoryDTO;
import com.portfolio_management.portfolio.investments.crypto.repository.AssetRepository;
import com.portfolio_management.portfolio.investments.crypto.repository.CryptoRepository;
import com.portfolio_management.portfolio.investments.crypto.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionControllerUnitTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AssetRepository assetRepository;

    @Mock
    private CryptoRepository cryptoRepository;

    private TransactionController transactionController;

    @BeforeEach
    void setUp() {
        transactionController = new TransactionController(transactionRepository, assetRepository, cryptoRepository);
    }

    @Test
    void getTransactionHistory_usesPortfolioAndAssetFilter_whenBothProvided() {
        Transaction tx = sampleTx(1L, 10L, 100L, "BUY", "2026-08-06T10:00:00");
        when(transactionRepository.findByPortfolioIdAndAssetId(10L, 100L)).thenReturn(List.of(tx));
        when(assetRepository.findById(100L)).thenReturn(Optional.of(new Asset(100L, 10L, "BTCUSD", "Bitcoin", "CRYPTO", "USD")));
        when(cryptoRepository.findByAssetId(100L)).thenReturn(Optional.empty());

        ResponseEntity<List<TransactionHistoryDTO>> response = transactionController.getTransactionHistory(10L, 100L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
        verify(transactionRepository, times(1)).findByPortfolioIdAndAssetId(10L, 100L);
    }

    @Test
    void getTransactionHistory_usesPortfolioFilter_whenOnlyPortfolioProvided() {
        Transaction tx = sampleTx(2L, 20L, 200L, "SELL", "2026-08-06T11:00:00");
        when(transactionRepository.findByPortfolioId(20L)).thenReturn(List.of(tx));
        when(assetRepository.findById(200L)).thenReturn(Optional.of(new Asset(200L, 20L, "ETHUSD", "Ethereum", "CRYPTO", "USD")));
        when(cryptoRepository.findByAssetId(200L)).thenReturn(Optional.empty());

        ResponseEntity<List<TransactionHistoryDTO>> response = transactionController.getTransactionHistory(20L, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(transactionRepository, times(1)).findByPortfolioId(20L);
        verify(transactionRepository, never()).findByAssetId(200L);
    }

    @Test
    void getTransactionHistory_usesAssetFilter_whenOnlyAssetProvided() {
        Transaction tx = sampleTx(3L, 30L, 300L, "BUY", "2026-08-06T12:00:00");
        when(transactionRepository.findByAssetId(300L)).thenReturn(List.of(tx));
        when(assetRepository.findById(300L)).thenReturn(Optional.of(new Asset(300L, 30L, "SOLUSD", "Solana", "CRYPTO", "USD")));
        when(cryptoRepository.findByAssetId(300L)).thenReturn(Optional.empty());

        ResponseEntity<List<TransactionHistoryDTO>> response = transactionController.getTransactionHistory(null, 300L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(transactionRepository, times(1)).findByAssetId(300L);
    }

    @Test
    void getTransactionHistory_noFilters_sortsByDateDescending() {
        Transaction older = sampleTx(4L, 1L, 400L, "BUY", "2026-08-06T08:00:00");
        Transaction newer = sampleTx(5L, 1L, 500L, "SELL", "2026-08-06T09:00:00");
        when(transactionRepository.findAll()).thenReturn(List.of(older, newer));
        when(assetRepository.findById(400L)).thenReturn(Optional.of(new Asset(400L, 1L, "ADAUSD", "Cardano", "CRYPTO", "USD")));
        when(assetRepository.findById(500L)).thenReturn(Optional.of(new Asset(500L, 1L, "DOGEUSD", "Dogecoin", "CRYPTO", "USD")));
        when(cryptoRepository.findByAssetId(400L)).thenReturn(Optional.empty());
        when(cryptoRepository.findByAssetId(500L)).thenReturn(Optional.empty());

        List<TransactionHistoryDTO> body = transactionController.getTransactionHistory(null, null).getBody();

        assertEquals(2, body.size());
        assertEquals(5L, body.get(0).getTransactionId());
        assertEquals(4L, body.get(1).getTransactionId());
    }

    @Test
    void getTransactionHistory_mapsUnknownAsset_whenAssetIdIsNull() {
        Transaction tx = sampleTx(6L, 1L, null, "BUY", "2026-08-06T13:00:00");
        when(transactionRepository.findByPortfolioId(1L)).thenReturn(List.of(tx));

        TransactionHistoryDTO dto = transactionController.getTransactionHistory(1L, null).getBody().get(0);

        assertEquals("UNKNOWN", dto.getSymbol());
        assertEquals("UNKNOWN", dto.getName());
    }

    @Test
    void getTransactionHistory_mapsUnknownAsset_whenAssetNotFound() {
        Transaction tx = sampleTx(7L, 1L, 700L, "BUY", "2026-08-06T14:00:00");
        when(transactionRepository.findByAssetId(700L)).thenReturn(List.of(tx));
        when(assetRepository.findById(700L)).thenReturn(Optional.empty());
        when(cryptoRepository.findByAssetId(700L)).thenReturn(Optional.empty());

        TransactionHistoryDTO dto = transactionController.getTransactionHistory(null, 700L).getBody().get(0);

        assertEquals("UNKNOWN", dto.getSymbol());
        assertEquals("UNKNOWN", dto.getName());
    }

    @Test
    void getTransactionHistory_mapsSymbolAndName_whenAssetFound() {
        Transaction tx = sampleTx(8L, 2L, 800L, "SELL", "2026-08-06T15:00:00");
        when(transactionRepository.findByAssetId(800L)).thenReturn(List.of(tx));
        when(assetRepository.findById(800L)).thenReturn(Optional.of(new Asset(800L, 2L, "TRXUSD", "TRON", "CRYPTO", "USD")));
        when(cryptoRepository.findByAssetId(800L)).thenReturn(Optional.empty());

        TransactionHistoryDTO dto = transactionController.getTransactionHistory(null, 800L).getBody().get(0);

        assertEquals("TRXUSD", dto.getSymbol());
        assertEquals("TRON", dto.getName());
    }

    @Test
    void getTransactionHistory_keepsAssetValues_whenCryptoRowExists() {
        Transaction tx = sampleTx(9L, 2L, 900L, "BUY", "2026-08-06T16:00:00");
        when(transactionRepository.findByAssetId(900L)).thenReturn(List.of(tx));
        when(assetRepository.findById(900L)).thenReturn(Optional.of(new Asset(900L, 2L, "MATICUSD", "Polygon", "CRYPTO", "USD")));
        when(cryptoRepository.findByAssetId(900L)).thenReturn(Optional.of(new Crypto(1L, 900L, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO)));

        TransactionHistoryDTO dto = transactionController.getTransactionHistory(null, 900L).getBody().get(0);

        assertEquals("MATICUSD", dto.getSymbol());
        assertEquals("Polygon", dto.getName());
    }

    @Test
    void getTransactionHistory_mapsCoreTransactionFields() {
        Transaction tx = sampleTx(10L, 99L, 1000L, "SELL", "2026-08-06T17:00:00");
        tx.setQuantity(new BigDecimal("3.5000"));
        tx.setTransactionPrice(new BigDecimal("123.45"));
        when(transactionRepository.findByPortfolioIdAndAssetId(99L, 1000L)).thenReturn(List.of(tx));
        when(assetRepository.findById(1000L)).thenReturn(Optional.of(new Asset(1000L, 99L, "AVAXUSD", "Avalanche", "CRYPTO", "USD")));
        when(cryptoRepository.findByAssetId(1000L)).thenReturn(Optional.empty());

        TransactionHistoryDTO dto = transactionController.getTransactionHistory(99L, 1000L).getBody().get(0);

        assertEquals(99L, dto.getPortfolioId());
        assertEquals(1000L, dto.getAssetId());
        assertEquals("SELL", dto.getTransactionType());
        assertEquals(new BigDecimal("3.5000"), dto.getQuantity());
        assertEquals(new BigDecimal("123.45"), dto.getTransactionPrice());
    }

    private Transaction sampleTx(Long id, Long portfolioId, Long assetId, String type, String dateTime) {
        return new Transaction(
                id,
                portfolioId,
                assetId,
                type,
                new BigDecimal("1.0000"),
                new BigDecimal("100.00"),
                LocalDateTime.parse(dateTime)
        );
    }
}

