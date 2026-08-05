'use strict';

// ─── State ────────────────────────────────────────────────────────────────
let pendingSellId = null;
let allocationChart = null;
let performanceChart = null;
let stockInsightChart = null;
let stockSymbolLookupTimer = null;
const DEFAULT_PORTFOLIO_ID = 1;
const marketplaceState = {
  category: 'STOCK',
  previousCategory: 'STOCK',
  page: 1,
  size: 10,
  totalPages: 1
};

// ─── Init ─────────────────────────────────────────────────────────────────
document.addEventListener('DOMContentLoaded', () => {
  initNavigation();
  initStockAutoFill();
  navigateTo('dashboard');
});

// ─── Navigation ───────────────────────────────────────────────────────────
function initNavigation() {
  document.querySelectorAll('.nav-item').forEach(link => {
    link.addEventListener('click', e => {
      e.preventDefault();
      navigateTo(link.dataset.section);
    });
  });
}

function navigateTo(section) {
  document.querySelectorAll('.nav-item').forEach(l =>
    l.classList.toggle('active', l.dataset.section === section));
  document.querySelectorAll('.section').forEach(s =>
    s.classList.toggle('active', s.id === `section-${section}`));

  switch (section) {
    case 'dashboard':    loadDashboard(); break;
    case 'holdings':     loadHoldings(document.getElementById('holdings-filter').value); break;
    case 'marketplace':  loadMarketplace(); break;
    case 'transactions': loadTransactions(); break;
  }
}

// ─── Dashboard ────────────────────────────────────────────────────────────
async function loadDashboard() {
  await Promise.all([loadSummary(), loadAllocationChart(), loadPerformanceChart()]);
}

async function loadSummary() {
  try {
    const data = await apiFetch('/api/v1/portfolio/summary');
    const pos = Number(data.totalReturns) >= 0;

    document.getElementById('kpi-total-value').textContent = fmt(data.totalPortfolioValue);
    document.getElementById('kpi-returns').textContent = (pos ? '+' : '') + fmt(data.totalReturns);
    document.getElementById('kpi-returns-pct').textContent =
      `(${pos ? '+' : ''}${data.totalReturnsPercent}%)`;
    document.getElementById('kpi-returns-pct').style.color =
      pos ? 'var(--success)' : 'var(--danger)';

    document.getElementById('summary-cards').innerHTML = [
      { label: 'Total Value',    value: fmt(data.totalPortfolioValue), sub: '' },
      { label: 'Total Invested', value: fmt(data.totalInvested),       sub: '' },
      { label: 'Total Returns',  value: (pos?'+':'')+fmt(data.totalReturns), sub: `${pos?'+':''}${data.totalReturnsPercent}%`, pos },
      { label: 'Stocks Value',   value: fmt(data.stocksValue),  sub: `${data.stocksPercent}%` },
      { label: 'Bonds Value',    value: fmt(data.bondsValue),   sub: `${data.bondsPercent}%` },
      { label: 'Crypto Value',   value: fmt(data.cryptoValue),  sub: `${data.cryptoPercent}%` },
    ].map(c => `
      <div class="summary-card">
        <div class="sc-label">${c.label}</div>
        <div class="sc-value ${c.pos===true?'pos':c.pos===false?'neg':''}">${c.value}</div>
        ${c.sub ? `<div class="sc-sub">${c.sub}</div>` : ''}
      </div>`).join('');
  } catch (e) {
    console.error('Summary error', e);
  }
}

async function loadAllocationChart() {
  try {
    const d = await apiFetch('/api/v1/portfolio/summary');
    const ctx = document.getElementById('chart-allocation').getContext('2d');
    if (allocationChart) allocationChart.destroy();
    allocationChart = new Chart(ctx, {
      type: 'doughnut',
      data: {
        labels: ['Stocks', 'Bonds', 'Crypto'],
        datasets: [{
          data: [+d.stocksValue, +d.bondsValue, +d.cryptoValue],
          backgroundColor: ['#3b82f6','#f59e0b','#8b5cf6'],
          borderColor: getComputedStyle(document.documentElement)
            .getPropertyValue('--surface').trim() || '#fff',
          borderWidth: 3,
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: { position: 'bottom',
            labels: { color: getComputedStyle(document.documentElement)
                .getPropertyValue('--text').trim() || '#000', font: { size: 12 } }},
          tooltip: { callbacks: { label: ctx => ` $${Number(ctx.raw).toLocaleString('en-US', {minimumFractionDigits:2})}` } }
        }
      }
    });
  } catch (e) { console.error('Allocation chart error', e); }
}

async function loadPerformanceChart() {
  try {
    const d = await apiFetch('/api/v1/portfolio/performance-history');
    const ctx = document.getElementById('chart-performance').getContext('2d');
    if (performanceChart) performanceChart.destroy();
    const textColor = getComputedStyle(document.documentElement).getPropertyValue('--text').trim();
    const mutedColor = getComputedStyle(document.documentElement).getPropertyValue('--text-muted').trim();
    performanceChart = new Chart(ctx, {
      type: 'line',
      data: {
        labels: d.labels,
        datasets: [{
          label: 'Portfolio Value',
          data: d.values.map(Number),
          borderColor: '#4f46e5',
          backgroundColor: 'rgba(79,70,229,.1)',
          borderWidth: 2,
          fill: true,
          tension: 0.4,
          pointRadius: 4,
          pointBackgroundColor: '#4f46e5',
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: { display: false },
          tooltip: { callbacks: { label: ctx => ` $${Number(ctx.raw).toLocaleString('en-US', {minimumFractionDigits:2})}` } }
        },
        scales: {
          x: { ticks: { color: mutedColor, maxRotation: 30 }, grid: { color: 'rgba(128,128,128,.1)' } },
          y: { ticks: { color: mutedColor, callback: v => '$'+Number(v).toLocaleString() }, grid: { color: 'rgba(128,128,128,.1)' } }
        }
      }
    });
  } catch (e) { console.error('Performance chart error', e); }
}

// ─── Holdings ─────────────────────────────────────────────────────────────
async function loadHoldings(type = 'ALL') {
  const container = document.getElementById('holdings-container');
  const statusEl  = document.getElementById('holdings-status');
  container.innerHTML = '<p style="color:var(--text-muted);padding:12px 0;">Loading...</p>';
  statusEl.innerHTML = '';

  try {
    const data = await apiFetch(`/api/v1/portfolio/holdings?type=${type}`);
    if (!data.length) {
      container.innerHTML = emptyState('No holdings found. Add your first asset!');
      return;
    }

    if (type === 'STOCK') { container.innerHTML = buildStocksTable(data); }
    else if (type === 'BOND') { container.innerHTML = buildBondsTable(data); }
    else if (type === 'CRYPTO') { container.innerHTML = buildCryptoTable(data); }
    else {
      const stocks = data.filter(r => r.asset_type === 'STOCK');
      const bonds  = data.filter(r => r.asset_type === 'BOND');
      const crypto = data.filter(r => r.asset_type === 'CRYPTO');
      let html = '';
      if (stocks.length) html += `<h4 style="margin:8px 0 8px;color:var(--text-muted);">📊 Stocks</h4>${buildStocksTable(stocks)}`;
      if (bonds.length)  html += `<h4 style="margin:20px 0 8px;color:var(--text-muted);">📄 Bonds</h4>${buildBondsTable(bonds)}`;
      if (crypto.length) html += `<h4 style="margin:20px 0 8px;color:var(--text-muted);">🪙 Crypto</h4>${buildCryptoTable(crypto)}`;
      container.innerHTML = html || emptyState('No holdings found.');
    }
    statusEl.innerHTML = `<span class="status-success">✅ ${data.length} holding(s) loaded</span>`;
  } catch (e) {
    container.innerHTML = `<p class="status-error">❌ Failed: ${e.message}</p>`;
  }
}

function buildStocksTable(rows) {
  const head = `<tr>
    <th>Symbol</th><th>Company</th><th>Shares</th>
    <th>Price</th><th>Cost Basis</th><th>Market Value</th>
    <th>P/L</th><th>Details</th><th>Action</th></tr>`;
  const body = rows.map(r => {
    const pl = Number(r.profit_loss || 0);
    return `<tr>
      <td><strong>${esc(r.symbol)}</strong></td>
      <td>${esc(r.asset_name)}</td>
      <td>${num(r.quantity)}</td>
      <td>${fmt(r.current_price)}</td>
      <td>${fmt(r.cost_basis)}</td>
      <td>${fmt(r.market_value)}</td>
      <td class="${pl>=0?'pos':'neg'}">${pl>=0?'+':''}${fmt(pl)}</td>
      <td><button class="detail-btn" onclick="showStockTransactions('${esc(r.symbol)}')">Details</button></td>
      <td><button class="sell-btn" onclick="openSellModal(${r.asset_id},'${esc(r.asset_name)}')">Sell</button></td>
    </tr>`;
  }).join('');
  return `<table class="data-table"><thead>${head}</thead><tbody>${body}</tbody></table>`;
}

function buildBondsTable(rows) {
  const head = `<tr>
    <th>Issuer</th><th>Coupon Rate</th><th>Maturity</th>
    <th>Qty</th><th>Face Value</th><th>Total Value</th>
    <th>P/L</th><th>Action</th></tr>`;
  const body = rows.map(r => {
    const pl = Number(r.profit_loss || 0);
    return `<tr>
      <td><strong>${esc(r.issuer || r.asset_name)}</strong></td>
      <td>${num(r.interest_rate)}%</td>
      <td>${esc(r.maturity_date)}</td>
      <td>1</td>
      <td>${fmt(r.amount_invested)}</td>
      <td>${fmt(r.total_value)}</td>
      <td class="${pl>=0?'pos':'neg'}">${pl>=0?'+':''}${fmt(pl)}</td>
      <td><button class="sell-btn" onclick="openSellModal(${r.asset_id},'${esc(r.issuer||r.asset_name)}')">Sell</button></td>
    </tr>`;
  }).join('');
  return `<table class="data-table"><thead>${head}</thead><tbody>${body}</tbody></table>`;
}

function buildCryptoTable(rows) {
  const head = `<tr>
    <th>Symbol</th><th>Name</th><th>Quantity</th>
    <th>Current Price</th><th>Total Value</th>
    <th>P/L</th><th>Action</th></tr>`;
  const body = rows.map(r => {
    const pl = Number(r.profit_loss || 0);
    return `<tr>
      <td><strong>${esc(r.symbol)}</strong></td>
      <td>${esc(r.asset_name)}</td>
      <td>${num(r.quantity, 8)}</td>
      <td>${fmt(r.current_price)}</td>
      <td>${fmt(r.current_value)}</td>
      <td class="${pl>=0?'pos':'neg'}">${pl>=0?'+':''}${fmt(pl)}</td>
      <td><button class="sell-btn" onclick="openSellModal(${r.asset_id},'${esc(r.asset_name)}')">Sell</button></td>
    </tr>`;
  }).join('');
  return `<table class="data-table"><thead>${head}</thead><tbody>${body}</tbody></table>`;
}

// ─── Marketplace ───────────────────────────────────────────────────────────
async function loadMarketplace() {
  const filter = document.getElementById('marketplace-filter');
  marketplaceState.category = filter ? filter.value : 'STOCK';
  if (marketplaceState.category !== marketplaceState.previousCategory) {
    marketplaceState.page = 1;
    marketplaceState.previousCategory = marketplaceState.category;
  }

  const container = document.getElementById('market-container');
  const statusEl = document.getElementById('market-status');
  const pagerRow = document.getElementById('marketplace-pagination-row');

  container.innerHTML = '<p style="color:var(--text-muted);padding:12px 0;">Loading...</p>';
  statusEl.innerHTML = '';

  try {
    if (marketplaceState.category === 'STOCK') {
      pagerRow.style.display = 'flex';
      const data = await apiFetch(`/api/stocks/marketplace?page=${marketplaceState.page}&size=${marketplaceState.size}`);
      marketplaceState.totalPages = Math.max(1, Number(data.totalPages || 1));
      updateMarketplacePager();
      container.innerHTML = buildMarketplaceStocksTable(data.items || []);
      statusEl.innerHTML = `<span class="status-success">✅ ${data.items?.length || 0} stock(s) loaded</span>`;
      return;
    }

    pagerRow.style.display = 'none';
    const cryptos = await apiFetch('/api/v1/crypto');
    container.innerHTML = buildMarketplaceCryptoTable(cryptos || []);
    statusEl.innerHTML = `<span class="status-success">✅ ${cryptos?.length || 0} crypto asset(s) loaded</span>`;
  } catch (e) {
    container.innerHTML = `<p class="status-error">❌ Failed: ${e.message}</p>`;
  }
}

function changeMarketplacePage(delta) {
  const nextPage = marketplaceState.page + delta;
  if (nextPage < 1 || nextPage > marketplaceState.totalPages) {
    return;
  }
  marketplaceState.page = nextPage;
  loadMarketplace();
}

function updateMarketplacePager() {
  const pageText = document.getElementById('market-page-text');
  const prevBtn = document.getElementById('market-prev-btn');
  const nextBtn = document.getElementById('market-next-btn');

  pageText.textContent = `Page ${marketplaceState.page} of ${marketplaceState.totalPages}`;
  prevBtn.disabled = marketplaceState.page <= 1;
  nextBtn.disabled = marketplaceState.page >= marketplaceState.totalPages;
}

function buildMarketplaceStocksTable(rows) {
  if (!rows.length) {
    return emptyState('No stocks found for this page.');
  }

  const head = `<tr>
    <th>Symbol</th><th>Company</th><th>Exchange</th>
    <th>Current Price</th><th>Day %</th><th>Buy</th><th>View Performance</th></tr>`;

  const body = rows.map(r => `
    <tr>
      <td><strong>${esc(r.symbol)}</strong></td>
      <td>${esc(r.companyName)}</td>
      <td>${esc(r.exchange)}</td>
      <td>${fmt(r.currentPrice)}</td>
      <td class="${Number(r.dailyChangePercent || 0) >= 0 ? 'pos' : 'neg'}">${Number(r.dailyChangePercent || 0).toFixed(2)}%</td>
      <td><button class="btn btn-primary btn-sm" onclick="buyFromMarketplace('${esc(r.symbol)}', '${esc(r.companyName)}')">Buy</button></td>
      <td><button class="detail-btn" onclick="showStockPerformance('${esc(r.symbol)}')">View Performance</button></td>
    </tr>`
  ).join('');

  return `<table class="data-table"><thead>${head}</thead><tbody>${body}</tbody></table>`;
}

function buildMarketplaceCryptoTable(rows) {
  if (!rows.length) {
    return emptyState('No crypto assets available.');
  }

  const head = `<tr><th>Symbol</th><th>Name</th><th>Current Price</th><th>Quantity</th><th>Current Value</th></tr>`;
  const body = rows.map(r => `
    <tr>
      <td><strong>${esc(r.symbol)}</strong></td>
      <td>${esc(r.name)}</td>
      <td>${fmt(r.currentPrice)}</td>
      <td>${num(r.quantity, 8)}</td>
      <td>${fmt(r.currentValue)}</td>
    </tr>`
  ).join('');

  return `<table class="data-table"><thead>${head}</thead><tbody>${body}</tbody></table>`;
}

async function buyFromMarketplace(symbol, companyName) {
  const qtyInput = prompt(`Enter quantity to buy for ${companyName} (${symbol})`, '1');
  if (qtyInput === null) {
    return;
  }
  const quantity = Number(qtyInput);
  if (!Number.isFinite(quantity) || quantity <= 0) {
    alert('Please enter a valid quantity greater than 0.');
    return;
  }

  try {
    await apiFetch(`/api/portfolios/${DEFAULT_PORTFOLIO_ID}/stocks/buy`, {
      method: 'POST',
      body: JSON.stringify({ symbol, quantity })
    });
    alert(`Bought ${quantity} share(s) of ${symbol}`);
    if (document.getElementById('section-holdings').classList.contains('active')) {
      loadHoldings(document.getElementById('holdings-filter').value);
    }
  } catch (e) {
    alert(`Buy failed: ${e.message}`);
  }
}

// ─── Transactions ──────────────────────────────────────────────────────────
async function loadTransactions() {
  const container = document.getElementById('tx-container');
  const statusEl  = document.getElementById('tx-status');
  container.innerHTML = '<p style="color:var(--text-muted);padding:12px 0;">Loading...</p>';

  try {
    const data = await apiFetch('/api/v1/portfolio/transactions');
    if (!data.length) {
      container.innerHTML = emptyState('No transactions yet.');
      return;
    }
    const head = `<tr>
      <th>#</th><th>Symbol</th><th>Asset Type</th><th>Type</th>
      <th>Quantity</th><th>Price</th><th>Total Amount</th><th>Date & Time</th></tr>`;
    const body = data.map(r => {
      const isBuy = r.transaction_type === 'BUY';
      const atype = (r.asset_type||'').toLowerCase();
      return `<tr>
        <td>${esc(r.transaction_id)}</td>
        <td><strong>${esc(r.symbol)}</strong></td>
        <td><span class="badge badge-${atype}">${esc(r.asset_type)}</span></td>
        <td><span class="badge ${isBuy?'badge-buy':'badge-sell'}">${esc(r.transaction_type)}</span></td>
        <td>${num(r.quantity, 4)}</td>
        <td>${fmt(r.transaction_price)}</td>
        <td>${fmt(r.total_amount)}</td>
        <td>${fmtDate(r.transaction_date)}</td>
      </tr>`;
    }).join('');
    container.innerHTML = `<table class="data-table"><thead>${head}</thead><tbody>${body}</tbody></table>`;
    statusEl.innerHTML = `<span class="status-success">✅ ${data.length} transaction(s) loaded</span>`;
  } catch (e) {
    container.innerHTML = `<p class="status-error">❌ Failed: ${e.message}</p>`;
  }
}

// ─── Stock Insights (Details / Performance) ───────────────────────────────
function openStockInsightModal(title) {
  document.getElementById('stock-insight-title').textContent = title;
  document.getElementById('stock-insight-status').innerHTML = '';
  document.getElementById('stock-insight-content').innerHTML = '';
  document.getElementById('stock-performance-wrap').style.display = 'none';
  document.getElementById('stock-insight-modal').classList.add('open');
}

function closeStockInsightModal() {
  document.getElementById('stock-insight-modal').classList.remove('open');
}

async function showStockTransactions(symbol) {
  openStockInsightModal(`🧾 Transactions: ${symbol}`);
  const statusEl = document.getElementById('stock-insight-status');
  const contentEl = document.getElementById('stock-insight-content');
  statusEl.innerHTML = '<span style="color:var(--text-muted);">Loading transaction details...</span>';

  try {
    const tx = await apiFetch(`/api/portfolios/${DEFAULT_PORTFOLIO_ID}/stocks/${encodeURIComponent(symbol)}/transactions`);
    if (!tx.length) {
      contentEl.innerHTML = emptyState(`No transactions found for ${symbol}.`);
      statusEl.innerHTML = '';
      return;
    }

    const head = `<tr><th>ID</th><th>Type</th><th>Quantity</th><th>Price</th><th>Date</th></tr>`;
    const body = tx.map(t => `
      <tr>
        <td>${esc(t.transactionId)}</td>
        <td><span class="badge ${t.action === 'BUY' ? 'badge-buy' : 'badge-sell'}">${esc(t.action)}</span></td>
        <td>${num(t.quantity, 4)}</td>
        <td>${fmt(t.transactionPrice)}</td>
        <td>${fmtDate(t.transactionDate)}</td>
      </tr>`
    ).join('');

    contentEl.innerHTML = `<table class="data-table"><thead>${head}</thead><tbody>${body}</tbody></table>`;
    statusEl.innerHTML = `<span class="status-success">✅ ${tx.length} transaction(s) loaded</span>`;
  } catch (e) {
    statusEl.innerHTML = `<span class="status-error">❌ ${e.message}</span>`;
  }
}

async function showStockPerformance(symbol) {
  openStockInsightModal(`📈 Performance: ${symbol}`);
  const statusEl = document.getElementById('stock-insight-status');
  const wrapEl = document.getElementById('stock-performance-wrap');
  statusEl.innerHTML = '<span style="color:var(--text-muted);">Loading performance...</span>';

  try {
    const result = await apiFetch(`/api/stocks/${encodeURIComponent(symbol)}/performance`);
    const points = result.points || [];
    if (!points.length) {
      document.getElementById('stock-insight-content').innerHTML = emptyState(`No performance data available for ${symbol}.`);
      statusEl.innerHTML = '';
      return;
    }

    wrapEl.style.display = 'flex';
    renderStockPerformanceChart(points, `${result.companyName || symbol} (${symbol})`);
    statusEl.innerHTML = `<span class="status-success">✅ Last ${points.length} day(s) performance loaded</span>`;
  } catch (e) {
    statusEl.innerHTML = `<span class="status-error">❌ ${e.message}</span>`;
  }
}

function renderStockPerformanceChart(points, label) {
  const ctx = document.getElementById('chart-stock-performance').getContext('2d');
  if (stockInsightChart) {
    stockInsightChart.destroy();
  }

  const textColor = getComputedStyle(document.documentElement).getPropertyValue('--text').trim();
  const mutedColor = getComputedStyle(document.documentElement).getPropertyValue('--text-muted').trim();
  stockInsightChart = new Chart(ctx, {
    type: 'line',
    data: {
      labels: points.map(p => p.date),
      datasets: [{
        label,
        data: points.map(p => Number(p.closePrice)),
        borderColor: '#4f46e5',
        backgroundColor: 'rgba(79,70,229,.1)',
        borderWidth: 2,
        fill: true,
        tension: 0.35,
        pointRadius: 3,
        pointBackgroundColor: '#4f46e5'
      }]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: { display: false },
        tooltip: {
          callbacks: {
            label: c => ` $${Number(c.raw).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
          }
        }
      },
      scales: {
        x: { ticks: { color: mutedColor, maxRotation: 30 }, grid: { color: 'rgba(128,128,128,.1)' } },
        y: { ticks: { color: mutedColor, callback: v => '$' + Number(v).toLocaleString() }, grid: { color: 'rgba(128,128,128,.1)' } }
      }
    }
  });
}

// ─── Add Asset Modal ───────────────────────────────────────────────────────
function openAddModal() {
  document.getElementById('add-modal').classList.add('open');
  document.getElementById('add-status').textContent = '';
  document.getElementById('add-type').value = 'STOCK';
  switchAddForm('STOCK');
}
function closeAddModal() {
  document.getElementById('add-modal').classList.remove('open');
}
function switchAddForm(type) {
  ['STOCK','BOND','CRYPTO'].forEach(t => {
    document.getElementById(`form-${t}`).style.display = t === type ? 'grid' : 'none';
  });
}

function initStockAutoFill() {
  const stockSymbolInput = document.getElementById('stock-symbol');
  if (!stockSymbolInput) {
    return;
  }

  stockSymbolInput.addEventListener('input', () => {
    const raw = stockSymbolInput.value.trim();
    if (stockSymbolLookupTimer) {
      clearTimeout(stockSymbolLookupTimer);
    }
    if (!raw) {
      return;
    }

    stockSymbolLookupTimer = setTimeout(() => {
      autoFillStockFields(raw.toUpperCase());
    }, 350);
  });
}

async function autoFillStockFields(symbol) {
  const statusEl = document.getElementById('add-status');
  try {
    statusEl.innerHTML = '<span style="color:var(--text-muted);">Fetching stock details...</span>';
    const details = await apiFetch(`/api/stocks/${encodeURIComponent(symbol)}`);

    document.getElementById('stock-symbol').value = details.symbol || symbol;
    document.getElementById('stock-name').value = details.companyName || '';

    const price = Number(details?.quote?.currentPrice || 0);
    if (price > 0) {
      document.getElementById('stock-price').value = price.toFixed(2);
    }

    if (!document.getElementById('stock-date').value) {
      document.getElementById('stock-date').value = new Date().toISOString().slice(0, 10);
    }

    if (!document.getElementById('stock-quantity').value) {
      document.getElementById('stock-quantity').value = '1';
    }

    statusEl.innerHTML = '<span class="status-success">✅ Stock fields auto-filled from symbol</span>';
  } catch (e) {
    statusEl.innerHTML = `<span class="status-error">❌ Could not auto-fill: ${e.message}</span>`;
  }
}

async function submitAddAsset() {
  const type = document.getElementById('add-type').value;
  const btn = document.getElementById('add-submit-btn');
  const statusEl = document.getElementById('add-status');
  btn.disabled = true;
  btn.textContent = 'Adding...';
  statusEl.textContent = '';

  let payload = { type };
  try {
    if (type === 'STOCK') {
      const symbol = val('stock-symbol').toUpperCase();
      if (symbol) {
        await autoFillStockFields(symbol);
      }
      payload = { ...payload,
        symbol: val('stock-symbol'), assetName: val('stock-name'),
        quantity: val('stock-quantity'), purchasePrice: val('stock-price'),
        purchaseDate: val('stock-date') || new Date().toISOString().slice(0,10) };
      if (!payload.symbol || !payload.assetName || !payload.quantity || !payload.purchasePrice)
        throw new Error('Please fill all stock fields');
    } else if (type === 'BOND') {
      payload = { ...payload,
        issuer: val('bond-issuer'), interestRate: val('bond-rate'),
        amountInvested: val('bond-amount'), startDate: val('bond-start'),
        tenureMonths: val('bond-tenure') };
      if (!payload.issuer || !payload.interestRate || !payload.amountInvested || !payload.startDate || !payload.tenureMonths)
        throw new Error('Please fill all bond fields');
    } else {
      payload = { ...payload,
        symbol: val('crypto-symbol'), assetName: val('crypto-name'),
        quantity: val('crypto-quantity'), buyPrice: val('crypto-buy-price'),
        currentPrice: val('crypto-current-price') };
      if (!payload.symbol || !payload.assetName || !payload.quantity || !payload.buyPrice || !payload.currentPrice)
        throw new Error('Please fill all crypto fields');
    }

    await apiFetch('/api/v1/portfolio/holdings', { method: 'POST', body: JSON.stringify(payload) });
    statusEl.innerHTML = '<span class="status-success">✅ Asset added!</span>';
    setTimeout(() => {
      closeAddModal();
      if (document.getElementById('section-dashboard').classList.contains('active')) loadDashboard();
      if (document.getElementById('section-holdings').classList.contains('active'))
        loadHoldings(document.getElementById('holdings-filter').value);
    }, 800);
  } catch (e) {
    statusEl.innerHTML = `<span class="status-error">❌ ${e.message}</span>`;
  } finally {
    btn.disabled = false;
    btn.textContent = 'Add Asset';
  }
}

// ─── Sell Modal ────────────────────────────────────────────────────────────
function openSellModal(id, name) {
  pendingSellId = id;
  document.getElementById('sell-asset-name').textContent = name;
  document.getElementById('sell-modal').classList.add('open');
}
function closeSellModal() {
  pendingSellId = null;
  document.getElementById('sell-modal').classList.remove('open');
}
async function confirmSell() {
  if (!pendingSellId) return;
  const id = pendingSellId;
  closeSellModal();
  try {
    await apiFetch(`/api/v1/portfolio/sell/${id}`, { method: 'POST' });
    loadHoldings(document.getElementById('holdings-filter').value);
    if (document.getElementById('section-dashboard').classList.contains('active')) loadDashboard();
  } catch (e) {
    alert('Sell failed: ' + e.message);
  }
}

// ─── API Helper ───────────────────────────────────────────────────────────
async function apiFetch(url, opts = {}) {
  const res = await fetch(url, {
    headers: { 'Content-Type': 'application/json', ...opts.headers },
    ...opts
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error(err.message || `HTTP ${res.status}`);
  }
  if (res.status === 204) return {};
  return res.json();
}

// ─── Utility ──────────────────────────────────────────────────────────────
function esc(v) {
  if (v == null) return '—';
  return String(v).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;')
                  .replace(/"/g,'&quot;').replace(/'/g,'&#39;');
}
function fmt(v) {
  if (v == null) return '—';
  return '$' + Number(v).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}
function num(v, dec = 4) {
  if (v == null) return '—';
  return Number(v).toLocaleString('en-US', { minimumFractionDigits: 0, maximumFractionDigits: dec });
}
function fmtDate(v) {
  if (!v) return '—';
  return new Date(v).toLocaleString('en-US', { dateStyle: 'medium', timeStyle: 'short' });
}
function val(id) { return document.getElementById(id)?.value?.trim() || ''; }
function emptyState(msg) {
  return `<div class="empty-state"><div style="font-size:3rem;">📭</div><p>${msg}</p></div>`;
}

