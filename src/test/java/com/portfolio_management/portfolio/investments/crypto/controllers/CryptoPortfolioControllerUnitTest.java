package com.portfolio_management.portfolio.investments.crypto.controllers;

import com.portfolio_management.portfolio.investments.crypto.Entity.Portfolio;
import com.portfolio_management.portfolio.investments.crypto.repository.PortfolioRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CryptoPortfolioControllerUnitTest {

    @Mock
    private PortfolioRepository portfolioRepository;

    private PortfolioController portfolioController;

    @BeforeEach
    void setUp() {
        portfolioController = new PortfolioController(portfolioRepository);
    }

    @Test
    void getAllPortfolios_returnsOkWithList() {
        List<Portfolio> list = List.of(samplePortfolio(1L, "Default Portfolio", new BigDecimal("1000.00")));
        when(portfolioRepository.findAll()).thenReturn(list);

        ResponseEntity<List<Portfolio>> response = portfolioController.getAllPortfolios();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(list, response.getBody());
    }

    @Test
    void getAllPortfolios_callsRepositoryOnce() {
        when(portfolioRepository.findAll()).thenReturn(List.of());

        portfolioController.getAllPortfolios();

        verify(portfolioRepository, times(1)).findAll();
    }

    @Test
    void getPortfolioById_returnsOk_whenFound() {
        Portfolio p = samplePortfolio(10L, "Growth", new BigDecimal("5000.00"));
        when(portfolioRepository.findById(10L)).thenReturn(Optional.of(p));

        ResponseEntity<Portfolio> response = portfolioController.getPortfolioById(10L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(p, response.getBody());
    }

    @Test
    void getPortfolioById_returnsNotFound_whenMissing() {
        when(portfolioRepository.findById(404L)).thenReturn(Optional.empty());

        ResponseEntity<Portfolio> response = portfolioController.getPortfolioById(404L);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void createPortfolio_returnsCreatedWithSavedBody() {
        Portfolio request = samplePortfolio(null, "Income", new BigDecimal("1500.00"));
        Portfolio saved = samplePortfolio(21L, "Income", new BigDecimal("1500.00"));
        when(portfolioRepository.save(request)).thenReturn(saved);

        ResponseEntity<Portfolio> response = portfolioController.createPortfolio(request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertSame(saved, response.getBody());
    }

    @Test
    void updatePortfolio_returnsOkAndUpdatesFields_whenFound() {
        Portfolio existing = samplePortfolio(31L, "Old Name", new BigDecimal("100.00"));
        Portfolio details = samplePortfolio(null, "New Name", new BigDecimal("250.00"));
        details.setDescription("Updated description");

        when(portfolioRepository.findById(31L)).thenReturn(Optional.of(existing));
        when(portfolioRepository.save(existing)).thenReturn(existing);

        ResponseEntity<Portfolio> response = portfolioController.updatePortfolio(31L, details);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("New Name", existing.getPortfolioName());
        assertEquals("Updated description", existing.getDescription());
        assertEquals(new BigDecimal("250.00"), existing.getCashBalance());
    }

    @Test
    void updatePortfolio_returnsNotFound_whenMissing() {
        Portfolio details = samplePortfolio(null, "New Name", new BigDecimal("250.00"));
        when(portfolioRepository.findById(999L)).thenReturn(Optional.empty());

        ResponseEntity<Portfolio> response = portfolioController.updatePortfolio(999L, details);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        verify(portfolioRepository, never()).save(details);
    }

    @Test
    void deletePortfolio_returnsNoContent_whenExists() {
        when(portfolioRepository.existsById(40L)).thenReturn(true);

        ResponseEntity<Void> response = portfolioController.deletePortfolio(40L);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(portfolioRepository, times(1)).deleteById(40L);
    }

    @Test
    void deletePortfolio_returnsNotFound_whenMissing() {
        when(portfolioRepository.existsById(401L)).thenReturn(false);

        ResponseEntity<Void> response = portfolioController.deletePortfolio(401L);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        verify(portfolioRepository, never()).deleteById(401L);
    }

    @Test
    void getPortfolioValue_returnsOkWithZeroHoldings_whenFound() {
        Portfolio p = samplePortfolio(55L, "Core", new BigDecimal("1234.56"));
        when(portfolioRepository.findById(55L)).thenReturn(Optional.of(p));

        ResponseEntity<PortfolioController.PortfolioValue> response = portfolioController.getPortfolioValue(55L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(55L, response.getBody().getPortfolioId());
        assertEquals("Core", response.getBody().getPortfolioName());
        assertEquals(new BigDecimal("1234.56"), response.getBody().getCashBalance());
        assertEquals(0.0, response.getBody().getTotalHoldingsValue());
    }

    private Portfolio samplePortfolio(Long id, String name, BigDecimal cash) {
        return new Portfolio(id, name, "Sample", cash, LocalDateTime.of(2026, 8, 6, 0, 0));
    }
}

