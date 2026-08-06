package com.portfolio_management.portfolio.investments.bond.service;

import com.portfolio_management.portfolio.investments.asset.model.Asset;
import com.portfolio_management.portfolio.investments.asset.model.AssetType;
import com.portfolio_management.portfolio.investments.asset.repository.AssetRepository;
import com.portfolio_management.portfolio.investments.bond.dto.BondRequestDTO;
import com.portfolio_management.portfolio.investments.bond.dto.BondResponseDTO;
import com.portfolio_management.portfolio.investments.bond.model.Bond;
import com.portfolio_management.portfolio.investments.bond.repository.BondRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BondServiceUnitTest {

    @Mock
    private BondRepository bondRepository;

    @Mock
    private AssetRepository assetRepository;

    @Mock
    private JdbcTemplate jdbcTemplate;

    private BondService bondService;

    @BeforeEach
    void setUp() {
        bondService = new BondService(bondRepository, assetRepository, jdbcTemplate);
    }

    @Test
    void getAllBonds_returnsEmptyList_whenRepositoryEmpty() {
        when(bondRepository.findAll()).thenReturn(List.of());

        List<BondResponseDTO> result = bondService.getAllBonds();

        assertTrue(result.isEmpty());
    }

    @Test
    void getAllBonds_mapsSingleBond_toExpectedResponseFields() {
        when(bondRepository.findAll()).thenReturn(List.of(sampleBond(41L, "HDFC Bank", new BigDecimal("1000.00"), new BigDecimal("7.2000"), 12)));

        List<BondResponseDTO> result = bondService.getAllBonds();

        assertEquals(1, result.size());
        assertEquals(41L, result.get(0).id());
        assertEquals("HDFC Bank", result.get(0).issuer());
        assertEquals(new BigDecimal("1000.00"), result.get(0).amountInvested());
    }

    @Test
    void getAllBonds_calculatesAnnualIncomeCurrentYieldAndMaturityAmount_withExpectedRounding() {
        when(bondRepository.findAll()).thenReturn(List.of(sampleBond(52L, "ICICI Bank", new BigDecimal("2500.00"), new BigDecimal("7.1000"), 24)));

        BondResponseDTO result = bondService.getAllBonds().get(0);

        assertEquals(new BigDecimal("177.50"), result.annualIncome());
        assertEquals(new BigDecimal("7.1000"), result.currentYield());
        assertEquals(new BigDecimal("2855.00"), result.maturityAmount());
    }

    @Test
    void createBond_returnsExistingDuplicate_withoutSavingAssetBondOrTransaction() {
        Bond existing = sampleBond(1L, "HDFC Bank", new BigDecimal("1000.00"), new BigDecimal("7.2500"), 12);
        BondRequestDTO request = new BondRequestDTO("HDFC Bank", new BigDecimal("7.2500"), new BigDecimal("1000.00"), LocalDate.of(2026, 1, 1), 12);
        when(bondRepository.findDuplicate(anyString(), any(), any(), any(), anyInt())).thenReturn(Optional.of(existing));

        BondResponseDTO result = bondService.createBond(request);

        assertEquals(1L, result.id());
        verify(assetRepository, never()).save(any(Asset.class));
        verify(bondRepository, never()).save(any(Bond.class));
        verify(jdbcTemplate, never()).update(contains("transaction_history"), any(), any(), any(), any(), any());
    }

    @Test
    void createBond_createsNewBond_whenNoDuplicate_andReturnsMappedResponse() {
        BondRequestDTO request = new BondRequestDTO("Axis Bank", new BigDecimal("7.5000"), new BigDecimal("2000.00"), LocalDate.of(2026, 2, 1), 18);
        when(bondRepository.findDuplicate(anyString(), any(), any(), any(), anyInt())).thenReturn(Optional.empty());
        when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Long>>any())).thenReturn(List.of(99L));
        when(assetRepository.existsBySymbol(anyString())).thenReturn(false);
        when(assetRepository.save(any(Asset.class))).thenAnswer(invocation -> {
            Asset a = invocation.getArgument(0);
            a.setAssetId(200L);
            return a;
        });
        when(bondRepository.save(any(Bond.class))).thenAnswer(invocation -> {
            Bond b = invocation.getArgument(0);
            b.setAssetId(200L);
            return b;
        });

        BondResponseDTO result = bondService.createBond(request);

        assertEquals(200L, result.id());
        assertEquals("Axis Bank", result.issuer());
        verify(jdbcTemplate, times(1)).update(contains("transaction_history"), eq(99L), eq(200L), eq("BUY"), any(BigDecimal.class), eq(new BigDecimal("2000.00")));
    }

    @Test
    void createBond_usesExistingPortfolioId_whenPortfolioAlreadyExists() {
        BondRequestDTO request = new BondRequestDTO("SBI", new BigDecimal("6.9000"), new BigDecimal("1500.00"), LocalDate.of(2026, 3, 1), 12);
        when(bondRepository.findDuplicate(anyString(), any(), any(), any(), anyInt())).thenReturn(Optional.empty());
        when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Long>>any())).thenReturn(List.of(7L));
        when(assetRepository.existsBySymbol(anyString())).thenReturn(false);
        when(assetRepository.save(any(Asset.class))).thenAnswer(invocation -> {
            Asset a = invocation.getArgument(0);
            a.setAssetId(501L);
            return a;
        });
        when(bondRepository.save(any(Bond.class))).thenAnswer(invocation -> invocation.getArgument(0));

        bondService.createBond(request);

        verify(jdbcTemplate, never()).update(contains("INSERT INTO portfolio"), any(), any(), any());
    }

    @Test
    void createBond_createsDefaultPortfolio_whenNoPortfolioExists() {
        BondRequestDTO request = new BondRequestDTO("Kotak", new BigDecimal("7.0000"), new BigDecimal("1800.00"), LocalDate.of(2026, 4, 1), 12);
        when(bondRepository.findDuplicate(anyString(), any(), any(), any(), anyInt())).thenReturn(Optional.empty());
        when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Long>>any())).thenReturn(List.of());
        when(jdbcTemplate.queryForObject(contains("SELECT portfolio_id FROM portfolio ORDER BY portfolio_id DESC LIMIT 1"), eq(Long.class))).thenReturn(321L);
        when(assetRepository.existsBySymbol(anyString())).thenReturn(false);
        when(assetRepository.save(any(Asset.class))).thenAnswer(invocation -> {
            Asset a = invocation.getArgument(0);
            a.setAssetId(601L);
            return a;
        });
        when(bondRepository.save(any(Bond.class))).thenAnswer(invocation -> invocation.getArgument(0));

        bondService.createBond(request);

        verify(jdbcTemplate, times(1)).update(contains("INSERT INTO portfolio"), eq("Default Portfolio"), eq("Auto-created for first asset"), eq(BigDecimal.ZERO));
    }

    @Test
    void createBond_generatesFallbackSymbol_whenIssuerHasNoAlphanumericCharacters() {
        BondRequestDTO request = new BondRequestDTO("***", new BigDecimal("8.0000"), new BigDecimal("3000.00"), LocalDate.of(2026, 5, 1), 24);
        when(bondRepository.findDuplicate(anyString(), any(), any(), any(), anyInt())).thenReturn(Optional.empty());
        when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Long>>any())).thenReturn(List.of(88L));
        when(assetRepository.existsBySymbol("BONDBOND")).thenReturn(false);
        when(assetRepository.save(any(Asset.class))).thenAnswer(invocation -> {
            Asset a = invocation.getArgument(0);
            a.setAssetId(701L);
            return a;
        });
        when(bondRepository.save(any(Bond.class))).thenAnswer(invocation -> invocation.getArgument(0));

        bondService.createBond(request);

        ArgumentCaptor<Asset> assetCaptor = ArgumentCaptor.forClass(Asset.class);
        verify(assetRepository).save(assetCaptor.capture());
        assertEquals("BONDBOND", assetCaptor.getValue().getSymbol());
    }

    @Test
    void deleteBond_returnsFalse_whenBondNotFound_andSkipsDeleteAndTransactionInsert() {
        when(bondRepository.findById(999L)).thenReturn(Optional.empty());

        boolean result = bondService.deleteBond(999L);

        assertFalse(result);
        verify(jdbcTemplate, never()).update(contains("transaction_history"), any(), any(), any(), any(), any());
        verify(bondRepository, never()).deleteById(any(Long.class));
    }

    @Test
    void deleteBond_returnsTrue_whenBondFound_insertsSellTransaction_andDeletesBondById() {
        Bond bond = sampleBond(777L, "Reliance", new BigDecimal("2500.00"), new BigDecimal("7.8000"), 12);
        when(bondRepository.findById(777L)).thenReturn(Optional.of(bond));

        boolean result = bondService.deleteBond(777L);

        assertTrue(result);
        verify(jdbcTemplate, times(1)).update(contains("transaction_history"), eq(10L), eq(777L), eq("SELL"), eq(new BigDecimal("1.0000")), eq(new BigDecimal("2500.00")));
        verify(bondRepository, times(1)).deleteById(777L);
    }

    private Bond sampleBond(Long assetId, String issuer, BigDecimal amountInvested, BigDecimal rate, int tenureMonths) {
        Asset asset = new Asset();
        asset.setAssetId(assetId);
        asset.setPortfolioId(10L);
        asset.setAssetType(AssetType.BOND);
        asset.setAssetName(issuer);
        asset.setSymbol(issuer.replaceAll("[^A-Za-z0-9]", "").toUpperCase() + "BOND");
        asset.setCurrency("USD");

        Bond bond = new Bond();
        bond.setAssetId(assetId);
        bond.setAsset(asset);
        bond.setIssuer(issuer);
        bond.setAmountInvested(amountInvested);
        bond.setInterestRate(rate);
        bond.setStartDate(LocalDate.of(2026, 1, 1));
        bond.setTenureMonths(tenureMonths);
        bond.setMaturityDate(LocalDate.of(2026, 1, 1).plusMonths(tenureMonths));
        return bond;
    }
}


