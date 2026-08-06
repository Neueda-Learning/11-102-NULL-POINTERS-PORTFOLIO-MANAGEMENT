package com.portfolio_management.portfolio.investments.crypto.Entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("asset")
public class Asset {

    @Id
    @Column("asset_id")
    private Long assetId;

    @Column("portfolio_id")
    private Long portfolioId;

    @Column("symbol")
    private String symbol;

    @Column("asset_name")
    private String name;

    @Column("asset_type")
    private String assetType;

    @Column("currency")
    private String currency;

    public Asset() {
    }

    public Asset(Long assetId, Long portfolioId, String symbol, String name, String assetType, String currency) {
        this.assetId = assetId;
        this.portfolioId = portfolioId;
        this.symbol = symbol;
        this.name = name;
        this.assetType = assetType;
        this.currency = currency;
    }

    public Long getAssetId() {
        return assetId;
    }

    public void setAssetId(Long assetId) {
        this.assetId = assetId;
    }

    public Long getPortfolioId() {
        return portfolioId;
    }

    public void setPortfolioId(Long portfolioId) {
        this.portfolioId = portfolioId;
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

    public String getAssetType() {
        return assetType;
    }

    public void setAssetType(String assetType) {
        this.assetType = assetType;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }
}

