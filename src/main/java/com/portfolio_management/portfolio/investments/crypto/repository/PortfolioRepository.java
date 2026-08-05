package com.portfolio_management.portfolio.investments.crypto.repository;

import com.portfolio_management.portfolio.investments.crypto.Entity.Portfolio;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PortfolioRepository extends CrudRepository<Portfolio, Long> {

    /**
     * Find a portfolio by name
     */
    @Query("SELECT * FROM portfolio WHERE portfolio_name = :name")
    Optional<Portfolio> findByPortfolioName(@Param("name") String name);

    /**
     * Get all portfolios ordered by creation date descending
     */
    @Query("SELECT * FROM portfolio ORDER BY created_at DESC")
    Iterable<Portfolio> findAllOrderByCreatedAtDesc();
}

