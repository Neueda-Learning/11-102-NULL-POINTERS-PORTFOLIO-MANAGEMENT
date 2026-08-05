package com.portfolio_management.portfolio.investments.crypto.controllers;

import com.portfolio_management.portfolio.investments.crypto.Entity.Crypto;
import com.portfolio_management.portfolio.investments.crypto.Entity.Transaction;
import com.portfolio_management.portfolio.investments.crypto.dto.TransactionHistoryDTO;
import com.portfolio_management.portfolio.investments.crypto.repository.CryptoRepository;
import com.portfolio_management.portfolio.investments.crypto.repository.TransactionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/transactions")
@CrossOrigin(origins = "*", maxAge = 3600)
public class TransactionController {

    private final TransactionRepository transactionRepository;
    private final CryptoRepository cryptoRepository;

    public TransactionController(TransactionRepository transactionRepository, CryptoRepository cryptoRepository) {
        this.transactionRepository = transactionRepository;
        this.cryptoRepository = cryptoRepository;
    }

    /**
     * Get transaction history (optionally filtered by portfolioId and/or cryptoId)
     * GET /api/v1/transactions/history
     */
    @GetMapping("/history")
    public ResponseEntity<List<TransactionHistoryDTO>> getTransactionHistory(
            @RequestParam(required = false) Long portfolioId,
            @RequestParam(required = false) Long cryptoId
    ) {
        log.info("Fetching transaction history, portfolioId={}, cryptoId={}", portfolioId, cryptoId);

        List<Transaction> transactions;
        if (portfolioId != null && cryptoId != null) {
            transactions = transactionRepository.findByPortfolioIdAndCryptoId(portfolioId, cryptoId);
        } else if (portfolioId != null) {
            transactions = transactionRepository.findByPortfolioId(portfolioId);
        } else if (cryptoId != null) {
            transactions = transactionRepository.findByCryptoId(cryptoId);
        } else {
            transactions = new ArrayList<>();
            transactionRepository.findAll().forEach(transactions::add);
            transactions.sort(Comparator.comparing(Transaction::getTransactionDate).reversed());
        }

        List<TransactionHistoryDTO> response = transactions.stream()
                .map(this::toDTO)
                .toList();

        return ResponseEntity.ok(response);
    }

    private TransactionHistoryDTO toDTO(Transaction transaction) {
        String symbol = cryptoRepository.findById(transaction.getCryptoId())
                .map(Crypto::getSymbol)
                .orElse("UNKNOWN");

        return new TransactionHistoryDTO(
                transaction.getTransactionId(),
                transaction.getPortfolioId(),
                transaction.getCryptoId(),
                symbol,
                transaction.getTransactionType(),
                transaction.getQuantity(),
                transaction.getTransactionPrice(),
                transaction.getTransactionDate()
        );
    }
}

