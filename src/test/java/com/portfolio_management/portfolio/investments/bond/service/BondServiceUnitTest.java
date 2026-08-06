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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

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
    void getAllBonds_mapsIssuerAndId() {
        when(bondRepository.findAll()).thenReturn(List.of(sampleBond(1L, "HDFC", new BigDecimal("1000.00"), new BigDecimal("7.2000"), 12)));

        BondResponseDTO dto = bondService.getAllBonds().get(0);

        assertEquals(1L, dto.id());
        assertEquals("HDFC", dto.issuer());
    }

    @Test
    void getAllBonds_calculatesMaturityAndYieldFields() {
        when(bondRepository.findAll()).thenReturn(List.of(sampleBond(2L, "ICICI", new BigDecimal("2500.00"), new BigDecimal("7.1000"), 24)));

        BondResponseDTO dto = bondService.getAllBonds().get(0);

        assertEquals(new BigDecimal("177.50"), dto.annualIncome());
        assertEquals(new BigDecimal("7.1000"), dto.currentYield());
        assertEquals(new BigDecimal("2855.00"), dto.maturityAmount());
    }

    @Test
    void createBond_returnsExistingDuplicate_withoutSaving() {
        BondRequestDTO request = request("HDFC", "7.2500", "1000.00", 12);
        Bond existing = sampleBond(3L, "HDFC", new BigDecimal("1000.00"), new BigDecimal("7.2500"), 12);
        when(bondRepository.findDuplicate(anyString(), any(), any(), any(), anyInt())).thenReturn(Optional.of(existing));

        BondResponseDTO dto = bondService.createBond(request);

        assertEquals(3L, dto.id());
        verify(assetRepository, never()).save(any(Asset.class));
        verify(bondRepository, never()).save(any(Bond.class));
        verify(jdbcTemplate, never()).update(contains("transaction_history"), any(), any(), any(), any(), any());
    }

    @Test
    void createBond_createsBondAndTransaction_whenNoDuplicate() {
        BondRequestDTO request = request("Axis", "7.5000", "2000.00", 18);
        when(bondRepository.findDuplicate(anyString(), any(), any(), any(), anyInt())).thenReturn(Optional.empty());
        when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Long>>any())).thenReturn(List.of(99L));
        when(assetRepository.existsBySymbol(anyString())).thenReturn(false);
        when(assetRepository.save(any(Asset.class))).thenAnswer(invocation -> {
            Asset a = invocation.getArgument(0);
            a.setAssetId(101L);
            return a;
        });
        when(bondRepository.save(any(Bond.class))).thenAnswer(invocation -> {
            Bond b = invocation.getArgument(0);
            b.setAssetId(101L);
            return b;
        });

        BondResponseDTO dto = bondService.createBond(request);

        assertEquals(101L, dto.id());
        verify(jdbcTemplate, times(1)).update(contains("transaction_history"), eq(99L), eq(101L), eq("BUY"), any(BigDecimal.class), eq(new BigDecimal("2000.00")));
    }

    @Test
    void createBond_usesExistingPortfolioId_whenPresent() {
        BondRequestDTO request = request("SBI", "6.9000", "1500.00", 12);
        when(bondRepository.findDuplicate(anyString(), any(), any(), any(), anyInt())).thenReturn(Optional.empty());
        when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Long>>any())).thenReturn(List.of(7L));
        when(assetRepository.existsBySymbol(anyString())).thenReturn(false);
        when(assetRepository.save(any(Asset.class))).thenAnswer(invocation -> {
            Asset a = invocation.getArgument(0);
            a.setAssetId(102L);
            return a;
        });
        when(bondRepository.save(any(Bond.class))).thenAnswer(invocation -> invocation.getArgument(0));

        bondService.createBond(request);

        verify(jdbcTemplate, never()).update(contains("INSERT INTO portfolio"), any(), any(), any());
    }

    @Test
    void createBond_createsDefaultPortfolio_whenMissing() {
        BondRequestDTO request = request("Kotak", "7.0000", "1800.00", 12);
        when(bondRepository.findDuplicate(anyString(), any(), any(), any(), anyInt())).thenReturn(Optional.empty());
        when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Long>>any())).thenReturn(List.of());
        when(jdbcTemplate.queryForObject(contains("SELECT portfolio_id FROM portfolio ORDER BY portfolio_id DESC LIMIT 1"), eq(Long.class))).thenReturn(501L);
        when(assetRepository.existsBySymbol(anyString())).thenReturn(false);
        when(assetRepository.save(any(Asset.class))).thenAnswer(invocation -> {
            Asset a = invocation.getArgument(0);
            a.setAssetId(103L);
            return a;
        });
        when(bondRepository.save(any(Bond.class))).thenAnswer(invocation -> invocation.getArgument(0));

        bondService.createBond(request);

        verify(jdbcTemplate, times(1)).update(contains("INSERT INTO portfolio"), eq("Default Portfolio"), eq("Auto-created for first asset"), eq(BigDecimal.ZERO));
    }

    @Test
    void createBond_generatesFallbackSymbol_forNonAlphanumericIssuer() {
        BondRequestDTO request = request("***", "8.0000", "3000.00", 24);
        when(bondRepository.findDuplicate(anyString(), any(), any(), any(), anyInt())).thenReturn(Optional.empty());
        when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Long>>any())).thenReturn(List.of(88L));
        when(assetRepository.existsBySymbol("BONDBOND")).thenReturn(false);
        when(assetRepository.save(any(Asset.class))).thenAnswer(invocation -> {
            Asset a = invocation.getArgument(0);
            a.setAssetId(104L);
            return a;
        });
        when(bondRepository.save(any(Bond.class))).thenAnswer(invocation -> invocation.getArgument(0));

        bondService.createBond(request);

        ArgumentCaptor<Asset> captor = ArgumentCaptor.forClass(Asset.class);
        verify(assetRepository).save(captor.capture());
        assertEquals("BONDBOND", captor.getValue().getSymbol());
    }

    @Test
    void deleteBond_returnsFalse_whenNotFound() {
        when(bondRepository.existsById(999L)).thenReturn(false);

        boolean result = bondService.deleteBond(999L);

        assertFalse(result);
        verify(assetRepository, never()).deleteById(any(Long.class));
    }

    @Test
    void deleteBond_returnsTrue_andDeletesAsset_whenFound() {
        when(bondRepository.existsById(777L)).thenReturn(true);

        boolean result = bondService.deleteBond(777L);

        assertTrue(result);
        verify(assetRepository, times(1)).deleteById(777L);
    }

    private BondRequestDTO request(String issuer, String rate, String amount, int tenure) {
        return new BondRequestDTO(
                issuer,
                new BigDecimal(rate),
                new BigDecimal(amount),
                LocalDate.of(2026, 1, 1),
                tenure
        );
    }

    private Bond sampleBond(Long assetId, String issuer, BigDecimal amount, BigDecimal rate, int tenureMonths) {
        Asset asset = new Asset();
        asset.setAssetId(assetId);
        asset.setPortfolioId(10L);
        asset.setAssetType(AssetType.BOND);
        asset.setAssetName(issuer);
        asset.setSymbol(issuer.toUpperCase() + "BOND");
        asset.setCurrency("USD");

        Bond bond = new Bond();
        bond.setAssetId(assetId);
        bond.setAsset(asset);
        bond.setIssuer(issuer);
        bond.setAmountInvested(amount);
        bond.setInterestRate(rate);
        bond.setStartDate(LocalDate.of(2026, 1, 1));
        bond.setTenureMonths(tenureMonths);
        bond.setMaturityDate(LocalDate.of(2026, 1, 1).plusMonths(tenureMonths));
        return bond;
    }
}

