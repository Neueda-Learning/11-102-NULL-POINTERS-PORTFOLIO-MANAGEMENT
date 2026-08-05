package com.portfolio_management.portfolio.investments.stock.repository;

import com.portfolio_management.portfolio.investments.stock.entity.PortfolioEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository("stockPortfolioRepository")
public interface PortfolioRepository extends JpaRepository<PortfolioEntity, Long> {
}


