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
@Table("transaction_history")
public class Transaction {

    @Id
    @Column("transaction_id")
    private Long transactionId;

    @Column("portfolio_id")
    private Long portfolioId;

    @Column("crypto_id")
    private Long cryptoId;

    @Column("transaction_type")
    private String transactionType;

    @Column("quantity")
    private BigDecimal quantity;

    @Column("transaction_price")
    private BigDecimal transactionPrice;

    @Column("transaction_date")
    private LocalDateTime transactionDate;
}

