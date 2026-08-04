package com.portfolio_management.portfolio.investments.stock.config;

import com.portfolio_management.portfolio.investments.stock.entity.PortfolioEntity;
import com.portfolio_management.portfolio.investments.stock.repository.PortfolioRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;

@Component
public class PortfolioBootstrapRunner implements CommandLineRunner {

    private final PortfolioRepository portfolioRepository;

    public PortfolioBootstrapRunner(PortfolioRepository portfolioRepository) {
        this.portfolioRepository = portfolioRepository;
    }

    @Override
    public void run(String... args) {
        if (portfolioRepository.existsById(1L)) {
            return;
        }

        PortfolioEntity portfolio = new PortfolioEntity();
        portfolio.setPortfolioId(1L);
        portfolio.setPortfolioName("Default Portfolio");
        portfolio.setDescription("Auto-created default portfolio for stock module operations");
        portfolio.setCashBalance(new BigDecimal("10000.00"));
        portfolio.setCreatedAt(Instant.now());

        portfolioRepository.save(portfolio);
    }
}

