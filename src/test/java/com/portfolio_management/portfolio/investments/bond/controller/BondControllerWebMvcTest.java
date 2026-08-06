package com.portfolio_management.portfolio.investments.bond.controller;

import com.portfolio_management.portfolio.investments.bond.dto.BondRequestDTO;
import com.portfolio_management.portfolio.investments.bond.dto.BondResponseDTO;
import com.portfolio_management.portfolio.investments.bond.service.BondService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class BondControllerWebMvcTest {

    @Mock
    private BondService bondService;

    private BondController bondController;

    @BeforeEach
    void setUp() {
        bondController = new BondController(bondService);
    }

    @Test
    void getAllBonds_returnsOneItem_whenServiceReturnsData() {
        when(bondService.getAllBonds()).thenReturn(List.of(sampleResponse(1L, "HDFC Bank")));

        List<BondResponseDTO> result = bondController.getAllBonds();

        assertEquals(1, result.size());
    }

    @Test
    void getAllBonds_returnsExpectedIssuerField() {
        when(bondService.getAllBonds()).thenReturn(List.of(sampleResponse(1L, "HDFC Bank")));

        List<BondResponseDTO> result = bondController.getAllBonds();

        assertEquals("HDFC Bank", result.get(0).issuer());
    }

    @Test
    void getAllBonds_returnsEmptyList_whenServiceReturnsEmptyList() {
        when(bondService.getAllBonds()).thenReturn(List.of());

        List<BondResponseDTO> result = bondController.getAllBonds();

        assertTrue(result.isEmpty());
    }

    @Test
    void createBond_returnsServicePayload_whenRequestIsValid() {
        BondRequestDTO request = validRequest();
        when(bondService.createBond(any(BondRequestDTO.class))).thenReturn(sampleResponse(11L, "ICICI Bank"));

        BondResponseDTO result = bondController.createBond(request);

        assertNotNull(result);
        assertEquals(11L, result.id());
    }

    @Test
    void createBond_callsServiceOnce_whenPayloadIsValid() {
        BondRequestDTO request = validRequest();
        when(bondService.createBond(any(BondRequestDTO.class))).thenReturn(sampleResponse(12L, "ICICI Bank"));

        bondController.createBond(request);

        verify(bondService, times(1)).createBond(any(BondRequestDTO.class));
    }

    @Test
    void createBond_passesIssuerAsProvided_toService() {
        BondRequestDTO request = new BondRequestDTO(" ", new BigDecimal("7.25"), new BigDecimal("1000.00"), LocalDate.now(), 12);
        when(bondService.createBond(any(BondRequestDTO.class))).thenReturn(sampleResponse(13L, " "));

        bondController.createBond(request);

        verify(bondService).createBond(request);
    }

    @Test
    void createBond_passesNullInterestRate_toService() {
        BondRequestDTO request = new BondRequestDTO("HDFC Bank", null, new BigDecimal("1000.00"), LocalDate.now(), 12);
        when(bondService.createBond(any(BondRequestDTO.class))).thenReturn(sampleResponse(14L, "HDFC Bank"));

        bondController.createBond(request);

        verify(bondService).createBond(request);
    }

    @Test
    void createBond_passesLowAmount_toService() {
        BondRequestDTO request = new BondRequestDTO("HDFC Bank", new BigDecimal("7.25"), new BigDecimal("0.001"), LocalDate.now(), 12);
        when(bondService.createBond(any(BondRequestDTO.class))).thenReturn(sampleResponse(15L, "HDFC Bank"));

        bondController.createBond(request);

        verify(bondService).createBond(request);
    }

    @Test
    void createBond_passesZeroTenure_toService() {
        BondRequestDTO request = new BondRequestDTO("HDFC Bank", new BigDecimal("7.25"), new BigDecimal("1000.00"), LocalDate.now(), 0);
        when(bondService.createBond(any(BondRequestDTO.class))).thenReturn(sampleResponse(16L, "HDFC Bank"));

        bondController.createBond(request);

        verify(bondService).createBond(request);
    }

    @Test
    void deleteBond_handlesDeletedAndMissingCases() {
        when(bondService.deleteBond(101L)).thenReturn(true);
        when(bondService.deleteBond(999L)).thenReturn(false);

        assertDoesNotThrow(() -> bondController.deleteBond(101L));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> bondController.deleteBond(999L));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    private BondRequestDTO validRequest() {
        return new BondRequestDTO(
                "ICICI Bank",
                new BigDecimal("7.10"),
                new BigDecimal("2500.00"),
                LocalDate.of(2026, 1, 10),
                24
        );
    }

    private BondResponseDTO sampleResponse(Long id, String issuer) {
        return new BondResponseDTO(
                id,
                issuer,
                new BigDecimal("7.10"),
                new BigDecimal("2500.00"),
                LocalDate.of(2026, 1, 10),
                24,
                LocalDate.of(2028, 1, 10),
                new BigDecimal("2500.00"),
                new BigDecimal("177.50"),
                new BigDecimal("7.1000"),
                new BigDecimal("7.1000"),
                new BigDecimal("2855.00")
        );
    }
}


