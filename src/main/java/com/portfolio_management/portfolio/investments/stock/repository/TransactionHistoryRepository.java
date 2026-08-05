package com.portfolio_management.portfolio.investments.stock.repository;

import com.portfolio_management.portfolio.investments.stock.entity.TransactionHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository("stockTransactionHistoryRepository")
public interface TransactionHistoryRepository extends JpaRepository<TransactionHistoryEntity, Long> {

    List<TransactionHistoryEntity> findByPortfolioPortfolioIdAndAssetSymbolOrderByTransactionDateDesc(Long portfolioId, String symbol);
}


