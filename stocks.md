# Stock Module API Guide

This file documents all stock-related API calls currently exposed in `StockController`.

## Base URL

- Local base URL: `http://localhost:8080`
- API prefix: `/api`

## Common Error Response

When validation or business errors happen, APIs return this structure:

```json
{
  "timestamp": "2026-08-04T08:50:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "validationErrors": {
    "symbol": "symbol is required"
  }
}
```

Fields:
- `timestamp` - error time
- `status` - HTTP status code
- `error` - status text
- `message` - error message
- `validationErrors` - field-level validation errors (if any)

---

## 1) Search Stocks

- **Method**: `GET`
- **Path**: `/api/stocks/search`
- **Query Params**:
  - `query` (required, string) - symbol or company name (example: `AAPL`, `Tesla`)

### Example Request
`GET /api/stocks/search?query=AAPL`

### Success Response (200)
```json
[
  {
    "symbol": "AAPL",
    "displaySymbol": "AAPL",
    "companyName": "APPLE INC",
    "type": "Common Stock"
  }
]
```

---

## 2) Get Company Details

- **Method**: `GET`
- **Path**: `/api/stocks/{symbol}`
- **Path Params**:
  - `symbol` (required, string)

### Example Request
`GET /api/stocks/AAPL`

### Success Response (200)
```json
{
  "symbol": "AAPL",
  "companyName": "Apple Inc",
  "exchange": "NASDAQ NMS - GLOBAL MARKET",
  "country": "US",
  "sector": "Technology",
  "currency": "USD",
  "website": "https://www.apple.com/",
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

## 3) Marketplace (Paginated)

- **Method**: `GET`
- **Path**: `/api/stocks/marketplace`
- **Query Params**:
  - `page` (optional, int, default `1`)
  - `size` (optional, int, default `10`, max effective page size in service is `10`)

### Example Request
`GET /api/stocks/marketplace?page=1&size=10`

### Success Response (200)
```json
{
  "page": 1,
  "size": 10,
  "totalPages": 10,
  "totalItems": 100,
  "items": [
    {
      "symbol": "AAPL",
      "companyName": "Apple Inc",
      "exchange": "NASDAQ",
      "currentPrice": 192.11,
      "dailyChangePercent": 0.64
    }
  ]
}
```

---

## 4) Buy Stock

- **Method**: `POST`
- **Path**: `/api/portfolios/{portfolioId}/stocks/buy`
- **Path Params**:
  - `portfolioId` (required, long)
- **Request Body**:

```json
{
  "symbol": "AAPL",
  "quantity": 10
}
```

Validation:
- `symbol` required, non-blank
- `quantity` required, minimum `0.0001`

### Success Response (200)
```json
{
  "portfolioId": 1,
  "symbol": "AAPL",
  "companyName": "Apple Inc",
  "action": "BUY",
  "quantity": 10.0000,
  "executedPrice": 145.00,
  "totalAmount": 1450.00,
  "remainingCashBalance": 3550.00,
  "totalShares": 15.0000,
  "averagePurchasePrice": 126.67,
  "transactionDate": "2026-08-04T08:55:21.123Z"
}
```

---

## 5) Sell Stock

- **Method**: `POST`
- **Path**: `/api/portfolios/{portfolioId}/stocks/sell`
- **Path Params**:
  - `portfolioId` (required, long)
- **Request Body**:

```json
{
  "symbol": "AAPL",
  "quantity": 5
}
```

Validation:
- `symbol` required, non-blank
- `quantity` required, minimum `0.0001`

### Success Response (200)
```json
{
  "portfolioId": 1,
  "symbol": "AAPL",
  "companyName": "Apple Inc",
  "action": "SELL",
  "quantity": 5.0000,
  "executedPrice": 150.00,
  "totalAmount": 750.00,
  "remainingCashBalance": 4300.00,
  "totalShares": 10.0000,
  "averagePurchasePrice": 126.67,
  "transactionDate": "2026-08-04T09:02:10.456Z"
}
```

---

## 6) Get Portfolio Stock Holdings

- **Method**: `GET`
- **Path**: `/api/portfolios/{portfolioId}/stocks/holdings`
- **Path Params**:
  - `portfolioId` (required, long)

### Example Request
`GET /api/portfolios/1/stocks/holdings`

### Success Response (200)
```json
[
  {
    "symbol": "AAPL",
    "companyName": "Apple Inc",
    "shares": 15.0000,
    "averagePurchasePrice": 126.67,
    "costBasis": 1900.05,
    "currentPrice": 150.00,
    "marketValue": 2250.00,
    "unrealizedProfitLoss": 349.95,
    "unrealizedProfitLossPercent": 18.4185
  }
]
```

---

## 7) Get Single Holding Details (Holding + Transactions)

- **Method**: `GET`
- **Path**: `/api/portfolios/{portfolioId}/stocks/holdings/{symbol}`
- **Path Params**:
  - `portfolioId` (required, long)
  - `symbol` (required, string)

### Example Request
`GET /api/portfolios/1/stocks/holdings/AAPL`

### Success Response (200)
```json
{
  "holding": {
    "symbol": "AAPL",
    "companyName": "Apple Inc",
    "shares": 15.0000,
    "averagePurchasePrice": 126.67,
    "costBasis": 1900.05,
    "currentPrice": 150.00,
    "marketValue": 2250.00,
    "unrealizedProfitLoss": 349.95,
    "unrealizedProfitLossPercent": 18.4185
  },
  "transactions": [
    {
      "transactionId": 12,
      "symbol": "AAPL",
      "action": "BUY",
      "quantity": 10.0000,
      "transactionPrice": 120.00,
      "transactionDate": "2026-08-01T10:00:00Z"
    }
  ]
}
```

---

## 8) Get Transactions by Symbol

- **Method**: `GET`
- **Path**: `/api/portfolios/{portfolioId}/stocks/{symbol}/transactions`
- **Path Params**:
  - `portfolioId` (required, long)
  - `symbol` (required, string)

### Example Request
`GET /api/portfolios/1/stocks/AAPL/transactions`

### Success Response (200)
```json
[
  {
    "transactionId": 12,
    "symbol": "AAPL",
    "action": "BUY",
    "quantity": 10.0000,
    "transactionPrice": 120.00,
    "transactionDate": "2026-08-01T10:00:00Z"
  },
  {
    "transactionId": 13,
    "symbol": "AAPL",
    "action": "SELL",
    "quantity": 5.0000,
    "transactionPrice": 150.00,
    "transactionDate": "2026-08-04T09:02:10Z"
  }
]
```

---

## 9) Subscribe Symbols for Live Updates

- **Method**: `POST`
- **Path**: `/api/stocks/live/subscriptions`
- **Request Body**:

```json
{
  "symbols": ["AAPL", "TSLA", "NVDA"]
}
```

Validation:
- `symbols` must not be empty

### Success Response (202)
No body.

---

## WebSocket Output for Live Prices

After subscription, live events are pushed by backend via STOMP:

- Topic for all updates: `/topic/stocks/prices`
- Topic per symbol: `/topic/stocks/{SYMBOL}` (example: `/topic/stocks/AAPL`)

Payload format:

```json
{
  "symbol": "AAPL",
  "price": 193.25,
  "timestamp": "2026-08-04T09:10:00.000Z"
}
```

STOMP endpoint to connect:
- `/ws/stocks`

---

## Quick curl Samples

```bash
curl "http://localhost:8080/api/stocks/search?query=TSLA"
curl "http://localhost:8080/api/stocks/TSLA"
curl "http://localhost:8080/api/stocks/marketplace?page=1&size=10"
curl -X POST "http://localhost:8080/api/portfolios/1/stocks/buy" -H "Content-Type: application/json" -d "{\"symbol\":\"AAPL\",\"quantity\":10}"
curl -X POST "http://localhost:8080/api/portfolios/1/stocks/sell" -H "Content-Type: application/json" -d "{\"symbol\":\"AAPL\",\"quantity\":5}"
curl "http://localhost:8080/api/portfolios/1/stocks/holdings"
curl "http://localhost:8080/api/portfolios/1/stocks/holdings/AAPL"
curl "http://localhost:8080/api/portfolios/1/stocks/AAPL/transactions"
curl -X POST "http://localhost:8080/api/stocks/live/subscriptions" -H "Content-Type: application/json" -d "{\"symbols\":[\"AAPL\",\"TSLA\"]}"
```

