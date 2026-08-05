package com.portfolio_management.portfolio.investments.crypto.repository;

import com.portfolio_management.portfolio.investments.crypto.Entity.Transaction;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository("cryptoTransactionRepository")
public interface TransactionRepository extends CrudRepository<Transaction, Long> {

    /**
     * Find all transactions for a specific portfolio
     */
    @Query("SELECT * FROM transaction_history WHERE portfolio_id = :portfolioId ORDER BY transaction_date DESC")
    List<Transaction> findByPortfolioId(@Param("portfolioId") Long portfolioId);

    /**
     * Find all transactions for a specific asset
     */
    @Query("SELECT * FROM transaction_history WHERE asset_id = :assetId ORDER BY transaction_date DESC")
    List<Transaction> findByAssetId(@Param("assetId") Long assetId);

    /**
     * Find all transactions for a portfolio and asset
     */
    @Query("SELECT * FROM transaction_history WHERE portfolio_id = :portfolioId AND asset_id = :assetId ORDER BY transaction_date DESC")
    List<Transaction> findByPortfolioIdAndAssetId(
            @Param("portfolioId") Long portfolioId,
            @Param("assetId") Long assetId
    );

    /**
     * Get all transactions of a specific type
     */
    @Query("SELECT * FROM transaction_history WHERE transaction_type = :type ORDER BY transaction_date DESC")
    List<Transaction> findByTransactionType(@Param("type") String type);
}


