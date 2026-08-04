package com.portfolio_management.portfolio.investments.bond.controller;

import com.portfolio_management.portfolio.investments.bond.dto.BondRequestDTO;
import com.portfolio_management.portfolio.investments.bond.dto.BondResponseDTO;
import com.portfolio_management.portfolio.investments.bond.service.BondService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/bonds")
public class BondController {

    private final BondService bondService;

    public BondController(BondService bondService) {
        this.bondService = bondService;
    }

    @GetMapping
    public List<BondResponseDTO> getAllBonds() {
        return bondService.getAllBonds();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BondResponseDTO createBond(@Valid @RequestBody BondRequestDTO request) {
        return bondService.createBond(request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBond(@PathVariable Long id) {
        boolean deleted = bondService.deleteBond(id);
        if (!deleted) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Bond not found");
        }
    }
}

