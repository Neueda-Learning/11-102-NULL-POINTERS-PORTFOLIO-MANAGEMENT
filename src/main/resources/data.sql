INSERT INTO portfolio (portfolio_id, portfolio_name, description, cash_balance)
SELECT 1, 'Default Portfolio', 'Seeded portfolio for stock module testing', 10000.00
WHERE NOT EXISTS (
    SELECT 1 FROM portfolio WHERE portfolio_id = 1
);

