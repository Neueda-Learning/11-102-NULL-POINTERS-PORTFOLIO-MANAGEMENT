package com.portfolio_management.portfolio.investments.crypto.Entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Table("asset")
public class Asset {

    @Id
    @Column("asset_id")
    private Long assetId;

    @Column("portfolio_id")
    private Long portfolioId;

    @Column("symbol")
    private String symbol;

    @Column("asset_name")
    private String name;

    @Column("asset_type")
    private String assetType;

    @Column("currency")
    private String currency;
}

