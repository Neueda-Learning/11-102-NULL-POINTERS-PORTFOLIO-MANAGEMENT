package com.portfolio_management.portfolio.investments.crypto.Entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Table("portfolio")
public class Portfolio {

    @Id
    @Column("portfolio_id")
    private Long portfolioId;

    @Column("portfolio_name")
    private String portfolioName;

    @Column("description")
    private String description;

    @Column("cash_balance")
    private BigDecimal cashBalance;

    @Column("created_at")
    private LocalDateTime createdAt;

    public Portfolio() {
    }

    public Portfolio(Long portfolioId, String portfolioName, String description, BigDecimal cashBalance, LocalDateTime createdAt) {
        this.portfolioId = portfolioId;
        this.portfolioName = portfolioName;
        this.description = description;
        this.cashBalance = cashBalance;
        this.createdAt = createdAt;
    }

    public Long getPortfolioId() {
        return portfolioId;
    }

    public void setPortfolioId(Long portfolioId) {
        this.portfolioId = portfolioId;
    }

    public String getPortfolioName() {
        return portfolioName;
    }

    public void setPortfolioName(String portfolioName) {
        this.portfolioName = portfolioName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getCashBalance() {
        return cashBalance;
    }

    public void setCashBalance(BigDecimal cashBalance) {
        this.cashBalance = cashBalance;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
