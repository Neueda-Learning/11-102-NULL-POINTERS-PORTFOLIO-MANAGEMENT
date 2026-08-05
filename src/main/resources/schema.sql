use portfolio;
CREATE TABLE IF NOT EXISTS portfolio (
    portfolio_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    portfolio_name VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    cash_balance DECIMAL(15,2) DEFAULT 0.00,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS asset (

    asset_id BIGINT AUTO_INCREMENT PRIMARY KEY,

    portfolio_id BIGINT NOT NULL,

    asset_type ENUM('STOCK','CRYPTO','BOND','CASH') NOT NULL,

    asset_name VARCHAR(100) NOT NULL,
    symbol VARCHAR(20) NOT NULL UNIQUE,
    currency VARCHAR(10)  DEFAULT 'USD' ,


    FOREIGN KEY (portfolio_id)
        REFERENCES portfolio(portfolio_id)
        ON DELETE CASCADE
);
CREATE TABLE IF NOT EXISTS transaction_history (

    transaction_id BIGINT AUTO_INCREMENT PRIMARY KEY,

    portfolio_id BIGINT NOT NULL,

    asset_id BIGINT NULL,

    transaction_type ENUM('BUY','SELL') NOT NULL,

    quantity DECIMAL(15,4) NOT NULL,

    transaction_price DECIMAL(15,2) NOT NULL,

    transaction_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (portfolio_id)
        REFERENCES portfolio(portfolio_id)
        ON DELETE CASCADE,

    FOREIGN KEY (asset_id)
        REFERENCES asset(asset_id)
        ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS bonds (
    asset_id BIGINT PRIMARY KEY,
    issuer VARCHAR(120) NOT NULL,
    interest_rate DECIMAL(8,4) NOT NULL,
    amount_invested DECIMAL(15,2) NOT NULL,
    start_date DATE NOT NULL,
    tenure_months INT NOT NULL,
    maturity_date DATE NOT NULL,
    FOREIGN KEY (asset_id)
        REFERENCES asset(asset_id)
        ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS stocks (
    asset_id BIGINT PRIMARY KEY,
    exchange VARCHAR(60),
    sector VARCHAR(60),
    FOREIGN KEY (asset_id)
        REFERENCES asset(asset_id)
        ON DELETE CASCADE
);
