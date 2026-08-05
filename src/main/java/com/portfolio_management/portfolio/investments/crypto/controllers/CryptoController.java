package com.portfolio_management.portfolio.investments.crypto.controllers;

import com.portfolio_management.portfolio.investments.crypto.dto.CryptoRequestDTO;
import com.portfolio_management.portfolio.investments.crypto.dto.CryptoResponseDTO;
import com.portfolio_management.portfolio.investments.crypto.service.CryptoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/v1/crypto")
@CrossOrigin(origins = "*", maxAge = 3600)
public class CryptoController {

    private final CryptoService cryptoService;

    public CryptoController(CryptoService cryptoService) {
        this.cryptoService = cryptoService;
    }

    /**
     * Get all cryptocurrencies
     * GET /api/v1/crypto
     */
    @GetMapping
    public ResponseEntity<List<CryptoResponseDTO>> getAllCryptos() {
        log.info("Retrieving all cryptocurrencies");
        List<CryptoResponseDTO> cryptos = cryptoService.getAllCryptos();
        return ResponseEntity.ok(cryptos);
    }

    /**
     * Get a cryptocurrency by symbol
     * GET /api/v1/crypto/symbol/{symbol}
     */
    @GetMapping("/symbol/{symbol}")
    public ResponseEntity<CryptoResponseDTO> getCryptoBySymbol(@PathVariable String symbol) {
        log.info("Retrieving cryptocurrency with symbol: {}", symbol);
        Optional<CryptoResponseDTO> crypto = cryptoService.getCryptoBySymbol(symbol.toUpperCase());
        return crypto.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Get a cryptocurrency by ID
     * GET /api/v1/crypto/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<CryptoResponseDTO> getCryptoById(@PathVariable Long id) {
        log.info("Retrieving cryptocurrency with ID: {}", id);
        Optional<CryptoResponseDTO> crypto = cryptoService.getCryptoById(id);
        return crypto.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Create or update a cryptocurrency
     * POST /api/v1/crypto
     */
    @PostMapping
    public ResponseEntity<CryptoResponseDTO> createCrypto(@RequestBody CryptoRequestDTO cryptoRequestDTO) {
        log.info("Creating/Updating cryptocurrency: {}", cryptoRequestDTO.getSymbol());
        CryptoResponseDTO crypto = cryptoService.saveCrypto(cryptoRequestDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(crypto);
    }

    /**
     * Update cryptocurrency price from Finnhub API
     * PUT /api/v1/crypto/{symbol}/price
     */
    @PutMapping("/{symbol}/price")
    public ResponseEntity<CryptoResponseDTO> updateCryptoPrice(@PathVariable String symbol) {
        log.info("Updating price for cryptocurrency: {}", symbol);
        CryptoResponseDTO crypto = cryptoService.updateCryptoPrice(symbol.toUpperCase());
        return ResponseEntity.ok(crypto);
    }

    /**
     * Delete a cryptocurrency
     * DELETE /api/v1/crypto/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCrypto(@PathVariable Long id) {
        log.info("Deleting cryptocurrency with ID: {}", id);
        cryptoService.deleteCrypto(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Lookup a crypto symbol (price + name) without persisting to DB.
     * GET /api/v1/crypto/lookup/{symbol}
     */
    @GetMapping("/lookup/{symbol}")
    public ResponseEntity<Map<String, Object>> lookupSymbol(@PathVariable String symbol) {
        log.info("Looking up crypto symbol: {}", symbol);
        Map<String, Object> result = cryptoService.lookupSymbol(symbol.toUpperCase());
        return ResponseEntity.ok(result);
    }

    /**
     * Get multiple cryptocurrencies by symbols
     * POST /api/v1/crypto/batch
     */
    @PostMapping("/batch")
    public ResponseEntity<List<CryptoResponseDTO>> getCryptosBySymbols(@RequestBody List<String> symbols) {
        log.info("Retrieving {} cryptocurrencies", symbols.size());
        List<CryptoResponseDTO> cryptos = cryptoService.getCryptosBySymbols(symbols);
        return ResponseEntity.ok(cryptos);
    }
}
