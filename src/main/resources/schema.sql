-- Drop and recreate crypto table only
USE portfolio;
SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS crypto;
SET FOREIGN_KEY_CHECKS = 1;

-- Portfolio Table
CREATE TABLE IF NOT EXISTS portfolio (
    portfolio_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    portfolio_name VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    cash_balance DECIMAL(15,2) DEFAULT 0.00,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Crypto Table (only requested attributes)
CREATE TABLE crypto (
    crypto_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    symbol VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    quantity DECIMAL(15,8) DEFAULT 0.00000000,
    buy_price DECIMAL(15,2) DEFAULT 0.00,
    current_price DECIMAL(15,8) NOT NULL,
    invested_amount DECIMAL(15,2) DEFAULT 0.00,
    current_value DECIMAL(15,2) DEFAULT 0.00,
    profit_loss DECIMAL(15,2) DEFAULT 0.00
);


INSERT INTO crypto (
    symbol,
    name,
    quantity,
    buy_price,
    current_price,
    invested_amount,
    current_value,
    profit_loss
)
VALUES
    ('BTCUSD', 'Bitcoin', 0.05000000, 55000.00, 65000.00, 2750.00, 3250.00, 500.00),
    ('ETHUSD', 'Ethereum', 0.00000000, 0.00, 3200.00, 0.00, 0.00, 0.00),
    ('BNBUSD', 'BNB', 0.00000000, 0.00, 580.00, 0.00, 0.00, 0.00),
    ('XRPUSD', 'XRP', 0.00000000, 0.00, 0.62, 0.00, 0.00, 0.00),
    ('SOLUSD', 'Solana', 0.00000000, 0.00, 175.00, 0.00, 0.00, 0.00),
    ('ADAUSD', 'Cardano', 0.00000000, 0.00, 0.45, 0.00, 0.00, 0.00),
    ('DOGEUSD', 'Dogecoin', 0.00000000, 0.00, 0.12, 0.00, 0.00, 0.00),
    ('TRXUSD', 'TRON', 0.00000000, 0.00, 0.13, 0.00, 0.00, 0.00),
    ('AVAXUSD', 'Avalanche', 0.00000000, 0.00, 28.00, 0.00, 0.00, 0.00),
    ('MATICUSD', 'Polygon', 0.00000000, 0.00, 0.72, 0.00, 0.00, 0.00);

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

-- Transaction History
CREATE TABLE IF NOT EXISTS transaction_history (
    transaction_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    portfolio_id BIGINT NOT NULL,
    crypto_id BIGINT NOT NULL,
    transaction_type VARCHAR(20) NOT NULL CHECK (transaction_type IN ('BUY', 'SELL')),
    quantity DECIMAL(15,8) NOT NULL,
    transaction_price DECIMAL(15,8) NOT NULL,
    transaction_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (portfolio_id) REFERENCES portfolio(portfolio_id) ON DELETE CASCADE,
    FOREIGN KEY (crypto_id) REFERENCES crypto(crypto_id) ON DELETE CASCADE
);
