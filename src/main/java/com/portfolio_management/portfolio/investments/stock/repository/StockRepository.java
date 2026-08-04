package com.portfolio_management.portfolio.investments.stock.repository;

import com.portfolio_management.portfolio.investments.stock.entity.AssetEntity;
import com.portfolio_management.portfolio.investments.stock.entity.StockEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface StockRepository extends JpaRepository<StockEntity, Long> {

    Optional<StockEntity> findByAsset(AssetEntity asset);

    Optional<StockEntity> findByAssetPortfolioPortfolioIdAndAssetSymbol(Long portfolioId, String symbol);

    List<StockEntity> findByAssetPortfolioPortfolioIdAndQuantityGreaterThan(Long portfolioId, BigDecimal quantity);

    @Query("select distinct s.asset.symbol from StockEntity s where s.quantity > 0")
    List<String> findAllActiveSymbols();
}

