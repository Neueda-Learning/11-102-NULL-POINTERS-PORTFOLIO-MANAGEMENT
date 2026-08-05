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
@Table("crypto_holdings")
public class CryptoHolding {

    @Id
    @Column("holding_id")
    private Long holdingId;

    @Column("portfolio_id")
    private Long portfolioId;

    @Column("crypto_id")
    private Long cryptoId;

    @Column("quantity")
    private BigDecimal quantity;

    @Column("purchase_price")
    private BigDecimal purchasePrice;

    @Column("purchase_date")
    private LocalDateTime purchaseDate;
}

