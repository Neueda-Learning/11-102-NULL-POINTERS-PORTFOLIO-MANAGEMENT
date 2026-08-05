package com.portfolio_management.portfolio.investments.bond.service;

import com.portfolio_management.portfolio.investments.asset.model.Asset;
import com.portfolio_management.portfolio.investments.asset.model.AssetType;
import com.portfolio_management.portfolio.investments.asset.repository.AssetRepository;
import com.portfolio_management.portfolio.investments.bond.dto.BondRequestDTO;
import com.portfolio_management.portfolio.investments.bond.dto.BondResponseDTO;
import com.portfolio_management.portfolio.investments.bond.model.Bond;
import com.portfolio_management.portfolio.investments.bond.repository.BondRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BondService {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final int MONEY_SCALE = 2;
    private static final int RATE_SCALE = 4;
    private static final String DEFAULT_CURRENCY = "USD";

    private final BondRepository bondRepository;
    private final AssetRepository assetRepository;
    private final JdbcTemplate jdbcTemplate;

    public BondService(BondRepository bondRepository, AssetRepository assetRepository, JdbcTemplate jdbcTemplate) {
        this.bondRepository = bondRepository;
        this.assetRepository = assetRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public List<BondResponseDTO> getAllBonds() {
        return bondRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public BondResponseDTO createBond(BondRequestDTO request) {
        Optional<Bond> existingBond = bondRepository.findDuplicate(
                request.issuer(),
                request.interestRate(),
                request.amountInvested(),
                request.startDate(),
                request.tenureMonths()
        );
        if (existingBond.isPresent()) {
            return toResponse(existingBond.get());
        }

        Asset asset = new Asset();
        asset.setPortfolioId(resolveOrCreateDefaultPortfolioId());
        asset.setAssetType(AssetType.BOND);
        asset.setAssetName(request.issuer());
        asset.setSymbol(generateUniqueSymbol(request.issuer()));
        asset.setCurrency(DEFAULT_CURRENCY);
        Asset savedAsset = assetRepository.save(asset);

        Bond bond = new Bond();
        bond.setAsset(savedAsset);
        bond.setIssuer(request.issuer());
        bond.setInterestRate(request.interestRate());
        bond.setAmountInvested(request.amountInvested());
        bond.setStartDate(request.startDate());
        bond.setTenureMonths(request.tenureMonths());
        bond.setMaturityDate(request.startDate().plusMonths(request.tenureMonths()));

        Bond saved = bondRepository.save(bond);

        // Log transaction in transaction_history
        jdbcTemplate.update(
                "INSERT INTO transaction_history (portfolio_id, asset_id, transaction_type, quantity, transaction_price, transaction_date) VALUES (?, ?, ?, ?, ?, NOW())",
                savedAsset.getPortfolioId(),
                savedAsset.getAssetId(),
                "BUY",
                new BigDecimal("1.0000"),
                request.amountInvested()
        );

        return toResponse(saved);
    }

    @Transactional
    public boolean deleteBond(Long id) {
        Optional<Bond> bondOptional = bondRepository.findById(id);
        if (bondOptional.isEmpty()) {
            return false;
        }

        Bond bond = bondOptional.get();
        Asset asset = bond.getAsset();

        jdbcTemplate.update(
                "INSERT INTO transaction_history (portfolio_id, asset_id, transaction_type, quantity, transaction_price, transaction_date) VALUES (?, ?, ?, ?, ?, NOW())",
                asset.getPortfolioId(),
                asset.getAssetId(),
                "SELL",
                new BigDecimal("1.0000"),
                bond.getAmountInvested()
        );

        // Delete only the bond position. Asset metadata is intentionally retained for transaction history display.
        bondRepository.deleteById(id);
        return true;
    }

    private BondResponseDTO toResponse(Bond bond) {
        BigDecimal amountInvested = bond.getAmountInvested();
        BigDecimal totalInvestment = amountInvested
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        BigDecimal annualIncome = amountInvested
                .multiply(bond.getInterestRate().divide(ONE_HUNDRED, 8, RoundingMode.HALF_UP))
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        BigDecimal currentYield = bond.getInterestRate().setScale(RATE_SCALE, RoundingMode.HALF_UP);

        BigDecimal tenureYears = BigDecimal.valueOf(bond.getTenureMonths())
                .divide(BigDecimal.valueOf(12), 8, RoundingMode.HALF_UP);

        BigDecimal totalInterest = annualIncome
                .multiply(tenureYears)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        BigDecimal maturityAmount = totalInvestment
                .add(totalInterest)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        BigDecimal approxYTM = calculateApproxYtm(bond);

        return new BondResponseDTO(
                bond.getAssetId(),
                bond.getIssuer(),
                bond.getInterestRate(),
                amountInvested,
                bond.getStartDate(),
                bond.getTenureMonths(),
                bond.getMaturityDate(),
                totalInvestment,
                annualIncome,
                currentYield,
                approxYTM,
                maturityAmount
        );
    }

    private BigDecimal calculateApproxYtm(Bond bond) {
        if (bond.getTenureMonths() == null || bond.getTenureMonths() <= 0) {
            return BigDecimal.ZERO.setScale(RATE_SCALE, RoundingMode.HALF_UP);
        }

        BigDecimal principal = bond.getAmountInvested();
        if (principal == null || principal.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(RATE_SCALE, RoundingMode.HALF_UP);
        }

        BigDecimal years = BigDecimal.valueOf(bond.getTenureMonths())
                .divide(BigDecimal.valueOf(12), 8, RoundingMode.HALF_UP);
        double yearsDouble = years.doubleValue();
        if (yearsDouble <= 0d) {
            return BigDecimal.ZERO.setScale(RATE_SCALE, RoundingMode.HALF_UP);
        }

        BigDecimal annualIncome = principal
                .multiply(bond.getInterestRate().divide(ONE_HUNDRED, 8, RoundingMode.HALF_UP));
        BigDecimal maturityAmount = principal
                .add(annualIncome.multiply(years));

        double ytm = (Math.pow(maturityAmount.doubleValue() / principal.doubleValue(), 1d / yearsDouble) - 1d) * 100d;
        return BigDecimal.valueOf(ytm)
                .setScale(RATE_SCALE, RoundingMode.HALF_UP);
    }

    private Long resolveOrCreateDefaultPortfolioId() {
        List<Long> portfolioIds = jdbcTemplate.query(
                "SELECT portfolio_id FROM portfolio ORDER BY portfolio_id LIMIT 1",
                (rs, rowNum) -> rs.getLong(1)
        );
        if (!portfolioIds.isEmpty()) {
            return portfolioIds.get(0);
        }

        jdbcTemplate.update(
                "INSERT INTO portfolio (portfolio_name, description, cash_balance) VALUES (?, ?, ?)",
                "Default Portfolio",
                "Auto-created for first asset",
                BigDecimal.ZERO
        );

        return jdbcTemplate.queryForObject(
                "SELECT portfolio_id FROM portfolio ORDER BY portfolio_id DESC LIMIT 1",
                Long.class
        );
    }

    private String generateUniqueSymbol(String issuer) {
        String base = issuer.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
        if (base.isEmpty()) {
            base = "BOND";
        }
        if (base.length() > 14) {
            base = base.substring(0, 14);
        }

        String candidate = base + "BOND";
        if (candidate.length() > 20) {
            candidate = candidate.substring(0, 20);
        }
        if (!assetRepository.existsBySymbol(candidate)) {
            return candidate;
        }

        for (int i = 1; i < 10000; i++) {
            String suffix = String.valueOf(i);
            int maxBase = 20 - suffix.length();
            String pref = base.length() > maxBase ? base.substring(0, maxBase) : base;
            String next = pref + suffix;
            if (!assetRepository.existsBySymbol(next)) {
                return next;
            }
        }

        return "BOND" + System.currentTimeMillis();
    }

}
