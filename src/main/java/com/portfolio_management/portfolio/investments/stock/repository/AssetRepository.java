package com.portfolio_management.portfolio.investments.stock.repository;

import com.portfolio_management.portfolio.investments.stock.entity.AssetEntity;
import com.portfolio_management.portfolio.investments.stock.entity.AssetType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AssetRepository extends JpaRepository<AssetEntity, Long> {

    Optional<AssetEntity> findByPortfolioPortfolioIdAndAssetTypeAndSymbol(Long portfolioId, AssetType assetType, String symbol);
}

