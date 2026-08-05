package com.portfolio_management.portfolio.investments.crypto.repository;

import com.portfolio_management.portfolio.investments.crypto.Entity.Crypto;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CryptoRepository extends CrudRepository<Crypto, Long> {

    /**
     * Find a cryptocurrency by its symbol
     */
    @Query("SELECT * FROM crypto WHERE symbol = :symbol")
    Optional<Crypto> findBySymbol(@Param("symbol") String symbol);

    /**
     * Find a cryptocurrency by its name
     */
    @Query("SELECT * FROM crypto WHERE name = :name")
    Optional<Crypto> findByName(@Param("name") String name);

    /**
     * Get all cryptocurrencies ordered by current value descending
     */
    @Query("SELECT * FROM crypto ORDER BY current_value DESC")
    Iterable<Crypto> findAllOrderByMarketCapDesc();
}
