package com.portfolio_management.portfolio.investments.asset.repository;

import com.portfolio_management.portfolio.investments.asset.model.Asset;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetRepository extends JpaRepository<Asset, Long> {

	boolean existsBySymbol(String symbol);
}

