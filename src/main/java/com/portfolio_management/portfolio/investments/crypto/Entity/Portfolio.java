package com.portfolio_management.portfolio.investments.crypto.Entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
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
}
