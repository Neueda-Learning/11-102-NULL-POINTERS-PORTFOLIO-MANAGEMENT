USE portfolio;
SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS transaction_history;
DROP TABLE IF EXISTS crypto_holdings;
DROP TABLE IF EXISTS crypto;
DROP TABLE IF EXISTS asset;
SET FOREIGN_KEY_CHECKS = 1;

-- Portfolio Table (common to all assets)
CREATE TABLE IF NOT EXISTS portfolio (
    portfolio_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    portfolio_name VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    cash_balance DECIMAL(15,2) DEFAULT 0.00,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Asset Table (shared identity for all assets)
CREATE TABLE asset (
    asset_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    symbol VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    asset_type VARCHAR(20) NOT NULL DEFAULT 'CRYPTO',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Crypto Table (crypto-specific metrics linked to asset)
CREATE TABLE crypto (
    crypto_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    asset_id BIGINT NOT NULL UNIQUE,
    quantity DECIMAL(15,8) DEFAULT 0.00000000,
    buy_price DECIMAL(15,2) DEFAULT 0.00,
    current_price DECIMAL(15,8) NOT NULL,
    invested_amount DECIMAL(15,2) DEFAULT 0.00,
    current_value DECIMAL(15,2) DEFAULT 0.00,
    profit_loss DECIMAL(15,2) DEFAULT 0.00,
    CONSTRAINT fk_crypto_asset FOREIGN KEY (asset_id) REFERENCES asset(asset_id) ON DELETE CASCADE
);

INSERT INTO asset (
    asset_id,
    symbol,
    name,
    asset_type
)
VALUES
    (1, 'BTCUSD', 'Bitcoin', 'CRYPTO'),
    (2, 'ETHUSD', 'Ethereum', 'CRYPTO'),
    (3, 'BNBUSD', 'BNB', 'CRYPTO'),
    (4, 'XRPUSD', 'XRP', 'CRYPTO'),
    (5, 'SOLUSD', 'Solana', 'CRYPTO'),
    (6, 'ADAUSD', 'Cardano', 'CRYPTO'),
    (7, 'DOGEUSD', 'Dogecoin', 'CRYPTO'),
    (8, 'TRXUSD', 'TRON', 'CRYPTO'),
    (9, 'AVAXUSD', 'Avalanche', 'CRYPTO'),
    (10, 'MATICUSD', 'Polygon', 'CRYPTO');

INSERT INTO crypto (
    asset_id,
    quantity,
    buy_price,
    current_price,
    invested_amount,
    current_value,
    profit_loss
)
VALUES
    (1, 0.05000000, 55000.00, 65000.00, 2750.00, 3250.00, 500.00),
    (2, 0.00000000, 0.00, 3200.00, 0.00, 0.00, 0.00),
    (3, 0.00000000, 0.00, 580.00, 0.00, 0.00, 0.00),
    (4, 0.00000000, 0.00, 0.62, 0.00, 0.00, 0.00),
    (5, 0.00000000, 0.00, 175.00, 0.00, 0.00, 0.00),
    (6, 0.00000000, 0.00, 0.45, 0.00, 0.00, 0.00),
    (7, 0.00000000, 0.00, 0.12, 0.00, 0.00, 0.00),
    (8, 0.00000000, 0.00, 0.13, 0.00, 0.00, 0.00),
    (9, 0.00000000, 0.00, 28.00, 0.00, 0.00, 0.00),
    (10, 0.00000000, 0.00, 0.72, 0.00, 0.00, 0.00);

-- Crypto Holdings (user's crypto positions)
CREATE TABLE IF NOT EXISTS crypto_holdings (
    holding_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    portfolio_id BIGINT NOT NULL,
    crypto_id BIGINT NOT NULL,
    quantity DECIMAL(15,8) NOT NULL,
    purchase_price DECIMAL(15,8) NOT NULL,
    purchase_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (portfolio_id) REFERENCES portfolio(portfolio_id) ON DELETE CASCADE,
    FOREIGN KEY (crypto_id) REFERENCES crypto(crypto_id) ON DELETE CASCADE
);

-- Transaction History (common table uses asset_id, not crypto_id)
CREATE TABLE IF NOT EXISTS transaction_history (
    transaction_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    portfolio_id BIGINT NOT NULL,
    asset_id BIGINT NOT NULL,
    transaction_type VARCHAR(20) NOT NULL CHECK (transaction_type IN ('BUY', 'SELL')),
    quantity DECIMAL(15,8) NOT NULL,
    transaction_price DECIMAL(15,8) NOT NULL,
    transaction_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (portfolio_id) REFERENCES portfolio(portfolio_id) ON DELETE CASCADE,
    FOREIGN KEY (asset_id) REFERENCES asset(asset_id) ON DELETE CASCADE
);

