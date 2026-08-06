package com.portfolio_management.portfolio.investments.crypto.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

public class CryptoPriceResponseDTO {

    @JsonProperty("symbol")
    private String symbol;

    @JsonProperty("description")
    private String description;

    @JsonProperty("displayName")
    private String displayName;

    @JsonProperty("c")
    private BigDecimal currentPrice;

    @JsonProperty("mc")
    private String marketCap;

    @JsonProperty("v")
    private BigDecimal volume24h;

    @JsonProperty("d")
    private BigDecimal change24h;

    @JsonProperty("dp")
    private BigDecimal changePercentage;

    @JsonProperty("t")
    private Long timestamp;

    public CryptoPriceResponseDTO() {
    }

    public CryptoPriceResponseDTO(String symbol, String description, String displayName, BigDecimal currentPrice,
                                  String marketCap, BigDecimal volume24h, BigDecimal change24h,
                                  BigDecimal changePercentage, Long timestamp) {
        this.symbol = symbol;
        this.description = description;
        this.displayName = displayName;
        this.currentPrice = currentPrice;
        this.marketCap = marketCap;
        this.volume24h = volume24h;
        this.change24h = change24h;
        this.changePercentage = changePercentage;
        this.timestamp = timestamp;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public BigDecimal getCurrentPrice() {
        return currentPrice;
    }

    public void setCurrentPrice(BigDecimal currentPrice) {
        this.currentPrice = currentPrice;
    }

    public String getMarketCap() {
        return marketCap;
    }

    public void setMarketCap(String marketCap) {
        this.marketCap = marketCap;
    }

    public BigDecimal getVolume24h() {
        return volume24h;
    }

    public void setVolume24h(BigDecimal volume24h) {
        this.volume24h = volume24h;
    }

    public BigDecimal getChange24h() {
        return change24h;
    }

    public void setChange24h(BigDecimal change24h) {
        this.change24h = change24h;
    }

    public BigDecimal getChangePercentage() {
        return changePercentage;
    }

    public void setChangePercentage(BigDecimal changePercentage) {
        this.changePercentage = changePercentage;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }
}

