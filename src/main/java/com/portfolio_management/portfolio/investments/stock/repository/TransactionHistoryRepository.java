package com.portfolio_management.portfolio.investments.stock.repository;

import com.portfolio_management.portfolio.investments.stock.entity.TransactionHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransactionHistoryRepository extends JpaRepository<TransactionHistoryEntity, Long> {

    List<TransactionHistoryEntity> findByPortfolioPortfolioIdAndAssetSymbolOrderByTransactionDateDesc(Long portfolioId, String symbol);
}

