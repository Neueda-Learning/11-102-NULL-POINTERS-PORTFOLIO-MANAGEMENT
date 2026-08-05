package com.portfolio_management.portfolio.investments.crypto.controllers;

import com.portfolio_management.portfolio.investments.crypto.Entity.Asset;
import com.portfolio_management.portfolio.investments.crypto.Entity.Crypto;
import com.portfolio_management.portfolio.investments.crypto.Entity.Transaction;
import com.portfolio_management.portfolio.investments.crypto.dto.TransactionHistoryDTO;
import com.portfolio_management.portfolio.investments.crypto.repository.AssetRepository;
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
    private final AssetRepository assetRepository;
    private final CryptoRepository cryptoRepository;

    public TransactionController(TransactionRepository transactionRepository, AssetRepository assetRepository, CryptoRepository cryptoRepository) {
        this.transactionRepository = transactionRepository;
        this.assetRepository = assetRepository;
        this.cryptoRepository = cryptoRepository;
    }

    /**
     * Get transaction history (optionally filtered by portfolioId and/or assetId)
     * GET /api/v1/transactions/history
     */
    @GetMapping("/history")
    public ResponseEntity<List<TransactionHistoryDTO>> getTransactionHistory(
            @RequestParam(required = false) Long portfolioId,
            @RequestParam(required = false) Long assetId
    ) {
        log.info("Fetching transaction history, portfolioId={}, assetId={}", portfolioId, assetId);

        List<Transaction> transactions;
        if (portfolioId != null && assetId != null) {
            transactions = transactionRepository.findByPortfolioIdAndAssetId(portfolioId, assetId);
        } else if (portfolioId != null) {
            transactions = transactionRepository.findByPortfolioId(portfolioId);
        } else if (assetId != null) {
            transactions = transactionRepository.findByAssetId(assetId);
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
        Asset asset = transaction.getAssetId() == null
                ? new Asset(null, "UNKNOWN", "UNKNOWN", "UNKNOWN", null)
                : assetRepository.findById(transaction.getAssetId())
                        .orElse(new Asset(transaction.getAssetId(), "UNKNOWN", "UNKNOWN", "UNKNOWN", null));

        Crypto crypto = transaction.getAssetId() == null
                ? null
                : cryptoRepository.findByAssetId(transaction.getAssetId())
                        .orElse(null);

        String symbol = asset.getSymbol();
        String name = asset.getName();

        if (crypto != null) {
            // The crypto row is the asset-specific extension; keeping the lookup ensures the two tables stay joined.
            symbol = asset.getSymbol();
            name = asset.getName();
        }

        return new TransactionHistoryDTO(
                transaction.getTransactionId(),
                transaction.getPortfolioId(),
                transaction.getAssetId(),
                symbol,
                name,
                transaction.getTransactionType(),
                transaction.getQuantity(),
                transaction.getTransactionPrice(),
                transaction.getTransactionDate()
        );
    }
}

