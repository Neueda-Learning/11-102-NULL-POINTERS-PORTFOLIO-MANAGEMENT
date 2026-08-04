const api = {
  get: (url) => fetch(url).then(handleResponse),
  post: (url, body) => fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body)
  }).then(handleResponse)
};

const state = {
  stomp: null,
  holdingsBySymbol: new Map()
};

const el = (id) => document.getElementById(id);
const portfolioId = () => Number(el("portfolioId").value || 1);

function handleResponse(res) {
  return res.text().then((text) => {
    const payload = text ? JSON.parse(text) : null;
    if (!res.ok) {
      throw new Error(payload?.message || `HTTP ${res.status}`);
    }
    return payload;
  });
}

function log(message, data) {
  const out = el("logOutput");
  const line = data ? `${message}\n${JSON.stringify(data, null, 2)}\n` : `${message}\n`;
  out.textContent = `${line}${out.textContent}`;
}

function setJson(id, data) {
  el(id).textContent = JSON.stringify(data, null, 2);
}

function wireEvents() {
  el("searchBtn").addEventListener("click", searchStocks);
  el("loadMarketplaceBtn").addEventListener("click", loadMarketplace);
  el("buyBtn").addEventListener("click", () => trade("buy"));
  el("sellBtn").addEventListener("click", () => trade("sell"));
  el("loadHoldingsBtn").addEventListener("click", loadHoldings);
  el("loadTxBtn").addEventListener("click", loadTransactionsByInput);
  el("subscribeBtn").addEventListener("click", subscribeSymbolsFromInput);
  el("connectWsBtn").addEventListener("click", connectWs);
}

async function searchStocks() {
  try {
    const query = el("searchQuery").value.trim();
    const data = await api.get(`/api/stocks/search?query=${encodeURIComponent(query)}`);
    const tbody = el("searchTable").querySelector("tbody");
    tbody.innerHTML = "";
    data.forEach((item) => {
      const tr = document.createElement("tr");
      tr.innerHTML = `<td>${item.symbol}</td><td>${item.companyName}</td><td>${item.type}</td><td><button data-symbol="${item.symbol}">Details</button></td>`;
      tr.querySelector("button").addEventListener("click", () => loadCompany(item.symbol));
      tbody.appendChild(tr);
    });
  } catch (err) {
    log("Search failed", err.message);
  }
}

async function loadCompany(symbol) {
  try {
    const data = await api.get(`/api/stocks/${encodeURIComponent(symbol)}`);
    setJson("companyDetails", data);
    el("tradeSymbol").value = data.symbol;
    el("txSymbol").value = data.symbol;
  } catch (err) {
    log("Company details failed", err.message);
  }
}

async function loadMarketplace() {
  try {
    const page = Number(el("marketPage").value || 1);
    const size = Number(el("marketSize").value || 10);
    const data = await api.get(`/api/stocks/marketplace?page=${page}&size=${size}`);
    const tbody = el("marketTable").querySelector("tbody");
    tbody.innerHTML = "";

    data.items.forEach((item) => {
      const tr = document.createElement("tr");
      tr.innerHTML = `<td>${item.symbol}</td><td>${item.companyName}</td><td>${item.exchange}</td><td>${item.currentPrice}</td><td>${item.dailyChangePercent}</td><td><button data-symbol="${item.symbol}">Buy</button></td>`;
      tr.querySelector("button").addEventListener("click", () => {
        el("tradeSymbol").value = item.symbol;
        el("tradeQuantity").focus();
      });
      tbody.appendChild(tr);
    });
  } catch (err) {
    log("Marketplace failed", err.message);
  }
}

async function trade(action) {
  try {
    const symbol = el("tradeSymbol").value.trim();
    const quantity = Number(el("tradeQuantity").value);
    const data = await api.post(`/api/portfolios/${portfolioId()}/stocks/${action}`, { symbol, quantity });
    setJson("tradeResult", data);
    await loadHoldings();
  } catch (err) {
    log(`${action.toUpperCase()} failed`, err.message);
  }
}

async function loadHoldings() {
  try {
    const data = await api.get(`/api/portfolios/${portfolioId()}/stocks/holdings`);
    const tbody = el("holdingsTable").querySelector("tbody");
    tbody.innerHTML = "";
    state.holdingsBySymbol.clear();

    data.forEach((item) => {
      state.holdingsBySymbol.set(item.symbol, item);
      const tr = document.createElement("tr");
      tr.innerHTML = `<td data-sym="${item.symbol}">${item.symbol}</td><td>${item.shares}</td><td>${item.averagePurchasePrice}</td><td class="price">${item.currentPrice}</td><td class="mv">${item.marketValue}</td><td class="pl">${item.unrealizedProfitLoss}</td><td><button class="detail">Open</button></td><td><button class="tx">Open</button></td><td><button class="sub">Sub</button></td>`;
      tr.querySelector(".detail").addEventListener("click", () => loadHoldingDetails(item.symbol));
      tr.querySelector(".tx").addEventListener("click", () => loadTransactions(item.symbol));
      tr.querySelector(".sub").addEventListener("click", () => subscribeSymbols([item.symbol]));
      tbody.appendChild(tr);
    });
  } catch (err) {
    log("Load holdings failed", err.message);
  }
}

async function loadHoldingDetails(symbol) {
  try {
    const data = await api.get(`/api/portfolios/${portfolioId()}/stocks/holdings/${encodeURIComponent(symbol)}`);
    setJson("holdingDetails", data);
  } catch (err) {
    log("Holding details failed", err.message);
  }
}

async function loadTransactions(symbol) {
  try {
    const data = await api.get(`/api/portfolios/${portfolioId()}/stocks/${encodeURIComponent(symbol)}/transactions`);
    setJson("txOutput", data);
  } catch (err) {
    log("Transactions failed", err.message);
  }
}

async function loadTransactionsByInput() {
  const symbol = el("txSymbol").value.trim();
  if (!symbol) return;
  await loadTransactions(symbol);
}

async function subscribeSymbols(symbols) {
  try {
    await api.post("/api/stocks/live/subscriptions", { symbols });
    log("Subscribed symbols", symbols);
    if (state.stomp && state.stomp.connected) {
      symbols.forEach((s) => state.stomp.subscribe(`/topic/stocks/${s.toUpperCase()}`, (m) => onLiveMessage(JSON.parse(m.body))));
    }
  } catch (err) {
    log("Subscribe API failed", err.message);
  }
}

async function subscribeSymbolsFromInput() {
  const symbols = el("subSymbols").value
    .split(",")
    .map((s) => s.trim().toUpperCase())
    .filter(Boolean);

  if (!symbols.length) return;
  await subscribeSymbols(symbols);
}

function connectWs() {
  if (!window.StompJs) {
    log("STOMP library not loaded");
    return;
  }

  if (state.stomp && state.stomp.connected) {
    log("WebSocket already connected");
    return;
  }

  const protocol = window.location.protocol === "https:" ? "wss" : "ws";
  const brokerURL = `${protocol}://${window.location.host}/ws/stocks`;

  state.stomp = new StompJs.Client({
    brokerURL,
    reconnectDelay: 3000,
    onConnect: () => {
      el("wsStatus").textContent = "WS: connected";
      log("WS connected", { brokerURL });
      state.stomp.subscribe("/topic/stocks/prices", (msg) => onLiveMessage(JSON.parse(msg.body)));
    },
    onStompError: (frame) => {
      el("wsStatus").textContent = "WS: error";
      log("WS STOMP error", frame);
    },
    onWebSocketClose: () => {
      el("wsStatus").textContent = "WS: disconnected";
    }
  });

  state.stomp.activate();
}

function onLiveMessage(update) {
  setJson("liveOutput", update);
  const symbol = update.symbol;
  const price = Number(update.price);
  const rows = [...el("holdingsTable").querySelectorAll("tbody tr")];
  rows.forEach((row) => {
    const sym = row.querySelector("td[data-sym]")?.getAttribute("data-sym");
    if (sym !== symbol) return;

    const holding = state.holdingsBySymbol.get(symbol);
    if (!holding) return;

    const shares = Number(holding.shares);
    const avg = Number(holding.averagePurchasePrice);
    const costBasis = shares * avg;
    const marketValue = shares * price;
    const pnl = marketValue - costBasis;

    row.querySelector(".price").textContent = price.toFixed(2);
    row.querySelector(".mv").textContent = marketValue.toFixed(2);
    row.querySelector(".pl").textContent = pnl.toFixed(2);
  });
}

wireEvents();

