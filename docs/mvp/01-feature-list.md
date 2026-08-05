# MVP Feature List and Implementation Status

## Status Legend
- Implemented: End-to-end behavior is available in app UI and backend APIs.
- Partial: Core capability exists, but missing one or more key MVP expectations.
- Not Implemented: Feature is not available yet beyond placeholders/docs.

## Features (Original + Additional Implemented)

| # | Feature Name | Description | Priority | Current Status | Evidence in Code |
|---|---|---|---|---|---|
| 1 | Portfolio Creation and Management | Create, read, update, delete portfolio records and maintain portfolio metadata (name, description, cash balance). | Must Have | Partial | CRUD APIs in `src/main/java/com/portfolio_management/portfolio/investments/crypto/controllers/PortfolioController.java`; main UI uses auto-created default portfolio from `src/main/java/com/portfolio_management/portfolio/portfolio/service/PortfolioService.java` |
| 2 | Asset Management (CRUD) | Add, view, update, and remove holdings across Stocks, Bonds, and Crypto. | Must Have | Partial | Add/sell in `src/main/java/com/portfolio_management/portfolio/portfolio/service/PortfolioService.java`; stock buy/sell APIs in `src/main/java/com/portfolio_management/portfolio/investments/stock/controller/StockController.java`; no true edit operation yet |
| 3 | Live Market Price Integration | Fetch live or near-live prices for market assets to support valuation updates. | Must Have | Partial | Stock REST + websocket in `src/main/java/com/portfolio_management/portfolio/investments/stock/service/StockMarketService.java` and `src/main/java/com/portfolio_management/portfolio/investments/stock/websocket/FinnhubWebSocketClient.java`; crypto refresh exists; bonds are fixed/dummy |
| 4 | Portfolio Valuation (P/L and % Returns) | Calculate portfolio value, invested amount, profit/loss, and return percentages. | Must Have | Implemented | Return math and summary in `src/main/java/com/portfolio_management/portfolio/portfolio/service/PortfolioService.java` |
| 5 | Portfolio Dashboard | Consolidated KPI screen for value, returns, and allocation. | Must Have | Implemented | Dashboard sections/cards in `src/main/resources/static/index.html` and render logic in `src/main/resources/static/js/app.js` |
| 6 | Portfolio Performance Visualization (Charts) | Visualize allocation and performance trends in charts. | Must Have | Partial | Charts in `src/main/resources/static/js/app.js`; performance is transaction-flow based, not mark-to-market NAV history |
| 7 | Alerts and Warnings | Automatically generate warnings/alerts for key portfolio events and risks. | Good to Have | Not Implemented | No backend rules engine or alerts UI found |
| 8 | Transaction History | Record and view historical buy/sell transactions. | Must Have | Implemented | API + query in `src/main/java/com/portfolio_management/portfolio/portfolio/service/PortfolioService.java`; transactions table in `src/main/resources/static/js/app.js` |
| 9 | AI Analysis of Owned Stocks Only | AI insights limited to currently owned stocks. | Future | Not Implemented | No AI analysis endpoints/services found in project code |
| 10 | Light/Dark Theme | Toggle and persist theme preference. | Good to Have | Implemented | Theme persistence/toggle in `src/main/resources/static/js/theme.js` |
| 11 | Marketplace by Asset Category | Marketplace filter with Stocks, Bonds, and Crypto categories. | Must Have | Implemented | Marketplace section and category switch in `src/main/resources/static/index.html` and `src/main/resources/static/js/app.js` |
| 12 | Bonds Marketplace with Dummy Catalog | Predefined bond offerings from banks/companies for demo MVP usage. | Good to Have | Implemented | Dummy bond dataset and table in `src/main/resources/static/js/app.js` (`DUMMY_BOND_MARKET`, `buildMarketplaceBondsTable`) |
| 13 | Buy from Marketplace via Add Asset Prefill | Buying stock/bond from marketplace opens Add Asset modal with details prefilled. | Must Have | Implemented | `buyFromMarketplace` and `buyBondFromMarketplace` in `src/main/resources/static/js/app.js` |
| 14 | Stock Insights (Transactions + Performance Modal) | Open stock-level modal to view transaction history and 10-day performance chart. | Good to Have | Implemented | `showStockTransactions`, `showStockPerformance`, and modal in `src/main/resources/static/js/app.js` and `src/main/resources/static/index.html` |
| 15 | Stock Symbol Auto-Fill in Add Form | Auto-populate stock details from symbol lookup when adding stock. | Good to Have | Implemented | `initStockAutoFill` and `autoFillStockFields` in `src/main/resources/static/js/app.js` |
| 16 | Holdings Filter and Type-Specific Tables | Holdings can be filtered by All/Stock/Bond/Crypto and rendered with type-specific columns. | Must Have | Implemented | `loadHoldings` + table builders in `src/main/resources/static/js/app.js` |
| 17 | Bond Accrued P/L (Current Date Based) | Bond holdings show accrued interest to current date, not full maturity-only profit. | Must Have | Implemented | Bond SQL calculation in `src/main/java/com/portfolio_management/portfolio/portfolio/service/PortfolioService.java` |
| 18 | Settings Page Action Panel (Demo) | Settings page includes profile/preferences/security/account action controls with demo responses. | Future | Partial | Settings UI + demo handlers in `src/main/resources/static/index.html` and `src/main/resources/static/js/app.js`; not backed by persistent APIs |

## Suggested MVP Scope Baseline

### Must Have (for MVP sign-off)
1. Portfolio Creation and Management (single usable portfolio flow minimum)
2. Asset Management CRUD (close Edit gap)
3. Live Market Price Integration (stocks at minimum)
4. Portfolio Valuation (P/L and returns)
5. Portfolio Dashboard
6. Holdings Filter and Type-Specific Tables
7. Transaction History
8. Marketplace by Asset Category
9. Buy from Marketplace via Add Asset Prefill

### Good to Have
1. Portfolio Performance Visualization (full historical valuation enhancement pending)
2. Bonds Marketplace with Dummy Catalog
3. Stock Insights Modal
4. Stock Symbol Auto-Fill
5. Light/Dark Theme

### Future
1. Alerts and Warnings engine
2. AI Analysis of Owned Stocks Only
3. Settings page actions with real backend persistence

