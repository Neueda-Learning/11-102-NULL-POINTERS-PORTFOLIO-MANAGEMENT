package com.portfolio_management.portfolio.investments.crypto.controllers;

import com.portfolio_management.portfolio.investments.crypto.dto.CryptoRequestDTO;
import com.portfolio_management.portfolio.investments.crypto.dto.CryptoResponseDTO;
import com.portfolio_management.portfolio.investments.crypto.service.CryptoService;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CryptoControllerUnitTest {

    @Mock
    private CryptoService cryptoService;

    private CryptoController cryptoController;

    @BeforeEach
    void setUp() {
        cryptoController = new CryptoController(cryptoService);
    }

    @Test
    void getAllCryptos_returnsOkWithServiceList() {
        List<CryptoResponseDTO> list = List.of(sampleCrypto("BTCUSD", "Bitcoin"));
        when(cryptoService.getAllCryptos()).thenReturn(list);

        ResponseEntity<List<CryptoResponseDTO>> response = cryptoController.getAllCryptos();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(list, response.getBody());
    }

    @Test
    void getCryptoBySymbol_returnsOk_whenSymbolExists() {
        CryptoResponseDTO dto = sampleCrypto("ETHUSD", "Ethereum");
        when(cryptoService.getCryptoBySymbol("ETHUSD")).thenReturn(Optional.of(dto));

        ResponseEntity<CryptoResponseDTO> response = cryptoController.getCryptoBySymbol("ethusd");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(dto, response.getBody());
    }

    @Test
    void getCryptoBySymbol_returnsNotFound_whenSymbolMissing() {
        when(cryptoService.getCryptoBySymbol("XLMUSD")).thenReturn(Optional.empty());

        ResponseEntity<CryptoResponseDTO> response = cryptoController.getCryptoBySymbol("xlmusd");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void getCryptoBySymbol_uppercasesInput_beforeServiceCall() {
        when(cryptoService.getCryptoBySymbol("DOGEUSD")).thenReturn(Optional.of(sampleCrypto("DOGEUSD", "Dogecoin")));

        cryptoController.getCryptoBySymbol("dogeusd");

        verify(cryptoService, times(1)).getCryptoBySymbol("DOGEUSD");
    }

    @Test
    void getCryptoById_returnsOk_whenIdExists() {
        CryptoResponseDTO dto = sampleCrypto("ADAUSD", "Cardano");
        when(cryptoService.getCryptoById(7L)).thenReturn(Optional.of(dto));

        ResponseEntity<CryptoResponseDTO> response = cryptoController.getCryptoById(7L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(dto, response.getBody());
    }

    @Test
    void getCryptoById_returnsNotFound_whenIdMissing() {
        when(cryptoService.getCryptoById(404L)).thenReturn(Optional.empty());

        ResponseEntity<CryptoResponseDTO> response = cryptoController.getCryptoById(404L);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void createCrypto_returnsCreatedAndBody_fromService() {
        CryptoRequestDTO request = new CryptoRequestDTO("TRXUSD", "TRON", 1L, "BUY", new BigDecimal("10"), new BigDecimal("0.30"), new BigDecimal("0.31"));
        CryptoResponseDTO dto = sampleCrypto("TRXUSD", "TRON");
        when(cryptoService.saveCrypto(request)).thenReturn(dto);

        ResponseEntity<CryptoResponseDTO> response = cryptoController.createCrypto(request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertSame(dto, response.getBody());
    }

    @Test
    void updateCryptoPrice_uppercasesSymbol_andReturnsOk() {
        CryptoResponseDTO dto = sampleCrypto("MATICUSD", "Polygon");
        when(cryptoService.updateCryptoPrice("MATICUSD")).thenReturn(dto);

        ResponseEntity<CryptoResponseDTO> response = cryptoController.updateCryptoPrice("maticusd");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(dto, response.getBody());
        verify(cryptoService, times(1)).updateCryptoPrice("MATICUSD");
    }

    @Test
    void deleteCrypto_callsService_andReturnsNoContent() {
        ResponseEntity<Void> response = cryptoController.deleteCrypto(33L);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(cryptoService, times(1)).deleteCrypto(33L);
    }

    @Test
    void lookupSymbol_uppercasesInput_andReturnsOkWithMap() {
        Map<String, Object> lookup = new LinkedHashMap<>();
        lookup.put("symbol", "BTCUSD");
        lookup.put("name", "Bitcoin");
        lookup.put("currentPrice", new BigDecimal("64000.00"));
        when(cryptoService.lookupSymbol("BTCUSD")).thenReturn(lookup);

        ResponseEntity<Map<String, Object>> response = cryptoController.lookupSymbol("btcusd");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(lookup, response.getBody());
        verify(cryptoService, times(1)).lookupSymbol("BTCUSD");
    }

    private CryptoResponseDTO sampleCrypto(String symbol, String name) {
        return new CryptoResponseDTO(
                1L,
                symbol,
                name,
                new BigDecimal("2.50000000"),
                new BigDecimal("100.00"),
                new BigDecimal("110.00"),
                new BigDecimal("250.00"),
                new BigDecimal("275.00"),
                new BigDecimal("25.00")
        );
    }
}

