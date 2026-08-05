package com.portfolio_management.portfolio.investments.crypto.repository;

import com.portfolio_management.portfolio.investments.crypto.Entity.Crypto;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CryptoRepository extends CrudRepository<Crypto, Long> {

    @Query("SELECT * FROM crypto WHERE asset_id = :assetId")
    Optional<Crypto> findByAssetId(@Param("assetId") Long assetId);

    /**
     * Get all cryptocurrencies ordered by current value descending
     */
    @Query("SELECT * FROM crypto ORDER BY current_value DESC")
    Iterable<Crypto> findAllOrderByMarketCapDesc();
}
