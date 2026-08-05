# MVP Acceptance Criteria

## 1) Feature: Portfolio Creation and Management
### Acceptance Criteria
- User can create a portfolio with `portfolio_name`; system returns created portfolio ID.
- User can retrieve portfolio by ID and list all portfolios.
- User can update portfolio name, description, and cash balance.
- User can delete a portfolio and dependent assets follow configured data rules.
- At least one portfolio is available for investment workflows (auto-created default is acceptable for MVP).

## 2) Feature: Asset Management (CRUD)
### Acceptance Criteria
- User can add Stock, Bond, and Crypto holdings from UI forms.
- Required fields are validated before save (for example: quantity, price/rate, dates).
- User can view all active holdings and filter by asset type.
- User can sell/remove holdings and holding disappears from active list.
- Every add/sell action creates a transaction history entry.
- User can edit existing holdings (currently gap; required to close CRUD scope).

## 3) Feature: Live Market Price Integration
### Acceptance Criteria
- User can search stock by symbol and retrieve company details.
- Marketplace stock rows show current price and daily change.
- Stock holdings valuation uses latest available live/quote price.
- System handles temporary provider failures without crashing page render.
- Crypto price refresh endpoint updates current price/value for selected symbol.

## 4) Feature: Portfolio Valuation (P/L and % Returns)
### Acceptance Criteria
- Dashboard shows total portfolio value, invested amount, and total return amount.
- Dashboard shows total return percentage.
- Holdings display holding-level P/L values.
- Calculations use consistent formulas and non-null safe defaults.
- Selling/removing holdings is reflected in valuation outputs after refresh.

## 5) Feature: Portfolio Dashboard
### Acceptance Criteria
- Dashboard page loads summary cards with no manual API calls from user.
- KPI cards include total value, invested amount, returns, and asset-type values.
- Positive returns are visually distinguishable from negative returns.
- Dashboard updates after add/sell actions.

## 6) Feature: Performance Visualization (Charts)
### Acceptance Criteria
- Allocation chart renders Stocks/Bonds/Crypto values.
- Performance line chart renders time series values with labels.
- Charts re-render cleanly on refresh/navigation without duplicate canvas artifacts.
- Empty states are handled gracefully when no data exists.
- Performance definition is documented (current implementation is transaction-flow based).

## 7) Feature: Alerts and Warnings
### Acceptance Criteria
- System defines at least 3 rule-based alerts (example: drawdown threshold, concentration limit, stale price).
- Alerts are generated automatically when conditions are met.
- Alerts are visible in UI with severity levels.
- Alerts can be dismissed or marked read.
- Alert generation is auditable (timestamp and trigger reason).

## 8) Feature: Transaction History
### Acceptance Criteria
- Every BUY/SELL writes to transaction history with portfolio ID, asset ID, quantity, price, and timestamp.
- User can view transactions in reverse chronological order.
- Transaction table shows symbol, asset type, transaction type, quantity, price, and total amount.
- System supports filtering transaction history by portfolio and/or asset (API level).

## 9) Feature: AI Analysis of Owned Stocks Only
### Acceptance Criteria
- AI analysis endpoint accepts portfolio context and derives owned stock universe from holdings.
- AI output excludes symbols not currently owned (unless explicitly requested by user).
- AI response includes symbol-level rationale and confidence/quality notes.
- Analysis requests and outputs are logged for audit/debug.

## 10) Feature: Light/Dark Theme
### Acceptance Criteria
- User can toggle theme from navbar.
- Theme changes apply across all visible sections.
- Selected theme persists across page reloads.
- Theme toggle text/icon reflects current mode.

