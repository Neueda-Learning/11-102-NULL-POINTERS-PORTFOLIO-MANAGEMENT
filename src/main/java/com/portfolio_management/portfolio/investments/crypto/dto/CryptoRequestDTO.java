package com.portfolio_management.portfolio.investments.crypto.dto;

import java.math.BigDecimal;

public class CryptoRequestDTO {

    private String symbol;
    private String name;
    private Long portfolioId;
    private String transactionType;
    private BigDecimal quantity;
    private BigDecimal buyPrice;
    private BigDecimal currentPrice;

    public CryptoRequestDTO() {
    }

    public CryptoRequestDTO(String symbol, String name, Long portfolioId, String transactionType,
                            BigDecimal quantity, BigDecimal buyPrice, BigDecimal currentPrice) {
        this.symbol = symbol;
        this.name = name;
        this.portfolioId = portfolioId;
        this.transactionType = transactionType;
        this.quantity = quantity;
        this.buyPrice = buyPrice;
        this.currentPrice = currentPrice;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getPortfolioId() {
        return portfolioId;
    }

    public void setPortfolioId(Long portfolioId) {
        this.portfolioId = portfolioId;
    }

    public String getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(String transactionType) {
        this.transactionType = transactionType;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getBuyPrice() {
        return buyPrice;
    }

    public void setBuyPrice(BigDecimal buyPrice) {
        this.buyPrice = buyPrice;
    }

    public BigDecimal getCurrentPrice() {
        return currentPrice;
    }

    public void setCurrentPrice(BigDecimal currentPrice) {
        this.currentPrice = currentPrice;
    }
}

