package com.portfolio_management.portfolio.investments.crypto.repository;

import com.portfolio_management.portfolio.investments.crypto.Entity.Asset;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AssetRepository extends CrudRepository<Asset, Long> {

    @Query("SELECT * FROM asset WHERE symbol = :symbol")
    Optional<Asset> findBySymbol(@Param("symbol") String symbol);

    @Query("SELECT * FROM asset WHERE asset_name = :name")
    Optional<Asset> findByName(@Param("name") String name);
}

