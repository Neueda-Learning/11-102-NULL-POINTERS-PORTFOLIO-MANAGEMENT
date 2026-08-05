package com.portfolio_management.portfolio.investments.crypto.repository;

import com.portfolio_management.portfolio.investments.crypto.Entity.CryptoHolding;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CryptoHoldingsRepository extends CrudRepository<CryptoHolding, Long> {

    /**
     * Find all holdings for a specific portfolio
     */
    @Query("SELECT * FROM crypto_holdings WHERE portfolio_id = :portfolioId")
    List<CryptoHolding> findByPortfolioId(@Param("portfolioId") Long portfolioId);

    /**
     * Find holding for a specific cryptocurrency in a portfolio
     */
    @Query("SELECT * FROM crypto_holdings WHERE portfolio_id = :portfolioId AND crypto_id = :cryptoId")
    Optional<CryptoHolding> findByPortfolioIdAndCryptoId(
            @Param("portfolioId") Long portfolioId,
            @Param("cryptoId") Long cryptoId
    );

    /**
     * Find all holdings for a specific cryptocurrency
     */
    @Query("SELECT * FROM crypto_holdings WHERE crypto_id = :cryptoId")
    List<CryptoHolding> findByCryptoId(@Param("cryptoId") Long cryptoId);

    /**
     * Delete all holdings for a portfolio
     */
    @Query("DELETE FROM crypto_holdings WHERE portfolio_id = :portfolioId")
    void deleteByPortfolioId(@Param("portfolioId") Long portfolioId);
}

