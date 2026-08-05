package com.portfolio_management.portfolio.investments.crypto.Entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Table("crypto")
public class Crypto {

    @Id
    @Column("crypto_id")
    private Long cryptoId;

    @Column("asset_id")
    private Long assetId;

    @Column("quantity")
    private BigDecimal quantity;

    @Column("buy_price")
    private BigDecimal buyPrice;

    @Column("current_price")
    private BigDecimal currentPrice;

    @Column("invested_amount")
    private BigDecimal investedAmount;

    @Column("current_value")
    private BigDecimal currentValue;

    @Column("profit_loss")
    private BigDecimal profitLoss;
}

