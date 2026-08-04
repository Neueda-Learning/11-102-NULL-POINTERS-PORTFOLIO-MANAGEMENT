# API Testing & Finnhub Setup Guide

## Current Status

✅ **Mock Mode Enabled** — All stock APIs are working with demo data by default.  
ℹ️ The invalid API key has been automatically bypassed.

## Debug Endpoints (Available Right Now)

Test the API without using the frontend:

```
GET  /api/debug/health                    → System status
GET  /api/debug/test-search/AAPL          → Search stocks
GET  /api/debug/test-profile/AAPL         → Company profile
GET  /api/debug/test-quote/AAPL           → Stock quote
GET  /api/debug/test-all                  → All endpoints combined
```

## Quick Test

Open in browser or curl:

```bash
curl http://localhost:8080/api/debug/test-all
```

Expected response (mock data):

```json
{
  "search": [
    { "symbol": "AAPL", "displaySymbol": "AAPL", "companyName": "Apple Inc", "type": "Common Stock" }
  ],
  "profile": {
    "symbol": "AAPL",
    "companyName": "Apple Inc",
    "exchange": "NASDAQ",
    "country": "US",
    "sector": "Technology",
    "currency": "USD",
    "website": "https://www.apple.com"
  },
  "quote": {
    "currentPrice": 192.11,
    "change": 1.22,
    "changePercent": 0.64,
    "high": 193.00,
    "low": 189.71,
    "open": 190.20,
    "previousClose": 190.89,
    "timestamp": 1722850000
  }
}
```

---

## Switch to Real Finnhub API (Production)

### Step 1: Get a Finnhub API Key

1. Visit https://finnhub.io/register
2. Sign up for a free account
3. Copy your **API Key** from the dashboard
4. Paste into `src/main/resources/application.properties`:

```properties
finnhub.api.key=YOUR_REAL_API_KEY_HERE
finnhub.mock-mode=false
```

### Step 2: Rebuild & Run

```bash
.\mvnw.cmd -DskipTests compile
.\mvnw.cmd spring-boot:run
```

All endpoints will now use real Finnhub market data.

---

## All Stock APIs (Using Frontend)

Open in browser: **http://localhost:8080/**

The frontend supports:

- ✅ **Search** — Find stocks by symbol or name
- ✅ **Company Details** — Profile, exchange, sector
- ✅ **Marketplace** — Browse curated 100 stocks with pagination
- ✅ **Buy** — Purchase stocks with cash deduction
- ✅ **Sell** — Sell holdings and restore cash
- ✅ **Holdings** — View all active positions
- ✅ **Transaction History** — Per-symbol records
- ✅ **Live Prices** — WebSocket real-time updates (demo rates)

---

## Troubleshooting

### "502 Bad Gateway" Error

**Cause**: Real API key missing or invalid  
**Fix**: Either enable mock mode or provide a valid API key

### WebSocket Not Connecting

**Cause**: Browser CORS or WS endpoint mismatch  
**Fix**: Ensure app runs on `http://localhost:8080` (not HTTPS in dev)

### No Holdings After Buy

**Cause**: Portfolio ID is hardcoded as `1` in frontend  
**Fix**: Ensure portfolio ID `1` exists in database or change in frontend form

---

## API Endpoint Reference

See `stocks.md` for complete request/response examples.

Quick reference:

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/stocks/search?query=AAPL` | Search |
| GET | `/api/stocks/AAPL` | Company details |
| GET | `/api/stocks/marketplace?page=1&size=10` | Browse stocks |
| POST | `/api/portfolios/1/stocks/buy` | Buy |
| POST | `/api/portfolios/1/stocks/sell` | Sell |
| GET | `/api/portfolios/1/stocks/holdings` | Holdings |

---

## Mock Data (Demo Symbols)

When in mock mode, only these symbols return data:

- `AAPL` — Apple Inc
- `TSLA` — Tesla Inc
- `MSFT` — Microsoft Corp
- `GOOGL` — Alphabet Inc
- `AMZN` — Amazon.com Inc

Others return generic demo quotes.

---

## Disable Mock Mode Temporarily

Edit `application.properties`:

```properties
finnhub.mock-mode=false
```

App will then call real Finnhub endpoint (requires valid API key).

