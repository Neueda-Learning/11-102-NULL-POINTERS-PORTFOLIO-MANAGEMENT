package com.portfolio_management.portfolio.investments.bond.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BondResponseDTO(
        Long id,
        String issuer,
        BigDecimal interestRate,
        BigDecimal amountInvested,
        LocalDate startDate,
        Integer tenureMonths,
        LocalDate maturityDate,
        BigDecimal totalInvestment,
        BigDecimal annualIncome,
        BigDecimal currentYield,
        BigDecimal approxYTM,
        BigDecimal maturityAmount
) {
}

