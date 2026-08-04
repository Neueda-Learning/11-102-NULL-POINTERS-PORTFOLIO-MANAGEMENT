package com.portfolio_management.portfolio.investments.stock.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "portfolio")
public class PortfolioEntity {

    @Id
    @Column(name = "portfolio_id")
    private Long portfolioId;

    @Column(name = "portfolio_name", nullable = false)
    private String portfolioName;

    @Column(name = "description")
    private String description;

    @Column(name = "cash_balance", nullable = false)
    private BigDecimal cashBalance;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

