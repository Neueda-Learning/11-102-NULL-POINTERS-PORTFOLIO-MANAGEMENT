package com.portfolio_management.portfolio.investments.crypto.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CryptoResponseDTO {

    private Long cryptoId;
    private String symbol;
    private String name;
    private BigDecimal quantity;
    private BigDecimal buyPrice;
    private BigDecimal currentPrice;
    private BigDecimal investedAmount;
    private BigDecimal currentValue;
    private BigDecimal profitLoss;
}

