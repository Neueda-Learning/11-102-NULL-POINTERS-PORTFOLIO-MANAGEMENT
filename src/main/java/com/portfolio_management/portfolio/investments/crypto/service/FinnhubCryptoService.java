package com.portfolio_management.portfolio.investments.crypto.service;

import com.portfolio_management.portfolio.investments.crypto.client.FinnhubClient;
import com.portfolio_management.portfolio.investments.crypto.Entity.Asset;
import com.portfolio_management.portfolio.investments.crypto.dto.CryptoRequestDTO;
import com.portfolio_management.portfolio.investments.crypto.dto.CryptoPriceResponseDTO;
import com.portfolio_management.portfolio.investments.crypto.dto.CryptoResponseDTO;
import com.portfolio_management.portfolio.investments.crypto.Entity.Crypto;
import com.portfolio_management.portfolio.investments.crypto.Entity.Transaction;
import com.portfolio_management.portfolio.investments.crypto.Entity.Portfolio;
import com.portfolio_management.portfolio.investments.crypto.exception.CryptoNotFoundException;
import com.portfolio_management.portfolio.investments.crypto.repository.AssetRepository;
import com.portfolio_management.portfolio.investments.crypto.repository.CryptoRepository;
import com.portfolio_management.portfolio.investments.crypto.repository.PortfolioRepository;
import com.portfolio_management.portfolio.investments.crypto.repository.TransactionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
public class FinnhubCryptoService implements CryptoService {

    private final CryptoRepository cryptoRepository;
    private final AssetRepository assetRepository;
    private final TransactionRepository transactionRepository;
    private final PortfolioRepository portfolioRepository;
    private final FinnhubClient finnhubClient;

    public FinnhubCryptoService(
            CryptoRepository cryptoRepository,
            AssetRepository assetRepository,
            TransactionRepository transactionRepository,
            PortfolioRepository portfolioRepository,
            FinnhubClient finnhubClient
    ) {
        this.cryptoRepository = cryptoRepository;
        this.assetRepository = assetRepository;
        this.transactionRepository = transactionRepository;
        this.portfolioRepository = portfolioRepository;
        this.finnhubClient = finnhubClient;
    }

    @Override
    public List<CryptoResponseDTO> getAllCryptos() {
        log.info("Fetching all cryptocurrencies");
        List<CryptoResponseDTO> cryptos = new ArrayList<>();
        cryptoRepository.findAll().forEach(crypto -> {
            refreshPriceAndMetrics(crypto, false);
            cryptos.add(convertToDTO(crypto));
        });
        return cryptos;
    }

    @Override
    public Optional<CryptoResponseDTO> getCryptoById(Long id) {
        log.info("Fetching cryptocurrency with ID: {}", id);
        return cryptoRepository.findById(id)
                .map(crypto -> refreshPriceAndMetrics(crypto, false))
                .map(this::convertToDTO);
    }

    @Override
    public Optional<CryptoResponseDTO> getCryptoBySymbol(String symbol) {
        log.info("Fetching cryptocurrency with symbol: {}", symbol);
        return assetRepository.findBySymbol(symbol.toUpperCase())
                .flatMap(asset -> cryptoRepository.findByAssetId(asset.getAssetId()))
                .map(crypto -> refreshPriceAndMetrics(crypto, false))
                .map(this::convertToDTO);
    }

    @Override
    public CryptoResponseDTO saveCrypto(CryptoRequestDTO cryptoRequestDTO) {
        log.info("Saving cryptocurrency: {}", cryptoRequestDTO.getSymbol());

        validateTradeRequest(cryptoRequestDTO);
        Long portfolioId = resolvePortfolioId(cryptoRequestDTO);

        String normalizedSymbol = cryptoRequestDTO.getSymbol().toUpperCase();
        Asset asset = resolveOrCreateAsset(normalizedSymbol, cryptoRequestDTO.getName(), portfolioId);
        Optional<Crypto> existingCrypto = cryptoRepository.findByAssetId(asset.getAssetId());

        Crypto crypto = existingCrypto.orElseGet(Crypto::new);
        crypto.setAssetId(asset.getAssetId());
        if (crypto.getQuantity() == null) {
            crypto.setQuantity(BigDecimal.ZERO);
        }
        if (crypto.getBuyPrice() == null) {
            crypto.setBuyPrice(BigDecimal.ZERO);
        }
        // If live price fetch fails, keep request price (if provided) instead of failing create.
        if (cryptoRequestDTO.getCurrentPrice() != null) {
            crypto.setCurrentPrice(cryptoRequestDTO.getCurrentPrice());
        }
        refreshPriceAndMetrics(crypto, false);

        Crypto savedCrypto = cryptoRepository.save(crypto);
        Crypto updatedCrypto = syncRelatedTables(savedCrypto, cryptoRequestDTO, portfolioId);
        return convertToDTO(updatedCrypto);
    }

    @Override
    public void deleteCrypto(Long id) {
        log.info("Deleting cryptocurrency with ID: {}", id);
        if (!cryptoRepository.existsById(id)) {
            throw new CryptoNotFoundException("Cryptocurrency not found with ID: " + id);
        }
        cryptoRepository.deleteById(id);
    }

    @Override
    public CryptoResponseDTO updateCryptoPrice(String symbol) {
        log.info("Updating price for cryptocurrency: {}", symbol);
        
        try {
            String normalizedSymbol = symbol.toUpperCase();
            // Fetch real-time price from Finnhub API
            CryptoPriceResponseDTO priceData = finnhubClient.getCryptoQuote(normalizedSymbol);
            
            Long defaultPortfolioId = resolveDefaultPortfolioId();
            Asset asset = assetRepository.findBySymbol(normalizedSymbol)
                    .orElseGet(() -> assetRepository.save(new Asset(
                            null,
                            defaultPortfolioId,
                            normalizedSymbol,
                            priceData != null && priceData.getDisplayName() != null ? priceData.getDisplayName() : normalizedSymbol,
                            "CRYPTO",
                            "USD",
                            null
                    )));

            // Find existing crypto or create new one
            Optional<Crypto> existingCrypto = cryptoRepository.findByAssetId(asset.getAssetId());
            
            Crypto crypto;
            if (existingCrypto.isPresent()) {
                crypto = existingCrypto.get();
                log.info("Updating existing cryptocurrency: {}", normalizedSymbol);
            } else {
                crypto = new Crypto();
                crypto.setAssetId(asset.getAssetId());
                crypto.setQuantity(java.math.BigDecimal.ZERO);
                crypto.setBuyPrice(java.math.BigDecimal.ZERO);
                log.info("Creating new cryptocurrency entry: {}", normalizedSymbol);
            }

            // Update price information
            if (priceData == null || priceData.getCurrentPrice() == null) {
                throw new RuntimeException("Finnhub returned empty current price for symbol: " + normalizedSymbol);
            }
            crypto.setCurrentPrice(priceData.getCurrentPrice());
            recalculateHoldingMetrics(crypto);

            Crypto updatedCrypto = cryptoRepository.save(crypto);
            log.info("Successfully updated price for {}: {}", normalizedSymbol, updatedCrypto.getCurrentPrice());
            
            return convertToDTO(updatedCrypto);
        } catch (Exception e) {
            log.error("Error updating crypto price for symbol: {}", symbol, e);
            throw new RuntimeException("Failed to update crypto price for symbol: " + symbol.toUpperCase(), e);
        }
    }

    @Override
    public List<CryptoResponseDTO> getCryptosBySymbols(List<String> symbols) {
        log.info("Fetching {} cryptocurrencies by symbols", symbols.size());
        return symbols.stream()
                .flatMap(symbol -> assetRepository.findBySymbol(symbol.toUpperCase())
                        .flatMap(asset -> cryptoRepository.findByAssetId(asset.getAssetId()))
                        .stream())
                .map(crypto -> refreshPriceAndMetrics(crypto, false))
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    private Crypto refreshPriceAndMetrics(Crypto crypto, boolean failIfPriceUnavailable) {
        String symbol = resolveAssetSymbol(crypto.getAssetId());
        if ("UNKNOWN".equals(symbol)) {
            if (failIfPriceUnavailable) {
                throw new RuntimeException("Cannot refresh price because asset symbol is unknown for crypto id: " + crypto.getCryptoId());
            }
            log.warn("Skipping Finnhub refresh because asset symbol is unknown for crypto id: {}", crypto.getCryptoId());
            recalculateHoldingMetrics(crypto);
            return crypto.getCryptoId() != null ? cryptoRepository.save(crypto) : crypto;
        }
        try {
            CryptoPriceResponseDTO priceData = finnhubClient.getCryptoQuote(symbol);
            if (priceData == null || priceData.getCurrentPrice() == null) {
                if (failIfPriceUnavailable) {
                    throw new RuntimeException("Finnhub returned empty current price for symbol: " + symbol);
                }
                log.warn("Using stored current price for {} because Finnhub returned no price", symbol);
            } else {
                crypto.setCurrentPrice(priceData.getCurrentPrice());
            }
        } catch (Exception ex) {
            if (failIfPriceUnavailable) {
                throw ex;
            }
            log.warn("Using stored current price for {} due to Finnhub fetch failure: {}", symbol, ex.getMessage());
        }

        recalculateHoldingMetrics(crypto);

        if (crypto.getCryptoId() != null) {
            return cryptoRepository.save(crypto);
        }
        return crypto;
    }

    private Crypto syncRelatedTables(Crypto savedCrypto, CryptoRequestDTO request, Long portfolioId) {
        String txType = normalizeTransactionType(request.getTransactionType());
        Crypto updatedCrypto = applyTradeToCrypto(savedCrypto, request.getQuantity(), request.getBuyPrice(), txType);
        insertTransaction(updatedCrypto, portfolioId, request, txType);
        return updatedCrypto;
    }

    private Long resolvePortfolioId(CryptoRequestDTO request) {
        if (request.getPortfolioId() != null) {
            if (!portfolioRepository.existsById(request.getPortfolioId())) {
                throw new RuntimeException("Portfolio not found with ID: " + request.getPortfolioId());
            }
            return request.getPortfolioId();
        }

        // Auto-link to a default portfolio so holdings/transactions are always recorded.
        Optional<Portfolio> defaultPortfolio = portfolioRepository.findByPortfolioName("Default Portfolio");
        if (defaultPortfolio.isPresent()) {
            return defaultPortfolio.get().getPortfolioId();
        }

        Portfolio portfolio = new Portfolio();
        portfolio.setPortfolioName("Default Portfolio");
        portfolio.setDescription("Auto-created portfolio for crypto transactions");
        portfolio.setCashBalance(BigDecimal.ZERO);
        Portfolio savedPortfolio = portfolioRepository.save(portfolio);
        return savedPortfolio.getPortfolioId();
    }

    private Long resolveDefaultPortfolioId() {
        Optional<Portfolio> defaultPortfolio = portfolioRepository.findByPortfolioName("Default Portfolio");
        if (defaultPortfolio.isPresent()) {
            return defaultPortfolio.get().getPortfolioId();
        }

        Portfolio portfolio = new Portfolio();
        portfolio.setPortfolioName("Default Portfolio");
        portfolio.setDescription("Auto-created portfolio for crypto transactions");
        portfolio.setCashBalance(BigDecimal.ZERO);
        return portfolioRepository.save(portfolio).getPortfolioId();
    }

    private Crypto applyTradeToCrypto(Crypto crypto, BigDecimal quantity, BigDecimal buyPrice, String txType) {
        BigDecimal tradeQuantity = quantity != null ? quantity : BigDecimal.ZERO;
        BigDecimal tradeBuyPrice = buyPrice != null ? buyPrice : BigDecimal.ZERO;
        BigDecimal existingQuantity = crypto.getQuantity() != null ? crypto.getQuantity() : BigDecimal.ZERO;
        BigDecimal existingBuyPrice = crypto.getBuyPrice() != null ? crypto.getBuyPrice() : BigDecimal.ZERO;

        if ("BUY".equals(txType)) {
            BigDecimal totalQuantity = existingQuantity.add(tradeQuantity);
            BigDecimal existingCost = existingQuantity.multiply(existingBuyPrice);
            BigDecimal tradeCost = tradeQuantity.multiply(tradeBuyPrice);
            BigDecimal averageBuyPrice = totalQuantity.compareTo(BigDecimal.ZERO) > 0
                    ? existingCost.add(tradeCost).divide(totalQuantity, 8, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            crypto.setQuantity(totalQuantity);
            crypto.setBuyPrice(averageBuyPrice);
        } else {
            BigDecimal updatedQuantity = existingQuantity.subtract(tradeQuantity);
            if (updatedQuantity.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Sell quantity exceeds current holding for symbol: " + resolveAssetSymbol(crypto.getAssetId()));
            }
            crypto.setQuantity(updatedQuantity);
            if (updatedQuantity.compareTo(BigDecimal.ZERO) == 0) {
                crypto.setBuyPrice(BigDecimal.ZERO);
            }
        }

        recalculateHoldingMetrics(crypto);
        return cryptoRepository.save(crypto);
    }

    private void insertTransaction(Crypto savedCrypto, Long portfolioId, CryptoRequestDTO request, String txType) {
        BigDecimal txQuantity = request.getQuantity() != null ? request.getQuantity() : BigDecimal.ZERO;

        Transaction transaction = new Transaction();
        transaction.setPortfolioId(portfolioId);
        transaction.setAssetId(savedCrypto.getAssetId());
        transaction.setTransactionType(txType);
        transaction.setQuantity(txQuantity);
        transaction.setTransactionPrice(savedCrypto.getCurrentPrice());
        transaction.setTransactionDate(LocalDateTime.now());

        transactionRepository.save(transaction);
    }

    private String normalizeTransactionType(String transactionType) {
        String txType = transactionType != null && !transactionType.isBlank()
                ? transactionType.toUpperCase()
                : "BUY";
        if (!"BUY".equals(txType) && !"SELL".equals(txType)) {
            throw new IllegalArgumentException("transactionType must be BUY or SELL");
        }
        return txType;
    }

    private void validateTradeRequest(CryptoRequestDTO request) {
        if (request.getSymbol() == null || request.getSymbol().isBlank()) {
            throw new IllegalArgumentException("symbol is required");
        }
        if (request.getQuantity() == null || request.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("quantity must be greater than 0");
        }
        if ("BUY".equals(normalizeTransactionType(request.getTransactionType()))
                && (request.getBuyPrice() == null || request.getBuyPrice().compareTo(BigDecimal.ZERO) < 0)) {
            throw new IllegalArgumentException("buyPrice must be provided for BUY transactions");
        }
    }

    private Asset resolveOrCreateAsset(String symbol, String requestedName, Long portfolioId) {
        String normalizedSymbol = symbol.toUpperCase();
        String normalizedName = requestedName != null && !requestedName.isBlank()
                ? requestedName.trim()
                : normalizedSymbol;

        Asset asset = assetRepository.findBySymbol(normalizedSymbol)
                .orElseGet(() -> assetRepository.save(new Asset(
                        null,
                        portfolioId,
                        normalizedSymbol,
                        normalizedName,
                        "CRYPTO",
                        "USD",
                        LocalDateTime.now()
                )));

        if (asset.getPortfolioId() == null) {
            asset.setPortfolioId(portfolioId);
            asset = assetRepository.save(asset);
        }

        if (!normalizedName.equals(asset.getName())) {
            asset.setName(normalizedName);
            asset = assetRepository.save(asset);
        }

        if (asset.getAssetType() == null || asset.getAssetType().isBlank()) {
            asset.setAssetType("CRYPTO");
            asset = assetRepository.save(asset);
        }

        if (asset.getCurrency() == null || asset.getCurrency().isBlank()) {
            asset.setCurrency("USD");
            asset = assetRepository.save(asset);
        }

        return asset;
    }

    private String resolveAssetSymbol(Long assetId) {
        if (assetId == null) {
            return "UNKNOWN";
        }
        return assetRepository.findById(assetId)
                .map(Asset::getSymbol)
                .orElse("UNKNOWN");
    }

    private Asset resolveAsset(Long assetId) {
        if (assetId == null) {
            return new Asset(null, null, "UNKNOWN", "UNKNOWN", "UNKNOWN", "USD", null);
        }
        return assetRepository.findById(assetId)
                .orElse(new Asset(assetId, null, "UNKNOWN", "UNKNOWN", "UNKNOWN", "USD", null));
    }

    /**
     * Convert Crypto entity to CryptoResponseDTO
     */
    private CryptoResponseDTO convertToDTO(Crypto crypto) {
        Asset asset = resolveAsset(crypto.getAssetId());
        return new CryptoResponseDTO(
                crypto.getCryptoId(),
                asset.getSymbol(),
                asset.getName(),
                crypto.getQuantity(),
                crypto.getBuyPrice(),
                crypto.getCurrentPrice(),
                crypto.getInvestedAmount(),
                crypto.getCurrentValue(),
                crypto.getProfitLoss()
        );
    }

    private void recalculateHoldingMetrics(Crypto crypto) {
        if (crypto.getQuantity() == null) {
            crypto.setQuantity(java.math.BigDecimal.ZERO);
        }
        if (crypto.getBuyPrice() == null) {
            crypto.setBuyPrice(java.math.BigDecimal.ZERO);
        }
        if (crypto.getCurrentPrice() == null) {
            crypto.setCurrentPrice(java.math.BigDecimal.ZERO);
        }

        java.math.BigDecimal invested = crypto.getQuantity().multiply(crypto.getBuyPrice()).setScale(2, RoundingMode.HALF_UP);
        java.math.BigDecimal current = crypto.getQuantity().multiply(crypto.getCurrentPrice()).setScale(2, RoundingMode.HALF_UP);
        java.math.BigDecimal profit = current.subtract(invested).setScale(2, RoundingMode.HALF_UP);

        crypto.setInvestedAmount(invested);
        crypto.setCurrentValue(current);
        crypto.setProfitLoss(profit);
    }
}
