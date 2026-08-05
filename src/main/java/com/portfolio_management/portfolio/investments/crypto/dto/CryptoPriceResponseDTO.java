package com.portfolio_management.portfolio.investments.crypto.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CryptoPriceResponseDTO {

    @JsonProperty("symbol")
    private String symbol;

    @JsonProperty("description")
    private String description;

    @JsonProperty("displayName")
    private String displayName;

    @JsonProperty("c")
    private BigDecimal currentPrice;

    @JsonProperty("mc")
    private String marketCap;

    @JsonProperty("v")
    private BigDecimal volume24h;

    @JsonProperty("d")
    private BigDecimal change24h;

    @JsonProperty("dp")
    private BigDecimal changePercentage;

    @JsonProperty("t")
    private Long timestamp;
}

