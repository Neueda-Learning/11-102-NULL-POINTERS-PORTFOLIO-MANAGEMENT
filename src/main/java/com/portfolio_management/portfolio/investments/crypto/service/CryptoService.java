package com.portfolio_management.portfolio.investments.crypto.service;

import com.portfolio_management.portfolio.investments.crypto.dto.CryptoRequestDTO;
import com.portfolio_management.portfolio.investments.crypto.dto.CryptoResponseDTO;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface CryptoService {

    List<CryptoResponseDTO> getAllCryptos();
    Optional<CryptoResponseDTO> getCryptoById(Long id);
    Optional<CryptoResponseDTO> getCryptoBySymbol(String symbol);
    CryptoResponseDTO saveCrypto(CryptoRequestDTO cryptoRequestDTO);
    void deleteCrypto(Long id);
    CryptoResponseDTO updateCryptoPrice(String symbol);
    List<CryptoResponseDTO> getCryptosBySymbols(List<String> symbols);

    /**
     * Lookup a crypto symbol: fetch live price from Finnhub and resolve name.
     * Does NOT persist anything to the database.
     */
    Map<String, Object> lookupSymbol(String symbol);
}

