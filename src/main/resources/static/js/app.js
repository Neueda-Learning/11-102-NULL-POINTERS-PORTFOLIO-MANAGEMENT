const api = {
  get: (url) => fetch(url).then(handleResponse),
  post: (url, body) => fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body)
  }).then(handleResponse)
};

const state = {
  holdingsBySymbol: new Map()
};

const el = (id) => document.getElementById(id);
const portfolioId = () => Number(el("portfolioId")?.value || 1);

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
  if (!out) {
    console.log(line);
    return;
  }
  out.textContent = `${line}${out.textContent}`;
}

function setJson(id, data) {
  const target = el(id);
  if (target) {
    target.textContent = JSON.stringify(data, null, 2);
  }
}

function bindClick(id, handler) {
  const node = el(id);
  if (node) {
    node.addEventListener("click", handler);
  }
}

function wireEvents() {
  bindClick("searchBtn", searchStocks);
  bindClick("loadMarketplaceBtn", loadMarketplace);
  bindClick("buyBtn", () => trade("buy"));
  bindClick("sellBtn", () => trade("sell"));
  bindClick("loadHoldingsBtn", loadHoldings);
  bindClick("loadTxBtn", loadTransactionsByInput);
}

async function searchStocks() {
  try {
    const query = el("searchQuery").value.trim();
    const data = await api.get(`/api/stocks/search?query=${encodeURIComponent(query)}`);
    const tbody = el("searchTable").querySelector("tbody");
    tbody.innerHTML = "";
    data.forEach((item) => {
      const tr = document.createElement("tr");
      tr.innerHTML = `<td>${item.symbol}</td><td>${item.companyName}</td><td>${item.type}</td><td><button class="details-btn">Details</button></td>`;
      tr.querySelector(".details-btn").addEventListener("click", () => loadCompany(item.symbol));
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
      tr.innerHTML = `
        <td>${item.symbol}</td>
        <td>${item.companyName}</td>
        <td>${item.exchange}</td>
        <td>${item.currentPrice}</td>
        <td>${item.dailyChangePercent}</td>
        <td><button class="buy-btn">Buy</button></td>
        <td><button class="perf-btn">View Performance</button></td>`;

      tr.querySelector(".buy-btn").addEventListener("click", () => {
        el("tradeSymbol").value = item.symbol;
        el("tradeQuantity").focus();
      });

      tr.querySelector(".perf-btn").addEventListener("click", () => {
        loadPerformance(item.symbol);
      });

      tbody.appendChild(tr);
    });
  } catch (err) {
    log("Marketplace failed", err.message);
  }
}

async function loadPerformance(symbol) {
  try {
    const data = await api.get(`/api/stocks/${encodeURIComponent(symbol)}/performance`);
    setJson("performanceData", data);
    const title = el("performanceTitle");
    if (title) {
      title.textContent = `${data.companyName} (${data.symbol}) - Last 10 Days`;
    }
    drawPerformanceChart(data.points || []);
  } catch (err) {
    log("Performance load failed", err.message);
  }
}

function drawPerformanceChart(points) {
  const canvas = el("performanceChart");
  if (!canvas) {
    return;
  }
  const ctx = canvas.getContext("2d");
  const width = canvas.width;
  const height = canvas.height;
  ctx.clearRect(0, 0, width, height);

  if (!points.length) {
    ctx.fillStyle = "#334155";
    ctx.fillText("No performance data available", 20, 30);
    return;
  }

  const prices = points.map((p) => Number(p.closePrice));
  const min = Math.min(...prices);
  const max = Math.max(...prices);
  const spread = Math.max(max - min, 1);

  const padding = 36;
  const plotWidth = width - padding * 2;
  const plotHeight = height - padding * 2;

  ctx.strokeStyle = "#cbd5e1";
  ctx.lineWidth = 1;
  ctx.beginPath();
  ctx.moveTo(padding, padding);
  ctx.lineTo(padding, height - padding);
  ctx.lineTo(width - padding, height - padding);
  ctx.stroke();

  ctx.strokeStyle = "#2563eb";
  ctx.lineWidth = 2;
  ctx.beginPath();

  points.forEach((point, index) => {
    const x = padding + (index * plotWidth) / Math.max(points.length - 1, 1);
    const y = padding + ((max - Number(point.closePrice)) / spread) * plotHeight;
    if (index === 0) {
      ctx.moveTo(x, y);
    } else {
      ctx.lineTo(x, y);
    }
  });
  ctx.stroke();

  ctx.fillStyle = "#0f172a";
  ctx.font = "12px Arial";
  ctx.fillText(`High: ${max.toFixed(2)}`, padding, 18);
  ctx.fillText(`Low: ${min.toFixed(2)}`, width - 110, 18);
  ctx.fillText(points[0].date, padding, height - 10);
  ctx.fillText(points[points.length - 1].date, width - 92, height - 10);
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
    alert(err.message);
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
      tr.innerHTML = `<td data-sym="${item.symbol}">${item.symbol}</td><td>${item.shares}</td><td>${item.averagePurchasePrice}</td><td class="price">${item.currentPrice}</td><td class="mv">${item.marketValue}</td><td class="pl">${item.unrealizedProfitLoss}</td><td><button class="detail">Open</button></td><td><button class="tx">Open</button></td>`;
      tr.querySelector(".detail").addEventListener("click", () => loadHoldingDetails(item.symbol));
      tr.querySelector(".tx").addEventListener("click", () => loadTransactions(item.symbol));
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
  if (!symbol) {
    return;
  }
  await loadTransactions(symbol);
}


wireEvents();

