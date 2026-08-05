package com.portfolio_management.portfolio.investments.crypto.controllers;

import com.portfolio_management.portfolio.investments.crypto.Entity.Portfolio;
import com.portfolio_management.portfolio.investments.crypto.repository.PortfolioRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/v1/portfolio")
@CrossOrigin(origins = "*", maxAge = 3600)
public class PortfolioController {

    private final PortfolioRepository portfolioRepository;

    public PortfolioController(PortfolioRepository portfolioRepository) {
        this.portfolioRepository = portfolioRepository;
    }

    /**
     * Get all portfolios
     * GET /api/v1/portfolio
     */
    @GetMapping
    public ResponseEntity<List<Portfolio>> getAllPortfolios() {
        log.info("Retrieving all portfolios");
        List<Portfolio> portfolios = (List<Portfolio>) portfolioRepository.findAll();
        return ResponseEntity.ok(portfolios);
    }

    /**
     * Get a portfolio by ID
     * GET /api/v1/portfolio/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<Portfolio> getPortfolioById(@PathVariable Long id) {
        log.info("Retrieving portfolio with ID: {}", id);
        Optional<Portfolio> portfolio = portfolioRepository.findById(id);
        return portfolio.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Create a new portfolio
     * POST /api/v1/portfolio
     */
    @PostMapping
    public ResponseEntity<Portfolio> createPortfolio(@RequestBody Portfolio portfolio) {
        log.info("Creating new portfolio: {}", portfolio.getPortfolioName());
        Portfolio savedPortfolio = portfolioRepository.save(portfolio);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedPortfolio);
    }

    /**
     * Update a portfolio
     * PUT /api/v1/portfolio/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<Portfolio> updatePortfolio(@PathVariable Long id, @RequestBody Portfolio portfolioDetails) {
        log.info("Updating portfolio with ID: {}", id);
        Optional<Portfolio> existingPortfolio = portfolioRepository.findById(id);

        if (existingPortfolio.isPresent()) {
            Portfolio portfolio = existingPortfolio.get();
            portfolio.setPortfolioName(portfolioDetails.getPortfolioName());
            portfolio.setDescription(portfolioDetails.getDescription());
            portfolio.setCashBalance(portfolioDetails.getCashBalance());

            Portfolio updatedPortfolio = portfolioRepository.save(portfolio);
            return ResponseEntity.ok(updatedPortfolio);
        }

        return ResponseEntity.notFound().build();
    }

    /**
     * Delete a portfolio
     * DELETE /api/v1/portfolio/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePortfolio(@PathVariable Long id) {
        log.info("Deleting portfolio with ID: {}", id);
        if (portfolioRepository.existsById(id)) {
            portfolioRepository.deleteById(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    /**
     * Get total portfolio value
     * GET /api/v1/portfolio/{id}/value
     */
    @GetMapping("/{id}/value")
    public ResponseEntity<PortfolioValue> getPortfolioValue(@PathVariable Long id) {
        log.info("Calculating portfolio value for ID: {}", id);
        Optional<Portfolio> portfolio = portfolioRepository.findById(id);

        if (portfolio.isPresent()) {
            Portfolio p = portfolio.get();
            PortfolioValue value = new PortfolioValue(
                    p.getPortfolioId(),
                    p.getPortfolioName(),
                    p.getCashBalance(),
                    0.0 // TODO: Calculate total holdings value
            );
            return ResponseEntity.ok(value);
        }

        return ResponseEntity.notFound().build();
    }

    /**
     * Inner class for portfolio value response
     */
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class PortfolioValue {
        private Long portfolioId;
        private String portfolioName;
        private java.math.BigDecimal cashBalance;
        private Double totalHoldingsValue;
    }
}

