package com.portfolio_management.portfolio.investments.stock.exceptions;

import org.springframework.http.HttpStatus;

public class StockModuleException extends RuntimeException {

    private final HttpStatus status;

    public StockModuleException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}

