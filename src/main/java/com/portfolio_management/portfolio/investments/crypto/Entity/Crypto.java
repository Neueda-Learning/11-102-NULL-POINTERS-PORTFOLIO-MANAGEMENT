package com.portfolio_management.portfolio.investments.crypto.Entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;

@Table("crypto")
public class Crypto {

    @Id
    @Column("crypto_id")
    private Long cryptoId;

    @Column("asset_id")
    private Long assetId;

    @Column("quantity")
    private BigDecimal quantity;

    @Column("buy_price")
    private BigDecimal buyPrice;

    @Column("current_price")
    private BigDecimal currentPrice;

    @Column("invested_amount")
    private BigDecimal investedAmount;

    @Column("current_value")
    private BigDecimal currentValue;

    @Column("profit_loss")
    private BigDecimal profitLoss;

    public Crypto() {
    }

    public Crypto(Long cryptoId, Long assetId, BigDecimal quantity, BigDecimal buyPrice, BigDecimal currentPrice,
                  BigDecimal investedAmount, BigDecimal currentValue, BigDecimal profitLoss) {
        this.cryptoId = cryptoId;
        this.assetId = assetId;
        this.quantity = quantity;
        this.buyPrice = buyPrice;
        this.currentPrice = currentPrice;
        this.investedAmount = investedAmount;
        this.currentValue = currentValue;
        this.profitLoss = profitLoss;
    }

    public Long getCryptoId() {
        return cryptoId;
    }

    public void setCryptoId(Long cryptoId) {
        this.cryptoId = cryptoId;
    }

    public Long getAssetId() {
        return assetId;
    }

    public void setAssetId(Long assetId) {
        this.assetId = assetId;
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

    public BigDecimal getInvestedAmount() {
        return investedAmount;
    }

    public void setInvestedAmount(BigDecimal investedAmount) {
        this.investedAmount = investedAmount;
    }

    public BigDecimal getCurrentValue() {
        return currentValue;
    }

    public void setCurrentValue(BigDecimal currentValue) {
        this.currentValue = currentValue;
    }

    public BigDecimal getProfitLoss() {
        return profitLoss;
    }

    public void setProfitLoss(BigDecimal profitLoss) {
        this.profitLoss = profitLoss;
    }
}

