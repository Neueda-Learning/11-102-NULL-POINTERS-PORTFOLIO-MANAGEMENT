package com.portfolio_management.portfolio.portfolio.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

@Service
public class PortfolioService {

    private final JdbcTemplate jdbcTemplate;

    public PortfolioService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // ─── Summary ────────────────────────────────────────────────────────────
    public Map<String, Object> getSummary() {
        return getSummary(1L);
    }

    public Map<String, Object> getSummary(Long portfolioId) {
        Long effectivePortfolioId = resolveOrCreatePortfolioId(portfolioId);
        BigDecimal bondsInvested = orZero(jdbcTemplate.queryForObject(
                """
                        SELECT COALESCE(SUM(b.amount_invested),0)
                        FROM bonds b
                        JOIN asset a ON a.asset_id = b.asset_id
                        WHERE a.portfolio_id = ?
                        """, BigDecimal.class, effectivePortfolioId));
        BigDecimal bondsValue = orZero(jdbcTemplate.queryForObject(
                """
                        SELECT COALESCE(SUM(b.amount_invested + (b.amount_invested * b.interest_rate * b.tenure_months / 1200)),0)
                        FROM bonds b
                        JOIN asset a ON a.asset_id = b.asset_id
                        WHERE a.portfolio_id = ?
                        """, BigDecimal.class, effectivePortfolioId));

        BigDecimal stocksInvested = orZero(jdbcTemplate.queryForObject(
                """
                        SELECT COALESCE(SUM(s.quantity * s.purchase_price),0)
                        FROM stock s
                        JOIN asset a ON a.asset_id = s.asset_id
                        WHERE a.portfolio_id = ?
                        """, BigDecimal.class, effectivePortfolioId));
        BigDecimal stocksValue = orZero(jdbcTemplate.queryForObject(
                """
                        SELECT COALESCE(SUM(s.quantity * s.purchase_price),0)
                        FROM stock s
                        JOIN asset a ON a.asset_id = s.asset_id
                        WHERE a.portfolio_id = ?
                        """, BigDecimal.class, effectivePortfolioId));

        BigDecimal cryptoInvested = orZero(jdbcTemplate.queryForObject(
                """
                        SELECT COALESCE(SUM(c.invested_amount),0)
                        FROM crypto c
                        JOIN asset a ON a.asset_id = c.asset_id
                        WHERE a.portfolio_id = ?
                        """, BigDecimal.class, effectivePortfolioId));
        BigDecimal cryptoValue = orZero(jdbcTemplate.queryForObject(
                """
                        SELECT COALESCE(SUM(c.current_value),0)
                        FROM crypto c
                        JOIN asset a ON a.asset_id = c.asset_id
                        WHERE a.portfolio_id = ?
                        """, BigDecimal.class, effectivePortfolioId));
        BigDecimal availableCash = getPortfolioCashBalance(effectivePortfolioId);

        BigDecimal holdingsValue = bondsValue.add(stocksValue).add(cryptoValue);
        BigDecimal totalPortfolioValue = holdingsValue.add(availableCash);
        // Cost basis for currently open holdings only; avoids stale transaction effects.
        BigDecimal netInvested = bondsInvested.add(stocksInvested).add(cryptoInvested);
        BigDecimal totalReturns = holdingsValue.subtract(netInvested);
        BigDecimal totalReturnsPercent = netInvested.compareTo(BigDecimal.ZERO) > 0
                ? totalReturns.divide(netInvested, 6, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        BigDecimal safeTotal = totalPortfolioValue.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ONE : totalPortfolioValue;

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("totalPortfolioValue", totalPortfolioValue.setScale(2, RoundingMode.HALF_UP));
        r.put("totalInvested", netInvested.setScale(2, RoundingMode.HALF_UP));
        r.put("totalReturns", totalReturns.setScale(2, RoundingMode.HALF_UP));
        r.put("totalReturnsPercent", totalReturnsPercent.setScale(2, RoundingMode.HALF_UP));
        r.put("stocksValue", stocksValue.setScale(2, RoundingMode.HALF_UP));
        r.put("bondsValue", bondsValue.setScale(2, RoundingMode.HALF_UP));
        r.put("cryptoValue", cryptoValue.setScale(2, RoundingMode.HALF_UP));
        r.put("stocksPercent", pct(stocksValue, safeTotal));
        r.put("bondsPercent", pct(bondsValue, safeTotal));
        r.put("cryptoPercent", pct(cryptoValue, safeTotal));
        r.put("availableCash", availableCash.setScale(2, RoundingMode.HALF_UP));
        r.put("portfolioId", effectivePortfolioId);
        return r;
    }

    public Map<String, Object> getCashBalance(Long portfolioId) {
        Long effectivePortfolioId = resolveOrCreatePortfolioId(portfolioId);
        BigDecimal cash = getPortfolioCashBalance(effectivePortfolioId).setScale(2, RoundingMode.HALF_UP);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("portfolioId", effectivePortfolioId);
        result.put("availableCash", cash);
        return result;
    }

    // ─── Performance History ────────────────────────────────────────────────
    public Map<String, Object> getPerformanceHistory() {
        return getPerformanceHistory(1L);
    }

    public Map<String, Object> getPerformanceHistory(Long portfolioId) {
        Long effectivePortfolioId = resolveOrCreatePortfolioId(portfolioId);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT
                    DATE(transaction_date) AS txn_date,
                    SUM(CASE WHEN transaction_type='BUY'
                        THEN transaction_price*quantity
                        ELSE -(transaction_price*quantity) END) AS net_amount
                FROM transaction_history
                WHERE portfolio_id = ?
                GROUP BY DATE(transaction_date)
                ORDER BY txn_date ASC
                """, effectivePortfolioId);

        List<String> labels = new ArrayList<>();
        List<BigDecimal> values = new ArrayList<>();
        BigDecimal running = BigDecimal.ZERO;
        for (Map<String, Object> row : rows) {
            labels.add(String.valueOf(row.get("txn_date")));
            BigDecimal net = row.get("net_amount") != null
                    ? new BigDecimal(row.get("net_amount").toString()) : BigDecimal.ZERO;
            running = running.add(net);
            values.add(running.setScale(2, RoundingMode.HALF_UP));
        }
        if (labels.isEmpty()) {
            labels.add(LocalDate.now().toString());
            values.add(BigDecimal.ZERO);
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("labels", labels);
        r.put("values", values);
        return r;
    }

    // ─── Holdings ───────────────────────────────────────────────────────────
    public List<Map<String, Object>> getHoldings(String type) {
        return getHoldings(1L, type);
    }

    public List<Map<String, Object>> getHoldings(Long portfolioId, String type) {
        Long effectivePortfolioId = resolveOrCreatePortfolioId(portfolioId);
        List<Map<String, Object>> holdings = new ArrayList<>();
        String t = type != null ? type.toUpperCase() : "ALL";
        if ("ALL".equals(t) || "STOCK".equals(t))  holdings.addAll(stockHoldings(effectivePortfolioId));
        if ("ALL".equals(t) || "BOND".equals(t))   holdings.addAll(bondHoldings(effectivePortfolioId));
        if ("ALL".equals(t) || "CRYPTO".equals(t)) holdings.addAll(cryptoHoldings(effectivePortfolioId));
        return holdings;
    }

    private List<Map<String, Object>> stockHoldings(Long portfolioId) {
        return jdbcTemplate.queryForList("""
                SELECT a.asset_id, a.symbol, a.asset_name, a.currency,
                       s.quantity, s.purchase_price,
                       s.purchase_price AS current_price,
                       (s.quantity * s.purchase_price) AS market_value,
                       (s.quantity * s.purchase_price) AS cost_basis,
                       0 AS profit_loss,
                       'STOCK' AS asset_type,
                       s.purchase_date
                FROM asset a
                JOIN stock s ON a.asset_id = s.asset_id
                WHERE a.asset_type = 'STOCK'
                  AND a.portfolio_id = ?
                  AND s.quantity > 0
                """, portfolioId);
    }

    private List<Map<String, Object>> bondHoldings(Long portfolioId) {
        return jdbcTemplate.queryForList("""
            SELECT a.asset_id, 
                   a.symbol, 
                   a.asset_name,
                   b.issuer, 
                   b.interest_rate, 
                   b.amount_invested,
                   b.start_date, 
                   b.tenure_months, 
                   b.maturity_date,
                   
                   -- 1. Calculate accrued interest based on days passed since start_date
                   (
                     b.amount_invested * (b.interest_rate / 100) * 
                     (LEAST(DATEDIFF(CURDATE(), b.start_date), DATEDIFF(b.maturity_date, b.start_date)) / 365.0)
                   ) AS profit_loss,
                   
                   -- 2. Total Value = Principal + Accrued Interest
                   (
                     b.amount_invested + 
                     (
                       b.amount_invested * (b.interest_rate / 100) * 
                       (LEAST(DATEDIFF(CURDATE(), b.start_date), DATEDIFF(b.maturity_date, b.start_date)) / 365.0)
                     )
                   ) AS total_value,
                   
                   'BOND' AS asset_type
            FROM asset a
            JOIN bonds b ON a.asset_id = b.asset_id
            WHERE a.asset_type = 'BOND'
              AND a.portfolio_id = ?
              AND b.amount_invested > 0
            """, portfolioId);
    }

    private List<Map<String, Object>> cryptoHoldings(Long portfolioId) {
        return jdbcTemplate.queryForList("""
                SELECT a.asset_id, a.symbol, a.asset_name,
                       c.quantity, c.buy_price, c.current_price,
                       c.current_value, c.invested_amount, c.profit_loss,
                       'CRYPTO' AS asset_type
                FROM asset a
                JOIN crypto c ON a.asset_id = c.asset_id
                WHERE a.asset_type = 'CRYPTO'
                  AND a.portfolio_id = ?
                """, portfolioId);
    }

    // ─── Add Holding ─────────────────────────────────────────────────────────
    @Transactional
    public Map<String, Object> addHolding(Map<String, Object> request) {
        String type = ((String) request.get("type")).toUpperCase(Locale.ROOT);
        Long portfolioId = resolveOrCreatePortfolioId(parsePortfolioId(request.get("portfolioId")));
        String assetName = (String) request.getOrDefault("assetName",
                request.getOrDefault("issuer", request.getOrDefault("symbol", "ASSET")));
        String baseSymbol = String.valueOf(request.getOrDefault("symbol",
                assetName.replaceAll("[^A-Za-z0-9]", ""))).trim().toUpperCase(Locale.ROOT);
        String currency = (String) request.getOrDefault("currency", "USD");

        if ("STOCK".equals(type)) {
            return upsertStockHolding(request, portfolioId, assetName, baseSymbol, currency);
        }

        String symbol = uniqueSymbol(baseSymbol, type);

        jdbcTemplate.update(
                "INSERT INTO asset (portfolio_id, asset_type, asset_name, symbol, currency) VALUES (?,?,?,?,?)",
                portfolioId, type, assetName, symbol, currency);
        Long assetId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        BigDecimal quantity = BigDecimal.ONE;
        BigDecimal price = BigDecimal.ZERO;

        switch (type) {
            case "BOND" -> {
                BigDecimal amountInvested = bd(request.get("amountInvested"));
                requireSufficientCash(portfolioId, amountInvested, "Insufficient cash balance for bond purchase");
                BigDecimal interestRate = bd(request.get("interestRate"));
                String startDate = (String) request.getOrDefault("startDate", LocalDate.now().toString());
                int tenureMonths = Integer.parseInt(request.get("tenureMonths").toString());
                String maturityDate = LocalDate.parse(startDate).plusMonths(tenureMonths).toString();
                String issuer = (String) request.getOrDefault("issuer", assetName);
                jdbcTemplate.update(
                        "INSERT INTO bonds (asset_id, issuer, interest_rate, amount_invested, start_date, tenure_months, maturity_date) VALUES (?,?,?,?,?,?,?)",
                        assetId, issuer, interestRate, amountInvested, startDate, tenureMonths, maturityDate);
                adjustPortfolioCashBalance(portfolioId, amountInvested.negate());
                price = amountInvested;
            }
            case "CRYPTO" -> {
                quantity = bd(request.get("quantity"));
                BigDecimal buyPrice = bd(request.get("buyPrice"));
                BigDecimal currentPrice = bd(request.get("currentPrice"));
                BigDecimal invested = quantity.multiply(buyPrice);
                requireSufficientCash(portfolioId, invested, "Insufficient cash balance for crypto purchase");
                BigDecimal currentValue = quantity.multiply(currentPrice);
                BigDecimal pl = currentValue.subtract(invested);
                jdbcTemplate.update(
                        "INSERT INTO crypto (asset_id, quantity, buy_price, current_price, invested_amount, current_value, profit_loss) VALUES (?,?,?,?,?,?,?)",
                        assetId, quantity, buyPrice, currentPrice, invested, currentValue, pl);
                adjustPortfolioCashBalance(portfolioId, invested.negate());
                price = buyPrice;
            }
        }

        jdbcTemplate.update(
                "INSERT INTO transaction_history (portfolio_id, asset_id, transaction_type, quantity, transaction_price, transaction_date) VALUES (?,?,'BUY',?,?,NOW())",
                portfolioId, assetId, quantity, price);

        return Map.of("success", true, "assetId", assetId, "message", type + " added successfully");
    }

    @Transactional
    protected Map<String, Object> upsertStockHolding(
            Map<String, Object> request,
            Long portfolioId,
            String assetName,
            String baseSymbol,
            String currency) {
        String symbol = baseSymbol.isBlank() ? uniqueSymbol("STOCK", "STOCK") : baseSymbol;
        BigDecimal buyQty = bd(request.get("quantity"));
        BigDecimal buyPrice = bd(request.get("purchasePrice"));
        BigDecimal totalCost = buyQty.multiply(buyPrice);
        requireSufficientCash(portfolioId, totalCost, "Insufficient cash balance for stock purchase");
        String purchaseDate = (String) request.getOrDefault("purchaseDate", LocalDate.now().toString());

        List<Map<String, Object>> existing = jdbcTemplate.queryForList("""
                SELECT a.asset_id, s.quantity, s.purchase_price
                FROM asset a
                JOIN stock s ON a.asset_id = s.asset_id
                WHERE a.portfolio_id = ?
                  AND a.asset_type = 'STOCK'
                  AND UPPER(a.symbol) = ?
                LIMIT 1
                """, portfolioId, symbol);

        Long assetId;
        if (!existing.isEmpty()) {
            Map<String, Object> row = existing.get(0);
            assetId = ((Number) row.get("asset_id")).longValue();

            BigDecimal oldQty = bd(row.get("quantity"));
            BigDecimal oldPrice = bd(row.get("purchase_price"));
            BigDecimal newQty = oldQty.add(buyQty);

            BigDecimal newAvgPrice = buyPrice;
            if (newQty.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal weightedTotalCost = oldQty.multiply(oldPrice).add(buyQty.multiply(buyPrice));
                newAvgPrice = weightedTotalCost.divide(newQty, 2, RoundingMode.HALF_UP);
            }

            jdbcTemplate.update(
                    "UPDATE stock SET quantity=?, purchase_price=?, purchase_date=? WHERE asset_id=?",
                    newQty, newAvgPrice, purchaseDate, assetId);
            jdbcTemplate.update(
                    "UPDATE asset SET asset_name=?, currency=? WHERE asset_id=?",
                    assetName, currency, assetId);
        } else {
            boolean symbolTaken = Optional.ofNullable(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM asset WHERE UPPER(symbol)=?", Integer.class, symbol))
                    .orElse(0) > 0;
            if (symbolTaken) {
                symbol = uniqueSymbol(symbol, "STOCK");
            }

            jdbcTemplate.update(
                    "INSERT INTO asset (portfolio_id, asset_type, asset_name, symbol, currency) VALUES (?,?,?,?,?)",
                    portfolioId, "STOCK", assetName, symbol, currency);
            assetId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

            jdbcTemplate.update(
                    "INSERT INTO stock (asset_id, quantity, purchase_price, purchase_date) VALUES (?,?,?,?)",
                    assetId, buyQty, buyPrice, purchaseDate);
        }

        jdbcTemplate.update(
                "INSERT INTO transaction_history (portfolio_id, asset_id, transaction_type, quantity, transaction_price, transaction_date) VALUES (?,?,'BUY',?,?,NOW())",
                portfolioId, assetId, buyQty, buyPrice);
        adjustPortfolioCashBalance(portfolioId, totalCost.negate());

        return Map.of("success", true, "assetId", assetId, "message", "STOCK added successfully");
    }

    // ─── Sell Holding ────────────────────────────────────────────────────────
    @Transactional
    public void sellHolding(Long assetId, BigDecimal requestedQuantity) {
        sellHolding(1L, assetId, requestedQuantity);
    }

    @Transactional
    public void sellHolding(Long portfolioId, Long assetId, BigDecimal requestedQuantity) {
        Long effectivePortfolioId = resolveOrCreatePortfolioId(portfolioId);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM asset WHERE asset_id = ? AND portfolio_id = ?", assetId, effectivePortfolioId);
        if (rows.isEmpty()) throw new RuntimeException("Asset not found: " + assetId);

        Map<String, Object> asset = rows.get(0);
        String assetType = (String) asset.get("asset_type");
        Long ownerPortfolioId = ((Number) asset.get("portfolio_id")).longValue();

        BigDecimal quantity = BigDecimal.ONE;
        BigDecimal price = BigDecimal.ZERO;
        boolean deleteAsset = true;

        switch (assetType) {
            case "STOCK" -> {
                Map<String, Object> s = jdbcTemplate.queryForMap("SELECT * FROM stock WHERE asset_id=?", assetId);
                BigDecimal currentQty = bd(s.get("quantity"));
                BigDecimal sellQty = requestedQuantity == null ? currentQty : requestedQuantity;
                if (sellQty.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new RuntimeException("Sell quantity must be greater than 0");
                }
                if (sellQty.compareTo(currentQty) > 0) {
                    throw new RuntimeException("Sell quantity cannot exceed available stocks: " + currentQty.stripTrailingZeros().toPlainString());
                }

                quantity = sellQty;
                price = bd(s.get("purchase_price"));

                BigDecimal remaining = currentQty.subtract(sellQty);
                // Keep the asset row so historical transactions continue to resolve symbol/name.
                jdbcTemplate.update("UPDATE stock SET quantity=? WHERE asset_id=?", remaining, assetId);
                deleteAsset = false;
            }
            case "BOND" -> {
                Map<String, Object> b = jdbcTemplate.queryForMap("SELECT * FROM bonds WHERE asset_id=?", assetId);
                price = bd(b.get("amount_invested"));
                // Keep bond + asset rows so transaction history can still resolve symbol/name.
                jdbcTemplate.update("UPDATE bonds SET amount_invested = 0 WHERE asset_id=?", assetId);
                deleteAsset = false;
            }
            case "CRYPTO" -> {
                Map<String, Object> c = jdbcTemplate.queryForMap("SELECT * FROM crypto WHERE asset_id=?", assetId);
                quantity = bd(c.get("quantity"));
                price = bd(c.get("current_price"));
            }
        }

        jdbcTemplate.update(
                "INSERT INTO transaction_history (portfolio_id, asset_id, transaction_type, quantity, transaction_price, transaction_date) VALUES (?,?,'SELL',?,?,NOW())",
                ownerPortfolioId, assetId, quantity, price);

        adjustPortfolioCashBalance(ownerPortfolioId, quantity.multiply(price));

        if (deleteAsset) {
            jdbcTemplate.update("DELETE FROM asset WHERE asset_id=?", assetId);
        }
    }

    // ─── All Transactions ────────────────────────────────────────────────────
    public List<Map<String, Object>> getAllTransactions() {
        return getAllTransactions(1L);
    }

    public List<Map<String, Object>> getAllTransactions(Long portfolioId) {
        Long effectivePortfolioId = resolveOrCreatePortfolioId(portfolioId);
        return jdbcTemplate.queryForList("""
                SELECT
                    th.transaction_id,
                    th.portfolio_id,
                    th.asset_id,
                    COALESCE(a.symbol, 'DELETED') AS symbol,
                    COALESCE(a.asset_type, 'UNKNOWN') AS asset_type,
                    COALESCE(a.asset_name, 'Deleted Asset') AS asset_name,
                    th.transaction_type,
                    th.quantity,
                    th.transaction_price,
                    (th.quantity * th.transaction_price) AS total_amount,
                    th.transaction_date
                FROM transaction_history th
                LEFT JOIN asset a ON a.asset_id = th.asset_id
                WHERE th.portfolio_id = ?
                ORDER BY th.transaction_date DESC
                """, effectivePortfolioId);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────
    private Long resolveOrCreatePortfolioId(Long preferredId) {
        if (preferredId != null) {
            Integer exists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM portfolio WHERE portfolio_id = ?",
                    Integer.class,
                    preferredId
            );
            if (exists != null && exists > 0) {
                return preferredId;
            }
        }
        List<Long> ids = jdbcTemplate.query(
                "SELECT portfolio_id FROM portfolio ORDER BY portfolio_id LIMIT 1",
                (rs, rn) -> rs.getLong(1));
        if (!ids.isEmpty()) return ids.get(0);
        jdbcTemplate.update(
                "INSERT INTO portfolio (portfolio_name, description, cash_balance) VALUES (?,?,?)",
                "Default Portfolio", "Auto-created", BigDecimal.ZERO);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private void requireSufficientCash(Long portfolioId, BigDecimal required, String messagePrefix) {
        BigDecimal available = getPortfolioCashBalance(portfolioId);
        if (available.compareTo(required) < 0) {
            throw new IllegalArgumentException(messagePrefix + ". Required: " + required.setScale(2, RoundingMode.HALF_UP)
                    + ", available: " + available.setScale(2, RoundingMode.HALF_UP));
        }
    }

    private BigDecimal getPortfolioCashBalance(Long portfolioId) {
        return orZero(jdbcTemplate.queryForObject(
                "SELECT cash_balance FROM portfolio WHERE portfolio_id = ?",
                BigDecimal.class,
                portfolioId
        ));
    }

    private void adjustPortfolioCashBalance(Long portfolioId, BigDecimal delta) {
        jdbcTemplate.update(
                "UPDATE portfolio SET cash_balance = COALESCE(cash_balance,0) + ? WHERE portfolio_id = ?",
                delta,
                portfolioId
        );
    }

    private Long parsePortfolioId(Object raw) {
        if (raw == null) {
            return null;
        }
        return Long.valueOf(raw.toString());
    }

    private String uniqueSymbol(String base, String type) {
        String prefix = base.isEmpty() ? type : base;
        if (prefix.length() > 16) prefix = prefix.substring(0, 16);
        String candidate = prefix;
        int count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM asset WHERE symbol LIKE ?", Integer.class, prefix + "%");
        if (count > 0) candidate = prefix + (count + 1);
        if (candidate.length() > 20) candidate = candidate.substring(0, 20);
        return candidate;
    }

    private BigDecimal orZero(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    private BigDecimal bd(Object v) {
        return v == null ? BigDecimal.ZERO : new BigDecimal(v.toString());
    }

    private BigDecimal pct(BigDecimal part, BigDecimal total) {
        return part.divide(total, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }
}

