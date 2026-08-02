use portfolio;
CREATE TABLE portfolio (
    portfolio_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    portfolio_name VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    cash_balance DECIMAL(15,2) DEFAULT 0.00,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE asset (
    asset_id BIGINT AUTO_INCREMENT PRIMARY KEY,

    portfolio_id BIGINT NOT NULL,

    asset_type ENUM('STOCK','CRYPTO','BOND','CASH') NOT NULL,

    symbol VARCHAR(20),

    asset_name VARCHAR(100) NOT NULL,

    quantity DECIMAL(15,4) NOT NULL,

    purchase_price DECIMAL(15,2) NOT NULL,

    purchase_date DATE,

    FOREIGN KEY (portfolio_id)
        REFERENCES portfolio(portfolio_id)
        ON DELETE CASCADE
);
CREATE TABLE transaction_history (

    transaction_id BIGINT AUTO_INCREMENT PRIMARY KEY,

    portfolio_id BIGINT NOT NULL,

    asset_id BIGINT NOT NULL,

    transaction_type ENUM('BUY','SELL') NOT NULL,

    quantity DECIMAL(15,4) NOT NULL,

    transaction_price DECIMAL(15,2) NOT NULL,

    transaction_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (portfolio_id)
        REFERENCES portfolio(portfolio_id)
        ON DELETE CASCADE,

    FOREIGN KEY (asset_id)
        REFERENCES asset(asset_id)
        ON DELETE CASCADE
);