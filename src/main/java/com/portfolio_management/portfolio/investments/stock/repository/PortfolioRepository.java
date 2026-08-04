package com.portfolio_management.portfolio.investments.stock.repository;

import com.portfolio_management.portfolio.investments.stock.entity.PortfolioEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PortfolioRepository extends JpaRepository<PortfolioEntity, Long> {
}

