package com.portfolio_management.portfolio.investments.crypto.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CryptoRequestDTO {

    private String symbol;
    private String name;
    private Long portfolioId;
    private String transactionType;
    private BigDecimal quantity;
    private BigDecimal buyPrice;
    private BigDecimal currentPrice;
}

