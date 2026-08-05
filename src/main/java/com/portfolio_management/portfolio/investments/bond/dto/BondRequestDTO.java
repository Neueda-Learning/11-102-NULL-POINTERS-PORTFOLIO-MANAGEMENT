package com.portfolio_management.portfolio.investments.bond.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record BondRequestDTO(
        @NotBlank String issuer,
        @NotNull @DecimalMin("0.0001") BigDecimal interestRate,
        @NotNull @DecimalMin("0.01") BigDecimal amountInvested,
        @NotNull LocalDate startDate,
        @NotNull @Min(1) Integer tenureMonths
) {
}

