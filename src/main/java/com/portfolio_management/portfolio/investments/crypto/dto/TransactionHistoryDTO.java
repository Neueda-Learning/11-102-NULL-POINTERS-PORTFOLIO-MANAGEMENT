package com.portfolio_management.portfolio.investments.crypto.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransactionHistoryDTO {

    private Long transactionId;
    private Long portfolioId;
    private Long assetId;
    private String symbol;
    private String name;
    private String transactionType;
    private BigDecimal quantity;
    private BigDecimal transactionPrice;
    private LocalDateTime transactionDate;
}

