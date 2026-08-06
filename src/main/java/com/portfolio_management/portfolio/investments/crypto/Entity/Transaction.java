package com.portfolio_management.portfolio.investments.crypto.Entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Table("transaction_history")
public class Transaction {

    @Id
    @Column("transaction_id")
    private Long transactionId;

    @Column("portfolio_id")
    private Long portfolioId;

    @Column("asset_id")
    private Long assetId;


    @Column("transaction_type")
    private String transactionType;

    @Column("quantity")
    private BigDecimal quantity;

    @Column("transaction_price")
    private BigDecimal transactionPrice;

    @Column("transaction_date")
    private LocalDateTime transactionDate;

    public Transaction() {
    }

    public Transaction(Long transactionId, Long portfolioId, Long assetId, String transactionType,
                       BigDecimal quantity, BigDecimal transactionPrice, LocalDateTime transactionDate) {
        this.transactionId = transactionId;
        this.portfolioId = portfolioId;
        this.assetId = assetId;
        this.transactionType = transactionType;
        this.quantity = quantity;
        this.transactionPrice = transactionPrice;
        this.transactionDate = transactionDate;
    }

    public Long getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(Long transactionId) {
        this.transactionId = transactionId;
    }

    public Long getPortfolioId() {
        return portfolioId;
    }

    public void setPortfolioId(Long portfolioId) {
        this.portfolioId = portfolioId;
    }

    public Long getAssetId() {
        return assetId;
    }

    public void setAssetId(Long assetId) {
        this.assetId = assetId;
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

    public BigDecimal getTransactionPrice() {
        return transactionPrice;
    }

    public void setTransactionPrice(BigDecimal transactionPrice) {
        this.transactionPrice = transactionPrice;
    }

    public LocalDateTime getTransactionDate() {
        return transactionDate;
    }

    public void setTransactionDate(LocalDateTime transactionDate) {
        this.transactionDate = transactionDate;
    }
}

