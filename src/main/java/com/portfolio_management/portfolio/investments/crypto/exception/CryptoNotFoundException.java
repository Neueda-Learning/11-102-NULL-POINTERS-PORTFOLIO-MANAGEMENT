package com.portfolio_management.portfolio.investments.crypto.exception;

public class CryptoNotFoundException extends RuntimeException {

    public CryptoNotFoundException(String message) {
        super(message);
    }

    public CryptoNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    public CryptoNotFoundException(String symbol, Long id) {
        super(String.format("Crypto not found with symbol: %s or id: %d", symbol, id));
    }
}

