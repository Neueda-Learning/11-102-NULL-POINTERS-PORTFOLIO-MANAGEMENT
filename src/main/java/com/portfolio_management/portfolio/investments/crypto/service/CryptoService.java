package com.portfolio_management.portfolio.investments.crypto.service;

import com.portfolio_management.portfolio.investments.crypto.dto.CryptoRequestDTO;
import com.portfolio_management.portfolio.investments.crypto.dto.CryptoResponseDTO;
import com.portfolio_management.portfolio.investments.crypto.Entity.Crypto;

import java.util.List;
import java.util.Optional;

public interface CryptoService {

    /**
     * Get all cryptocurrencies
     */
    List<CryptoResponseDTO> getAllCryptos();

    /**
     * Get a cryptocurrency by ID
     */
    Optional<CryptoResponseDTO> getCryptoById(Long id);

    /**
     * Get a cryptocurrency by symbol
     */
    Optional<CryptoResponseDTO> getCryptoBySymbol(String symbol);

    /**
     * Create or update a cryptocurrency
     */
    CryptoResponseDTO saveCrypto(CryptoRequestDTO cryptoRequestDTO);

    /**
     * Delete a cryptocurrency
     */
    void deleteCrypto(Long id);

    /**
     * Update crypto price from real-time API
     */
    CryptoResponseDTO updateCryptoPrice(String symbol);

    /**
     * Get multiple cryptocurrencies by symbols
     */
    List<CryptoResponseDTO> getCryptosBySymbols(List<String> symbols);
}

