'use strict';

const CRYPTO_MARKET_SYMBOLS = [
  'BTCUSD', 'ETHUSD', 'BNBUSD', 'XRPUSD', 'SOLUSD',
  'ADAUSD', 'DOGEUSD', 'TRXUSD', 'AVAXUSD', 'MATICUSD'
];

const DUMMY_BOND_MARKET = [
  { issuer: 'HDFC Bank', bondType: 'Corporate Bond', interestRate: 7.25, tenureMonths: 12, minInvestment: 1000 },
  { issuer: 'ICICI Bank', bondType: 'Bank Bond', interestRate: 7.1, tenureMonths: 24, minInvestment: 2000 },
  { issuer: 'State Bank of India', bondType: 'Bank Bond', interestRate: 6.9, tenureMonths: 36, minInvestment: 1500 },
  { issuer: 'Reliance Industries', bondType: 'Corporate Bond', interestRate: 7.6, tenureMonths: 24, minInvestment: 2500 },
  { issuer: 'Tata Capital', bondType: 'Corporate Bond', interestRate: 7.45, tenureMonths: 18, minInvestment: 1200 },
  { issuer: 'Axis Bank', bondType: 'Bank Bond', interestRate: 7.05, tenureMonths: 12, minInvestment: 1000 },
  { issuer: 'Kotak Mahindra Bank', bondType: 'Bank Bond', interestRate: 7.15, tenureMonths: 30, minInvestment: 3000 },
  { issuer: 'Bajaj Finance', bondType: 'Corporate Bond', interestRate: 7.8, tenureMonths: 24, minInvestment: 2000 }
];

const ALERT_RULES_KEY = 'portfolio.alertRules';
const ALERT_EVENTS_KEY = 'portfolio.alertEvents';
const ALERT_SNAPSHOTS_KEY = 'portfolio.alertSnapshots';
const DASHBOARD_ALERTS_VISIBLE_KEY = 'portfolio.dashboardAlertsVisible';
const ACTION_ALERT_MAX = 4;
const ALLOWED_ALERT_TYPES = ['PROFIT_LOSS', 'RISK_CONCENTRATION', 'GOAL_PORTFOLIO_VALUE', 'PRICE_ABOVE', 'DAILY_CHANGE'];

let pendingSell = null;
let allocationChart = null;
let performanceChart = null;
let cryptoLookupTimer = null;
let stockInsightChart = null;
let stockSymbolLookupTimer = null;
let alertPollTimer = null;
let tradePreviewContext = null;
const actionAlerts = [];
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
  initAlerts();
  onAlertTypeChange();
  navigateTo('dashboard');
});

// ─── Navigation ───────────────────────────────────────────────────────────
function initNavigation() {
  document.querySelectorAll('.nav-item').forEach(link => {
    link.addEventListener('click', event => {
      event.preventDefault();
      navigateTo(link.dataset.section);
    });
  });
}

function navigateTo(section) {
  document.querySelectorAll('.nav-item').forEach(link => {
    link.classList.toggle('active', link.dataset.section === section);
  });
  document.querySelectorAll('.section').forEach(panel => {
    panel.classList.toggle('active', panel.id === `section-${section}`);
  });
  switch (section) {
    case 'dashboard':    loadDashboard(); break;
    case 'holdings':     loadHoldings(document.getElementById('holdings-filter').value); break;
    case 'marketplace':  loadMarketplace(); break;
    case 'transactions': loadTransactions(); break;
    case 'alerts':       renderAlertsSection(); break;
    default: break;
  }
}

function pushActionAlert(message, level = 'warning', ttlMs = 9000) {
  const id = `action-${Date.now()}-${Math.random().toString(16).slice(2, 7)}`;
  actionAlerts.unshift({ id, message, level });
  if (actionAlerts.length > ACTION_ALERT_MAX) actionAlerts.length = ACTION_ALERT_MAX;
  renderActionAlerts();
  setTimeout(() => {
    const idx = actionAlerts.findIndex(item => item.id === id);
    if (idx >= 0) {
      actionAlerts.splice(idx, 1);
      renderActionAlerts();
    }
  }, ttlMs);
}

function renderActionAlerts() {
  const host = document.getElementById('action-alert-host');
  if (!host) return;
  host.innerHTML = actionAlerts.map(item => `
    <div class="action-alert action-alert-${normalizeAlertLevel(item.level)}">
      ${alertIconForLevel(item.level)} ${esc(item.message)}
    </div>`).join('');
}

function alertLevelForEvent(event) {
  if (event?.level) return normalizeAlertLevel(event.level);
  const highRiskCategories = ['RISK_CONCENTRATION', 'RISK_DRAWDOWN', 'PROFIT_LOSS', 'MARKET_MOVE'];
  return highRiskCategories.includes(event.category) ? 'risk' : 'warning';
}

function normalizeAlertLevel(level) {
  return ['danger', 'warning', 'success', 'info', 'risk'].includes(level) ? level : 'warning';
}

function alertIconForLevel(level) {
  switch (normalizeAlertLevel(level)) {
    case 'danger': return '🔴';
    case 'success': return '🟢';
    case 'info': return '🔵';
    case 'risk': return '🟠';
    default: return '🟡';
  }
}

function alertIconForEvent(event) {
  return alertIconForLevel(alertLevelForEvent(event));
}

// ─── Dashboard ────────────────────────────────────────────────────────────
async function loadDashboard() {
  await Promise.all([loadSummary(), loadAllocationChart(), loadPerformanceChart()]);
  applyDashboardAlertsVisibility();
  renderDashboardAlerts();
}

function isDashboardAlertsVisible() {
  const value = localStorage.getItem(DASHBOARD_ALERTS_VISIBLE_KEY);
  return value == null ? true : value === 'true';
}

function applyDashboardAlertsVisibility() {
  const card = document.getElementById('dashboard-live-alerts-card');
  const toggleBtn = document.getElementById('dashboard-alerts-toggle-btn');
  if (!card || !toggleBtn) return;
  const visible = isDashboardAlertsVisible();
  card.style.display = visible ? '' : 'none';
  toggleBtn.textContent = visible ? 'Hide Live Alerts' : 'Show Live Alerts';
}

function toggleDashboardAlertsVisibility() {
  const nextVisible = !isDashboardAlertsVisible();
  localStorage.setItem(DASHBOARD_ALERTS_VISIBLE_KEY, String(nextVisible));
  applyDashboardAlertsVisibility();
}

function renderDashboardAlerts(limit = 6) {
  if (!isDashboardAlertsVisible()) return;
  const container = document.getElementById('dashboard-alerts-container');
  if (!container) return;
  const events = loadAlertEvents().slice(0, limit);
  if (!events.length) {
    container.innerHTML = emptyState('No alerts yet. Buy assets or run Check Alerts to evaluate rules.');
    return;
  }
  container.innerHTML = events.map(event => {
    const level = alertLevelForEvent(event);
    return `
      <div class="alert-event-card severity-${level} ${event.read ? '' : 'unread'}">
        <div class="alert-title">${alertIconForEvent(event)} ${esc(event.title)}</div>
        <div>${esc(event.message)}</div>
        <div class="alert-pill-row">
          <span class="alert-pill">${esc(event.category)}</span>
          ${event.symbol ? `<span class="alert-pill">${esc(event.symbol)}</span>` : ''}
        </div>
        <div class="receipt-meta">${fmtDate(event.timestamp)}</div>
        <div class="settings-actions" style="margin-top:10px;">
          <button class="btn btn-secondary btn-sm" onclick="removeTriggeredAlertFromDashboard('${escJs(event.id)}')">Remove Trigger</button>
        </div>
      </div>`;
  }).join('');
}

function removeTriggeredAlertFromDashboard(eventId) {
  if (!eventId) return;
  const nextEvents = loadAlertEvents().filter(event => event.id !== eventId);
  saveAlertEvents(nextEvents);
  renderAlertsSection();
  renderDashboardAlerts();
  renderAlertsBadge();
  pushActionAlert('Triggered alert removed from dashboard.', 'warning', 5000);
}

function clearTriggeredAlertsFromDashboard() {
  if (!window.confirm('Clear all triggered alerts from dashboard and alerts center?')) return;
  saveAlertEvents([]);
  renderAlertsSection();
  renderDashboardAlerts();
  renderAlertsBadge();
  pushActionAlert('All triggered alerts cleared.', 'warning', 5000);
}

function scrollToDashboardLiveAlerts() {
  if (!isDashboardAlertsVisible()) return;
  const target = document.getElementById('dashboard-live-alerts-card') || document.getElementById('dashboard-alerts-container');
  if (!target) return;
  target.scrollIntoView({ behavior: 'smooth', block: 'start' });
}

async function loadSummary() {
  try {
    const data = await apiFetch('/api/v1/portfolio/summary');
    const pos = Number(data.totalReturns) >= 0;

    document.getElementById('kpi-total-value').textContent = fmt(data.totalPortfolioValue);
    document.getElementById('kpi-returns').textContent = (pos ? '+' : '') + fmt(data.totalReturns);
    document.getElementById('kpi-returns-pct').textContent = `(${pos ? '+' : ''}${data.totalReturnsPercent}%)`;
    document.getElementById('kpi-returns-pct').style.color = pos ? 'var(--success)' : 'var(--danger)';

    document.getElementById('summary-cards').innerHTML = [
      { label: 'Total Value', value: fmt(data.totalPortfolioValue), sub: '' },
      { label: 'Total Invested', value: fmt(data.totalInvested), sub: '' },
      { label: 'Total Returns', value: (pos ? '+' : '') + fmt(data.totalReturns), sub: `${pos ? '+' : ''}${data.totalReturnsPercent}%`, pos },
      { label: 'Stocks Value', value: fmt(data.stocksValue), sub: `${data.stocksPercent}%` },
      { label: 'Bonds Value', value: fmt(data.bondsValue), sub: `${data.bondsPercent}%` },
      { label: 'Crypto Value', value: fmt(data.cryptoValue), sub: `${data.cryptoPercent}%` }
    ].map(card => `
      <div class="summary-card">
        <div class="sc-label">${card.label}</div>
        <div class="sc-value ${card.pos === true ? 'pos' : card.pos === false ? 'neg' : ''}">${card.value}</div>
        ${card.sub ? `<div class="sc-sub">${card.sub}</div>` : ''}
      </div>`).join('');
  } catch (error) {
    console.error('Summary error', error);
  }
}

async function loadAllocationChart() {
  try {
    const summary = await apiFetch('/api/v1/portfolio/summary');
    const ctx = document.getElementById('chart-allocation').getContext('2d');

    if (allocationChart) allocationChart.destroy();

    allocationChart = new Chart(ctx, {
      type: 'doughnut',
      data: {
        labels: ['Stocks', 'Bonds', 'Crypto'],
        datasets: [{
          data: [Number(summary.stocksValue || 0), Number(summary.bondsValue || 0), Number(summary.cryptoValue || 0)],
          backgroundColor: ['#3b82f6', '#f59e0b', '#8b5cf6'],
          borderColor: getComputedStyle(document.documentElement).getPropertyValue('--surface').trim() || '#fff',
          borderWidth: 3
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: {
            position: 'bottom',
            labels: {
              color: getComputedStyle(document.documentElement).getPropertyValue('--text').trim() || '#000',
              font: { size: 12 }
            }
          },
          tooltip: {
            callbacks: {
              label: context => ` $${Number(context.raw).toLocaleString('en-US', { minimumFractionDigits: 2 })}`
            }
          }
        }
      }
    });
  } catch (error) {
    console.error('Allocation chart error', error);
  }
}

async function loadPerformanceChart() {
  try {
    const performance = await apiFetch('/api/v1/portfolio/performance-history');
    const ctx = document.getElementById('chart-performance').getContext('2d');
    const textColor = getComputedStyle(document.documentElement).getPropertyValue('--text').trim();
    const mutedColor = getComputedStyle(document.documentElement).getPropertyValue('--text-muted').trim();

    if (performanceChart) performanceChart.destroy();

    performanceChart = new Chart(ctx, {
      type: 'line',
      data: {
        labels: performance.labels || [],
        datasets: [{
          label: 'Portfolio Value',
          data: (performance.values || []).map(Number),
          borderColor: '#4f46e5',
          backgroundColor: 'rgba(79,70,229,.1)',
          borderWidth: 2,
          fill: true,
          tension: 0.4,
          pointRadius: 4,
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
              label: context => ` $${Number(context.raw).toLocaleString('en-US', { minimumFractionDigits: 2 })}`
            }
          }
        },
        scales: {
          x: { ticks: { color: mutedColor, maxRotation: 30 }, grid: { color: 'rgba(128,128,128,.1)' } },
          y: { ticks: { color: mutedColor, callback: value => '$' + Number(value).toLocaleString() }, grid: { color: 'rgba(128,128,128,.1)' } }
        }
      }
    });
  } catch (error) {
    console.error('Performance chart error', error);
  }
}

// ─── Holdings ─────────────────────────────────────────────────────────────
async function loadHoldings(type = 'ALL') {
  const container = document.getElementById('holdings-container');
  const statusEl = document.getElementById('holdings-status');
  container.innerHTML = '<p style="color:var(--text-muted);padding:12px 0;">Loading...</p>';
  statusEl.innerHTML = '';

  try {
    if (type === 'CRYPTO') {
      const cryptoRows = activeCryptoHoldings(await apiFetch('/api/v1/crypto'));
      container.innerHTML = cryptoRows.length ? buildCryptoTable(cryptoRows) : emptyState('No crypto holdings found. Buy crypto to see it here.');
      statusEl.innerHTML = `<span class="status-success">✅ ${cryptoRows.length} crypto holding(s) loaded</span>`;
      return;
    }

    const genericPromise = apiFetch(`/api/v1/portfolio/holdings?type=${type}`);

    if (type === 'ALL') {
      const [genericResult, cryptoResult] = await Promise.allSettled([genericPromise, apiFetch('/api/v1/crypto')]);
      const genericRows = genericResult.status === 'fulfilled' ? safeArray(genericResult.value) : [];
      const stocks = genericRows.filter(row => row.asset_type === 'STOCK');
      const bonds = genericRows.filter(row => row.asset_type === 'BOND');
      const cryptos = activeCryptoHoldings(cryptoResult.status === 'fulfilled' ? cryptoResult.value : []);

      let html = '';
      if (stocks.length) html += `<h4 style="margin:8px 0 8px;color:var(--text-muted);">📊 Stocks</h4>${buildStocksTable(stocks)}`;
      if (bonds.length) html += `<h4 style="margin:20px 0 8px;color:var(--text-muted);">📄 Bonds</h4>${buildBondsTable(bonds)}`;
      if (cryptos.length) html += `<h4 style="margin:20px 0 8px;color:var(--text-muted);">🪙 Crypto</h4>${buildCryptoTable(cryptos)}`;

      container.innerHTML = html || emptyState('No holdings found. Add your first asset!');
      statusEl.innerHTML = `<span class="status-success">✅ ${stocks.length + bonds.length + cryptos.length} holding(s) loaded</span>`;
      return;
    }

    const genericRows = safeArray(await genericPromise);
    if (!genericRows.length) { container.innerHTML = emptyState(`No ${type.toLowerCase()} holdings found.`); return; }
    container.innerHTML = type === 'STOCK' ? buildStocksTable(genericRows) : buildBondsTable(genericRows);
    statusEl.innerHTML = `<span class="status-success">✅ ${genericRows.length} holding(s) loaded</span>`;
  } catch (error) {
    container.innerHTML = `<p class="status-error">❌ Failed: ${esc(error.message)}</p>`;
  }
}

function buildStocksTable(rows) {
  const head = `<tr>
    <th>Symbol</th><th>Company</th><th>Shares</th>
    <th>Avg Buy Price</th><th>Current Price</th><th>Cost Basis</th>
    <th>Market Value</th><th>P/L</th><th>Details</th><th>Action</th></tr>`;
  const body = rows.map(row => {
    const investedAmount = Number(row.cost_basis || 0);
    const profitLoss = Number(row.profit_loss || 0);
    const plPct = investedAmount > 0 ? (profitLoss / investedAmount) * 100 : 0;
    return `<tr>
      <td><strong>${esc(row.symbol)}</strong></td>
      <td>${esc(row.asset_name)}</td>
      <td>${num(row.quantity)}</td>
      <td>${fmt(row.purchase_price)}</td>
      <td>${fmt(row.current_price)}</td>
      <td>${fmt(row.cost_basis)}</td>
      <td>${fmt(row.market_value)}</td>
      <td class="${plPct >= 0 ? 'pos' : 'neg'}">${plPct >= 0 ? '+' : ''}${plPct.toFixed(2)}%</td>
      <td><button class="detail-btn" onclick="showStockTransactions('${escJs(row.symbol)}')">Details</button></td>
      <td><button class="sell-btn" onclick='openSellModal(${json({ id: row.asset_id, symbol: row.symbol, name: row.asset_name, quantity: row.quantity, type: "STOCK" })})'>Sell</button></td>
    </tr>`;
  }).join('');
  return `<table class="data-table"><thead>${head}</thead><tbody>${body}</tbody></table>`;
}

function buildBondsTable(rows) {
  const head = `<tr>
    <th>Issuer</th><th>Coupon Rate</th><th>Maturity</th>
    <th>Face Value</th><th>Total Value</th><th>P/L</th><th>Action</th></tr>`;
  const body = rows.map(row => {
    const profitLoss = Number(row.profit_loss || 0);
    return `<tr>
      <td><strong>${esc(row.issuer || row.asset_name)}</strong></td>
      <td>${num(row.interest_rate)}%</td>
      <td>${fmtDateOnly(row.maturity_date)}</td>
      <td>${fmt(row.amount_invested)}</td>
      <td>${fmt(row.total_value)}</td>
      <td class="${profitLoss >= 0 ? 'pos' : 'neg'}">${profitLoss >= 0 ? '+' : ''}${fmt(profitLoss)}</td>
      <td><button class="sell-btn" onclick='openSellModal(${json({ id: row.asset_id, name: row.issuer || row.asset_name, type: "GENERIC" })})'>Sell</button></td>
    </tr>`;
  }).join('');
  return `<table class="data-table"><thead>${head}</thead><tbody>${body}</tbody></table>`;
}

function buildCryptoTable(rows) {
  const head = `<tr>
    <th>Symbol</th><th>Name</th><th>Quantity</th>
    <th>Avg Buy Price</th><th>Current Price</th><th>Invested Amount</th>
    <th>P/L %</th><th>Action</th></tr>`;
  const body = rows.map(row => {
    const profitLoss = Number(row.profitLoss || 0);
    const investedAmount = Number(row.investedAmount || 0);
    const plPct = investedAmount > 0 ? (profitLoss / investedAmount) * 100 : 0;
    return `<tr>
      <td><strong>${esc(row.symbol)}</strong></td>
      <td>${esc(row.name)}</td>
      <td>${num(row.quantity, 8)}</td>
      <td>${fmt(row.buyPrice)}</td>
      <td>${fmt(row.currentPrice)}</td>
      <td>${fmt(investedAmount)}</td>
      <td class="${plPct >= 0 ? 'pos' : 'neg'}">${plPct >= 0 ? '+' : ''}${plPct.toFixed(2)}%</td>
      <td>
        <div class="action-group">
          <button class="detail-btn" onclick="showCryptoDetails(${row.cryptoId})">Details</button>
          <button class="sell-btn" onclick='openSellModal(${json({ cryptoId: row.cryptoId, symbol: row.symbol, name: row.name, quantity: row.quantity, buyPrice: row.buyPrice, currentPrice: row.currentPrice, type: "CRYPTO" })})'>Sell</button>
        </div>
      </td>
    </tr>`;
  }).join('');
  return `<table class="data-table"><thead>${head}</thead><tbody>${body}</tbody></table>`;
}

// ─── Marketplace ──────────────────────────────────────────────────────────
async function loadMarketplace() {
  const filter = document.getElementById('marketplace-filter');
  marketplaceState.category = filter ? filter.value : 'STOCK';
  if (marketplaceState.category !== marketplaceState.previousCategory) {
    marketplaceState.page = 1;
    marketplaceState.previousCategory = marketplaceState.category;
  }

  const container = document.getElementById('market-container');
  const marketGrid = document.getElementById('market-grid');
  const statusEl = document.getElementById('market-status');
  const pagerRow = document.getElementById('marketplace-pagination-row');
  const cryptoToolbar = document.getElementById('crypto-search-toolbar');
  const lookupContainer = document.getElementById('market-lookup');

  container.innerHTML = '';
  marketGrid.innerHTML = '';
  statusEl.innerHTML = '';
  if (lookupContainer) lookupContainer.innerHTML = '';

  const isStock = marketplaceState.category === 'STOCK';
  const isCrypto = marketplaceState.category === 'CRYPTO';
  const isBond = marketplaceState.category === 'BOND';

  pagerRow.style.display = isStock ? 'flex' : 'none';
  if (cryptoToolbar) cryptoToolbar.style.display = isCrypto ? 'flex' : 'none';
  // Keep a single table container for STOCK/BOND/CRYPTO marketplace views.
  container.style.display = '';
  marketGrid.style.display = 'none';

  try {
    if (isStock) {
      container.innerHTML = '<p style="color:var(--text-muted);padding:12px 0;">Loading stocks...</p>';
      const data = await apiFetch(`/api/stocks/marketplace?page=${marketplaceState.page}&size=${marketplaceState.size}`);
      marketplaceState.totalPages = Math.max(1, Number(data.totalPages || 1));
      updateMarketplacePager();
      container.innerHTML = buildMarketplaceStocksTable(data.items || []);
      statusEl.innerHTML = `<span class="status-success">✅ ${data.items?.length || 0} stock(s) loaded</span>`;
    } else if (isCrypto) {
      container.innerHTML = '<p style="color:var(--text-muted);padding:12px 0;">Refreshing market data...</p>';
      await refreshMarketplacePrices(true);
      const marketData = normalizeCryptos(await apiFetch('/api/v1/crypto/batch', {
        method: 'POST',
        body: JSON.stringify(CRYPTO_MARKET_SYMBOLS)
      }));
      const sorted = [...marketData].sort((a, b) => Number(b.currentPrice || 0) - Number(a.currentPrice || 0));
      container.innerHTML = sorted.length ? buildMarketplaceCryptoMarketTable(sorted) : emptyState('No marketplace crypto data available.');
      statusEl.innerHTML = `<span class="status-success">✅ ${sorted.length} crypto asset(s) loaded</span>`;
    } else {
      // BOND — use dummy market data
      container.innerHTML = buildMarketplaceBondsTable(DUMMY_BOND_MARKET);
      statusEl.innerHTML = `<span class="status-success">✅ ${DUMMY_BOND_MARKET.length} bond(s) loaded</span>`;
    }
  } catch (e) {
    container.innerHTML = `<p class="status-error">❌ Failed: ${esc(e.message)}</p>`;
  }
}

function buildMarketplaceCryptoMarketTable(rows) {
  const head = `<tr>
    <th>Symbol</th><th>Name</th><th>Type</th>
    <th>Current Price</th><th>Tracked Value</th><th>P/L</th><th>Actions</th></tr>`;

  const body = rows.map(row => {
    const profit = Number(row.profitLoss || 0);
    const profitClass = profit >= 0 ? 'pos' : 'neg';
    return `<tr>
      <td><strong>${esc(row.symbol)}</strong></td>
      <td>${esc(row.name)}</td>
      <td><span class="badge badge-crypto">CRYPTO</span></td>
      <td>${fmt(row.currentPrice)}</td>
      <td>${fmt(row.currentValue)}</td>
      <td class="${profitClass}">${profit >= 0 ? '+' : ''}${fmt(profit)}</td>
      <td>
        <div class="action-group">
          <button class="btn btn-secondary btn-sm" onclick="showCryptoDetails(${row.cryptoId})">Details</button>
          <button class="btn btn-primary btn-sm" onclick='openAddModal("CRYPTO", ${json({ symbol: row.symbol, name: row.name, currentPrice: row.currentPrice })})'>Buy</button>
        </div>
      </td>
    </tr>`;
  }).join('');

  return `<table class="data-table"><thead>${head}</thead><tbody>${body}</tbody></table>`;
}

function changeMarketplacePage(delta) {
  const nextPage = marketplaceState.page + delta;
  if (nextPage < 1 || nextPage > marketplaceState.totalPages) return;
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
  if (!rows.length) return emptyState('No stocks found for this page.');
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
      <td><button class="btn btn-primary btn-sm" onclick="buyFromMarketplace('${escJs(r.symbol)}', '${escJs(r.companyName)}', ${Number(r.currentPrice || 0)})">Buy</button></td>
      <td><button class="detail-btn" onclick="showStockPerformance('${escJs(r.symbol)}')">View Performance</button></td>
    </tr>`
  ).join('');
  return `<table class="data-table"><thead>${head}</thead><tbody>${body}</tbody></table>`;
}

function buildMarketplaceCryptoTable(rows) {
  if (!rows.length) return emptyState('No crypto assets available.');
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

function buildMarketplaceBondsTable(rows) {
  if (!rows.length) return emptyState('No bonds available right now.');
  const head = `<tr>
    <th>Issuer</th><th>Bond Type</th><th>Coupon Rate</th>
    <th>Tenure (Months)</th><th>Min Investment</th><th>Buy Bond</th></tr>`;
  const body = rows.map((r, idx) => `
    <tr>
      <td><strong>${esc(r.issuer)}</strong></td>
      <td>${esc(r.bondType)}</td>
      <td>${num(r.interestRate, 2)}%</td>
      <td>${esc(r.tenureMonths)}</td>
      <td>${fmt(r.minInvestment)}</td>
      <td><button class="btn btn-primary btn-sm" onclick="buyBondFromMarketplaceByIndex(${idx})">Buy Bond</button></td>
    </tr>`
  ).join('');
  return `<table class="data-table"><thead>${head}</thead><tbody>${body}</tbody></table>`;
}

function buyBondFromMarketplaceByIndex(index) {
  const bond = DUMMY_BOND_MARKET[index];
  if (!bond) return;
  buyBondFromMarketplace(bond.issuer, bond.interestRate, bond.tenureMonths, bond.minInvestment);
}

function buyBondFromMarketplace(issuer, interestRate, tenureMonths, minInvestment) {
  openAddModal('BOND');
  document.getElementById('bond-issuer').value = issuer;
  document.getElementById('bond-rate').value = Number(interestRate).toFixed(2);
  document.getElementById('bond-tenure').value = String(tenureMonths);
  document.getElementById('bond-amount').value = Number(minInvestment).toFixed(2);
  if (!document.getElementById('bond-start').value) {
    document.getElementById('bond-start').value = new Date().toISOString().slice(0, 10);
  }
  const statusEl = document.getElementById('add-status');
  statusEl.innerHTML = `<span class="status-success">✅ Bond form prefilled for ${esc(issuer)}. Click Add Asset to buy.</span>`;
}

function buyFromMarketplace(symbol, companyName, currentPrice) {
  openAddModal('STOCK');
  document.getElementById('stock-symbol').value = symbol || '';
  document.getElementById('stock-name').value = companyName || '';
  document.getElementById('stock-price').value = Number(currentPrice || 0) > 0 ? Number(currentPrice).toFixed(2) : '';
  document.getElementById('stock-date').value = new Date().toISOString().slice(0, 10);
  document.getElementById('stock-quantity').value = '';
  const statusEl = document.getElementById('add-status');
  statusEl.innerHTML = `<span class="status-success">✅ Stock details prefilled for ${esc(symbol)}. Enter quantity and click Add Asset.</span>`;
}

function buildMarketplaceCards(rows) {
  return `<div class="market-grid">${rows.map(row => {
    const profitClass = Number(row.profitLoss || 0) >= 0 ? 'pos' : 'neg';
    return `
      <article class="market-card">
        <div class="market-card-header">
          <div><h3>${esc(row.symbol)}</h3><p>${esc(row.name)}</p></div>
          <span class="badge badge-crypto">CRYPTO</span>
        </div>
        <div class="market-metrics">
          <div><span>Current Price</span><strong>${fmt(row.currentPrice)}</strong></div>
          <div><span>Tracked Value</span><strong>${fmt(row.currentValue)}</strong></div>
          <div><span>P/L</span><strong class="${profitClass}">${Number(row.profitLoss || 0) >= 0 ? '+' : ''}${fmt(row.profitLoss)}</strong></div>
        </div>
        <div class="market-card-actions">
          <button class="btn btn-secondary btn-sm" onclick="showCryptoDetails(${row.cryptoId})">Details</button>
          <button class="btn btn-primary btn-sm" onclick='openAddModal("CRYPTO", ${json({ symbol: row.symbol, name: row.name, currentPrice: row.currentPrice })})'>Buy</button>
        </div>
      </article>`;
  }).join('')}</div>`;
}

async function refreshMarketplace(silent = false) {
  await loadMarketplace();
  if (!silent) await refreshDashboardAndHoldings();
}

async function refreshMarketplacePrices(silent = false) {
  const results = await Promise.allSettled(
    CRYPTO_MARKET_SYMBOLS.map(symbol => apiFetch(`/api/v1/crypto/${encodeURIComponent(symbol)}/price`, { method: 'PUT' }))
  );
  if (!silent) {
    const failures = results.filter(r => r.status === 'rejected');
    const statusEl = document.getElementById('market-status');
    statusEl.innerHTML = failures.length
      ? `<span class="status-error">⚠️ Refreshed with ${failures.length} warning(s)</span>`
      : '<span class="status-success">✅ Market prices refreshed</span>';
  }
}

async function lookupCryptoBySymbol() {
  const symbolInput = document.getElementById('market-symbol-input');
  const lookupContainer = document.getElementById('market-lookup');
  const symbol = symbolInput.value.trim().toUpperCase();
  if (!symbol) { lookupContainer.innerHTML = '<p class="status-error">❌ Enter a symbol like BTCUSD.</p>'; return; }
  lookupContainer.innerHTML = '<p style="color:var(--text-muted);">Searching...</p>';
  try {
    let crypto;
    try {
      crypto = normalizeCrypto(await apiFetch(`/api/v1/crypto/symbol/${encodeURIComponent(symbol)}`));
    } catch (error) {
      crypto = normalizeCrypto(await apiFetch(`/api/v1/crypto/${encodeURIComponent(symbol)}/price`, { method: 'PUT' }));
    }
    if (!CRYPTO_MARKET_SYMBOLS.includes(symbol)) CRYPTO_MARKET_SYMBOLS.push(symbol);
    lookupContainer.innerHTML = buildLookupCard(crypto);
  } catch (error) {
    lookupContainer.innerHTML = `<p class="status-error">❌ ${esc(error.message)}</p>`;
  }
}

function buildLookupCard(crypto) {
  return `
    <div class="lookup-card">
      <div><strong>${esc(crypto.symbol)}</strong><p>${esc(crypto.name)}</p></div>
      <div class="lookup-actions">
        <span class="lookup-price">${fmt(crypto.currentPrice)}</span>
        <button class="btn btn-secondary btn-sm" onclick="showCryptoDetails(${crypto.cryptoId})">Details</button>
        <button class="btn btn-primary btn-sm" onclick='openAddModal("CRYPTO", ${json({ symbol: crypto.symbol, name: crypto.name, currentPrice: crypto.currentPrice })})'>Buy</button>
      </div>
    </div>`;
}

// ─── Transactions ─────────────────────────────────────────────────────────
async function loadTransactions() {
  const container = document.getElementById('tx-container');
  const statusEl = document.getElementById('tx-status');
  container.innerHTML = '<p style="color:var(--text-muted);padding:12px 0;">Loading...</p>';
  statusEl.innerHTML = '';
  try {
    const rows = safeArray(await apiFetch('/api/v1/transactions/history'));
    if (!rows.length) { container.innerHTML = emptyState('No transactions yet.'); return; }
    const head = `<tr>
      <th>#</th><th>Symbol</th><th>Name</th>
      <th>Type</th><th>Quantity</th><th>Price</th><th>Total</th><th>Date & Time</th></tr>`;
    const body = rows.map(row => {
      const isBuy = row.transactionType === 'BUY';
      const total = Number(row.quantity || 0) * Number(row.transactionPrice || 0);
      return `<tr>
        <td>${esc(row.transactionId)}</td>
        <td><strong>${esc(row.symbol)}</strong></td>
        <td>${esc(row.name)}</td>
        <td><span class="badge ${isBuy ? 'badge-buy' : 'badge-sell'}">${esc(row.transactionType)}</span></td>
        <td>${num(row.quantity, 8)}</td>
        <td>${fmt(row.transactionPrice)}</td>
        <td>${fmt(total)}</td>
        <td>${fmtDate(row.transactionDate)}</td>
      </tr>`;
    }).join('');
    container.innerHTML = `<table class="data-table"><thead>${head}</thead><tbody>${body}</tbody></table>`;
    statusEl.innerHTML = `<span class="status-success">✅ ${rows.length} transaction(s) loaded</span>`;
  } catch (error) {
    container.innerHTML = `<p class="status-error">❌ Failed: ${esc(error.message)}</p>`;
  }
}

// ─── Stock Insights ───────────────────────────────────────────────────────
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
    if (!tx.length) { contentEl.innerHTML = emptyState(`No transactions found for ${symbol}.`); statusEl.innerHTML = ''; return; }
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
  if (stockInsightChart) stockInsightChart.destroy();
  const textColor = getComputedStyle(document.documentElement).getPropertyValue('--text').trim();
  const mutedColor = getComputedStyle(document.documentElement).getPropertyValue('--text-muted').trim();
  stockInsightChart = new Chart(ctx, {
    type: 'line',
    data: {
      labels: points.map(p => p.date),
      datasets: [{ label, data: points.map(p => Number(p.closePrice)), borderColor: '#4f46e5', backgroundColor: 'rgba(79,70,229,.1)', borderWidth: 2, fill: true, tension: 0.35, pointRadius: 3, pointBackgroundColor: '#4f46e5' }]
    },
    options: {
      responsive: true, maintainAspectRatio: false,
      plugins: { legend: { display: false }, tooltip: { callbacks: { label: c => ` $${Number(c.raw).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}` } } },
      scales: {
        x: { ticks: { color: mutedColor, maxRotation: 30 }, grid: { color: 'rgba(128,128,128,.1)' } },
        y: { ticks: { color: mutedColor, callback: v => '$' + Number(v).toLocaleString() }, grid: { color: 'rgba(128,128,128,.1)' } }
      }
    }
  });
}

// ─── Add Asset Modal ──────────────────────────────────────────────────────
function openAddModal(type = 'STOCK', preset = {}) {
  document.getElementById('add-modal').classList.add('open');
  document.getElementById('add-status').textContent = '';
  document.getElementById('add-submit-btn').textContent = 'Review Trade';
  tradePreviewContext = null;
  document.getElementById('add-type').value = type;
  switchAddForm(type);
  const lookupStatus = document.getElementById('crypto-lookup-status');
  if (lookupStatus) lookupStatus.textContent = '';
  if (type === 'CRYPTO') {
    document.getElementById('crypto-symbol').value = preset.symbol || '';
    document.getElementById('crypto-name').value = preset.name || '';
    document.getElementById('crypto-current-price').value = preset.currentPrice != null ? preset.currentPrice : '';
    if (preset.symbol && !preset.name) fetchCryptoInfo();
  }
}

function closeAddModal() {
  document.getElementById('add-modal').classList.remove('open');
  tradePreviewContext = null;
}

function switchAddForm(type) {
  ['STOCK', 'BOND', 'CRYPTO'].forEach(formType => {
    document.getElementById(`form-${formType}`).style.display = formType === type ? 'grid' : 'none';
  });
}

function initStockAutoFill() {
  const stockSymbolInput = document.getElementById('stock-symbol');
  if (!stockSymbolInput) return;
  stockSymbolInput.addEventListener('input', () => {
    const raw = stockSymbolInput.value.trim();
    if (stockSymbolLookupTimer) clearTimeout(stockSymbolLookupTimer);
    if (!raw) return;
    stockSymbolLookupTimer = setTimeout(() => autoFillStockFields(raw.toUpperCase()), 350);
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
    if (price > 0) document.getElementById('stock-price').value = price.toFixed(2);
    if (!document.getElementById('stock-date').value) document.getElementById('stock-date').value = new Date().toISOString().slice(0, 10);
    if (!document.getElementById('stock-quantity').value) document.getElementById('stock-quantity').value = '1';
    statusEl.innerHTML = '<span class="status-success">✅ Stock fields auto-filled from symbol</span>';
  } catch (e) {
    statusEl.innerHTML = `<span class="status-error">❌ Could not auto-fill: ${e.message}</span>`;
  }
}

async function reviewAddAsset() {
  try {
    tradePreviewContext = await buildAddTradePreview();
    openTradePreviewModal('Review Buy Summary', tradePreviewContext.html, 'Confirm Buy');
  } catch (error) {
    document.getElementById('add-status').innerHTML = `<span class="status-error">❌ ${esc(error.message)}</span>`;
  }
}

async function submitAddAsset() {
  const type = document.getElementById('add-type').value;
  const btn = document.getElementById('add-submit-btn');
  const statusEl = document.getElementById('add-status');
  btn.disabled = true;
  btn.textContent = 'Adding...';
  statusEl.textContent = '';

  try {
    if (type === 'CRYPTO') {
      const payload = {
        symbol: val('crypto-symbol').toUpperCase(),
        name: val('crypto-name'),
        quantity: numberValue('crypto-quantity'),
        buyPrice: numberValue('crypto-buy-price'),
        currentPrice: numberValue('crypto-current-price'),
        transactionType: 'BUY'
      };
      if (!payload.symbol || !payload.name || payload.quantity == null || payload.buyPrice == null || payload.currentPrice == null)
        throw new Error('Please fill all crypto fields');
      await apiFetch('/api/v1/crypto', { method: 'POST', body: JSON.stringify(payload) });
    } else if (type === 'STOCK') {
      const symbol = val('stock-symbol').toUpperCase();
      if (symbol) await autoFillStockFields(symbol);
      const payload = {
        type,
        symbol: val('stock-symbol'),
        assetName: val('stock-name'),
        quantity: val('stock-quantity'),
        purchasePrice: val('stock-price'),
        purchaseDate: val('stock-date') || new Date().toISOString().slice(0, 10)
      };
      if (!payload.symbol || !payload.assetName || !payload.quantity || !payload.purchasePrice)
        throw new Error('Please fill all stock fields');
      await apiFetch('/api/v1/portfolio/holdings', { method: 'POST', body: JSON.stringify(payload) });
    } else {
      const payload = {
        type,
        issuer: val('bond-issuer'),
        interestRate: val('bond-rate'),
        amountInvested: val('bond-amount'),
        startDate: val('bond-start'),
        tenureMonths: val('bond-tenure')
      };
      if (!payload.issuer || !payload.interestRate || !payload.amountInvested || !payload.startDate || !payload.tenureMonths)
        throw new Error('Please fill all bond fields');
      await apiFetch('/api/v1/portfolio/holdings', { method: 'POST', body: JSON.stringify(payload) });
    }

    statusEl.innerHTML = '<span class="status-success">✅ Asset added successfully!</span>';
    const receipt = tradePreviewContext || await buildAddTradePreview();
    setTimeout(async () => {
      closeTradePreviewModal();
      closeAddModal();
      showTradeReceipt({
        title: `${receipt.assetType} Buy Receipt`,
        ...receipt,
        status: 'Completed'
      });
      await refreshDashboardAndHoldings();
      if (document.getElementById('section-transactions').classList.contains('active')) await loadTransactions();
      if (document.getElementById('section-marketplace').classList.contains('active')) await loadMarketplace();
      const alertResult = await evaluateAlerts(true);
      navigateTo('dashboard');
      if (alertResult.newCount > 0) setTimeout(scrollToDashboardLiveAlerts, 120);
      pushActionAlert('Trade completed. Alerts were checked right away.', 'warning', 7000);
    }, 700);
  } catch (error) {
    statusEl.innerHTML = `<span class="status-error">❌ ${esc(error.message)}</span>`;
    pushActionAlert(`Trade could not be completed: ${error.message}`, 'danger');
  } finally {
    btn.disabled = false;
    btn.textContent = 'Review Trade';
  }
}

// ─── Sell Modal ───────────────────────────────────────────────────────────
function openSellModal(payload) {
  pendingSell = payload;
  document.getElementById('sell-asset-name').textContent = payload.name || payload.symbol || 'this asset';
  const cryptoFields = document.getElementById('sell-crypto-fields');
  const helpText = document.getElementById('sell-help-text');
  const quantityInput = document.getElementById('sell-quantity');
  const quantityHelp = document.getElementById('sell-quantity-help');
  if (payload.type === 'CRYPTO') {
    cryptoFields.style.display = 'block';
    quantityInput.value = payload.quantity != null ? payload.quantity : '';
    quantityHelp.textContent = `Available quantity: ${num(payload.quantity, 8)} ${payload.symbol}`;
    helpText.textContent = 'This uses the crypto BUY/SELL backend and deducts only the quantity you enter.';
  } else if (payload.type === 'STOCK') {
    cryptoFields.style.display = 'block';
    quantityInput.value = payload.quantity != null ? payload.quantity : '';
    quantityHelp.textContent = `Available stocks: ${num(payload.quantity, 4)} ${payload.symbol || ''}`.trim();
    helpText.textContent = 'Enter how many stocks to sell. Selling all removes the holding from the list.';
  } else {
    cryptoFields.style.display = 'none';
    quantityInput.value = '';
    quantityHelp.textContent = '';
    helpText.textContent = 'This records a SELL transaction and removes the asset from your portfolio.';
  }
  document.getElementById('sell-modal').classList.add('open');
}

function closeSellModal() {
  pendingSell = null;
  document.getElementById('sell-modal').classList.remove('open');
  document.getElementById('sell-quantity').value = '';
}

function reviewSellTrade() {
  try {
    tradePreviewContext = buildSellTradePreview();
    openTradePreviewModal('Review Sell Summary', tradePreviewContext.html, 'Confirm Sell');
  } catch (error) {
    pushActionAlert(error.message, 'warning');
  }
}

async function confirmSell() {
  if (!pendingSell) return;
  try {
    const receipt = tradePreviewContext || buildSellTradePreview();
    if (pendingSell.type === 'CRYPTO') {
      const quantity = numberValue('sell-quantity');
      if (quantity == null || quantity <= 0) throw new Error('Enter a valid sell quantity');
      if (Number(quantity) > Number(pendingSell.quantity)) throw new Error('Sell quantity cannot exceed current holdings');
      await apiFetch('/api/v1/crypto', {
        method: 'POST',
        body: JSON.stringify({ symbol: pendingSell.symbol, name: pendingSell.name, quantity, currentPrice: pendingSell.currentPrice, transactionType: 'SELL' })
      });
    } else if (pendingSell.type === 'STOCK') {
      const quantity = numberValue('sell-quantity');
      if (quantity == null || quantity <= 0) throw new Error('Enter a valid sell quantity');
      if (Number(quantity) > Number(pendingSell.quantity)) throw new Error(`Sell quantity cannot exceed available stocks: ${num(pendingSell.quantity, 4)}`);
      await apiFetch(`/api/v1/portfolio/sell/${pendingSell.id}`, {
        method: 'POST',
        body: JSON.stringify({ quantity })
      });
    } else {
      await apiFetch(`/api/v1/portfolio/sell/${pendingSell.id}`, { method: 'POST' });
    }
    closeTradePreviewModal();
    closeSellModal();
    showTradeReceipt({
      title: `${receipt.assetType} Sell Receipt`,
      ...receipt,
      status: 'Completed'
    });
    await refreshDashboardAndHoldings();
    await loadTransactions();
    if (document.getElementById('section-marketplace').classList.contains('active')) await loadMarketplace();
    const alertResult = await evaluateAlerts(true);
    navigateTo('dashboard');
    if (alertResult.newCount > 0) setTimeout(scrollToDashboardLiveAlerts, 120);
    pushActionAlert('Sell completed. Alerts were checked right away.', 'warning', 7000);
  } catch (error) {
    pushActionAlert(`Sell failed: ${error.message}`, 'danger');
  }
}

function openTradePreviewModal(title, contentHtml, actionLabel) {
  document.getElementById('trade-preview-title').textContent = title;
  document.getElementById('trade-preview-content').innerHTML = contentHtml;
  document.getElementById('trade-preview-confirm-btn').textContent = actionLabel;
  document.getElementById('trade-preview-modal').classList.add('open');
}

function closeTradePreviewModal() {
  document.getElementById('trade-preview-modal').classList.remove('open');
}

function closeTradeReceiptModal() {
  document.getElementById('trade-receipt-modal').classList.remove('open');
}

async function confirmTradePreview() {
  if (!tradePreviewContext) return;
  if (tradePreviewContext.kind === 'BUY') {
    await submitAddAsset();
  } else {
    await confirmSell();
  }
}

async function buildAddTradePreview() {
  const type = document.getElementById('add-type').value;
  if (type === 'STOCK') {
    const symbol = val('stock-symbol').toUpperCase();
    const assetName = val('stock-name');
    const quantity = numberValue('stock-quantity');
    const unitPrice = numberValue('stock-price');
    if (!symbol || !assetName || quantity == null || unitPrice == null) throw new Error('Please fill all stock fields');
    const holdings = safeArray(await apiFetch('/api/v1/portfolio/holdings?type=STOCK'));
    const existing = holdings.find(row => String(row.symbol || '').toUpperCase() === symbol);
    const existingQty = Number(existing?.quantity || 0);
    const existingAvg = Number(existing?.purchase_price || 0);
    const totalAmount = quantity * unitPrice;
    const afterQty = existingQty + quantity;
    const afterAvg = afterQty > 0 ? (((existingQty * existingAvg) + totalAmount) / afterQty) : unitPrice;
    return buildTradePreviewModel({ kind: 'BUY', assetType: 'STOCK', symbol, assetName, quantity, unitPrice, totalAmount, remainingLabel: 'Shares after buy', remainingValue: num(afterQty, 4), extraLabel: 'Avg price after buy', extraValue: fmt(afterAvg) });
  }

  if (type === 'CRYPTO') {
    const symbol = val('crypto-symbol').toUpperCase();
    const assetName = val('crypto-name');
    const quantity = numberValue('crypto-quantity');
    const unitPrice = numberValue('crypto-buy-price');
    if (!symbol || !assetName || quantity == null || unitPrice == null) throw new Error('Please fill all crypto fields');
    const holdings = safeArray(await apiFetch('/api/v1/crypto'));
    const existing = holdings.find(row => String(row.symbol || '').toUpperCase() === symbol);
    const existingQty = Number(existing?.quantity || 0);
    const totalAmount = quantity * unitPrice;
    return buildTradePreviewModel({ kind: 'BUY', assetType: 'CRYPTO', symbol, assetName, quantity, unitPrice, totalAmount, remainingLabel: 'Quantity after buy', remainingValue: num(existingQty + quantity, 8), extraLabel: 'Current tracked price', extraValue: fmt(numberValue('crypto-current-price') || unitPrice) });
  }

  const assetName = val('bond-issuer');
  const amount = numberValue('bond-amount');
  const rate = numberValue('bond-rate');
  const months = numberValue('bond-tenure');
  if (!assetName || amount == null || rate == null || months == null || !val('bond-start')) throw new Error('Please fill all bond fields');
  const estimatedMaturity = amount + (amount * rate * months / 1200);
  return buildTradePreviewModel({ kind: 'BUY', assetType: 'BOND', symbol: assetName.toUpperCase().replace(/[^A-Z0-9]/g, '').slice(0, 12) || 'BOND', assetName, quantity: 1, unitPrice: amount, totalAmount: amount, remainingLabel: 'Estimated maturity value', remainingValue: fmt(estimatedMaturity), extraLabel: 'Tenure / Rate', extraValue: `${months} months @ ${rate}%` });
}

function buildSellTradePreview() {
  if (!pendingSell) throw new Error('No asset selected to sell');
  if (pendingSell.type === 'STOCK') {
    const quantity = numberValue('sell-quantity');
    if (quantity == null || quantity <= 0) throw new Error('Enter a valid sell quantity');
    if (Number(quantity) > Number(pendingSell.quantity)) throw new Error(`Sell quantity cannot exceed available stocks: ${num(pendingSell.quantity, 4)}`);
    const unitPrice = Number(pendingSell.currentPrice || pendingSell.buyPrice || 0);
    return buildTradePreviewModel({ kind: 'SELL', assetType: 'STOCK', symbol: pendingSell.symbol || pendingSell.name, assetName: pendingSell.name, quantity, unitPrice, totalAmount: quantity * unitPrice, remainingLabel: 'Shares after sell', remainingValue: num(Number(pendingSell.quantity) - quantity, 4), extraLabel: 'Available before sell', extraValue: num(pendingSell.quantity, 4) });
  }
  if (pendingSell.type === 'CRYPTO') {
    const quantity = numberValue('sell-quantity');
    if (quantity == null || quantity <= 0) throw new Error('Enter a valid sell quantity');
    if (Number(quantity) > Number(pendingSell.quantity)) throw new Error('Sell quantity cannot exceed current holdings');
    const unitPrice = Number(pendingSell.currentPrice || 0);
    return buildTradePreviewModel({ kind: 'SELL', assetType: 'CRYPTO', symbol: pendingSell.symbol, assetName: pendingSell.name, quantity, unitPrice, totalAmount: quantity * unitPrice, remainingLabel: 'Quantity after sell', remainingValue: num(Number(pendingSell.quantity) - quantity, 8), extraLabel: 'Available before sell', extraValue: num(pendingSell.quantity, 8) });
  }
  return buildTradePreviewModel({ kind: 'SELL', assetType: 'BOND', symbol: pendingSell.symbol || pendingSell.name, assetName: pendingSell.name, quantity: 1, unitPrice: Number(pendingSell.currentPrice || 0), totalAmount: Number(pendingSell.currentPrice || 0), remainingLabel: 'Remaining position', remainingValue: 'Closed', extraLabel: 'Action', extraValue: 'Entire bond holding will be sold' });
}

function buildTradePreviewModel({ kind, assetType, symbol, assetName, quantity, unitPrice, totalAmount, remainingLabel, remainingValue, extraLabel, extraValue }) {
  const html = `
    <div class="receipt-grid">
      <div><span>Action</span><strong>${esc(kind)}</strong></div>
      <div><span>Asset Type</span><strong>${esc(assetType)}</strong></div>
      <div><span>Symbol</span><strong>${esc(symbol)}</strong></div>
      <div><span>Name</span><strong>${esc(assetName)}</strong></div>
      <div><span>Quantity</span><strong>${num(quantity, 8)}</strong></div>
      <div><span>Unit Price</span><strong>${fmt(unitPrice)}</strong></div>
      <div><span>Amount ${kind === 'BUY' ? 'Deducted' : 'Credited'}</span><strong>${fmt(totalAmount)}</strong></div>
      <div><span>${esc(remainingLabel)}</span><strong>${esc(remainingValue)}</strong></div>
      <div><span>${esc(extraLabel)}</span><strong>${esc(extraValue)}</strong></div>
    </div>
    <p class="receipt-meta">Review this summary before confirming. A receipt will be generated after the trade completes.</p>`;
  return { kind, assetType, symbol, assetName, quantity, unitPrice, totalAmount, remainingLabel, remainingValue, extraLabel, extraValue, html };
}

function showTradeReceipt(receipt) {
  document.getElementById('trade-receipt-title').textContent = receipt.title || 'Trade Receipt';
  document.getElementById('trade-receipt-content').innerHTML = `
    <div class="receipt-grid">
      <div><span>Status</span><strong>${esc(receipt.status || 'Completed')}</strong></div>
      <div><span>Action</span><strong>${esc(receipt.kind || '')}</strong></div>
      <div><span>Asset Type</span><strong>${esc(receipt.assetType || '')}</strong></div>
      <div><span>Symbol</span><strong>${esc(receipt.symbol || '')}</strong></div>
      <div><span>Name</span><strong>${esc(receipt.assetName || '')}</strong></div>
      <div><span>Quantity</span><strong>${num(receipt.quantity || 0, 8)}</strong></div>
      <div><span>Unit Price</span><strong>${fmt(receipt.unitPrice || 0)}</strong></div>
      <div><span>Total ${receipt.kind === 'SELL' ? 'Credited' : 'Deducted'}</span><strong>${fmt(receipt.totalAmount || 0)}</strong></div>
      <div><span>${esc(receipt.remainingLabel || 'Remaining')}</span><strong>${esc(receipt.remainingValue || '—')}</strong></div>
      <div><span>${esc(receipt.extraLabel || 'Summary')}</span><strong>${esc(receipt.extraValue || '—')}</strong></div>
    </div>
    <p class="receipt-meta">Receipt generated at ${esc(new Date().toLocaleString())}.</p>`;
  document.getElementById('trade-receipt-modal').classList.add('open');
  tradePreviewContext = null;
}

// ─── Crypto Detail Actions ────────────────────────────────────────────────
async function showCryptoDetails(cryptoId) {
  try {
    const crypto = normalizeCrypto(await apiFetch(`/api/v1/crypto/${cryptoId}`));
    const content = document.getElementById('crypto-details-content');
    content.innerHTML = `
      <div class="details-grid">
        <div><span>Crypto ID</span><strong>${esc(crypto.cryptoId)}</strong></div>
        <div><span>Symbol</span><strong>${esc(crypto.symbol)}</strong></div>
        <div><span>Name</span><strong>${esc(crypto.name)}</strong></div>
        <div><span>Quantity</span><strong>${num(crypto.quantity, 8)}</strong></div>
        <div><span>Buy Price</span><strong>${fmt(crypto.buyPrice)}</strong></div>
        <div><span>Current Price</span><strong>${fmt(crypto.currentPrice)}</strong></div>
        <div><span>Invested Amount</span><strong>${fmt(crypto.investedAmount)}</strong></div>
        <div><span>Current Value</span><strong>${fmt(crypto.currentValue)}</strong></div>
        <div><span>Profit / Loss</span><strong class="${Number(crypto.profitLoss || 0) >= 0 ? 'pos' : 'neg'}">${Number(crypto.profitLoss || 0) >= 0 ? '+' : ''}${fmt(crypto.profitLoss)}</strong></div>
      </div>`;
    document.getElementById('crypto-details-modal').classList.add('open');
  } catch (error) {
    alert(`Unable to load crypto details: ${error.message}`);
  }
}

function closeCryptoDetailsModal() {
  document.getElementById('crypto-details-modal').classList.remove('open');
}

async function refreshCryptoPrice(symbol, silent = false) {
  try {
    await apiFetch(`/api/v1/crypto/${encodeURIComponent(symbol)}/price`, { method: 'PUT' });
    if (!silent) {
      await refreshDashboardAndHoldings();
      if (document.getElementById('section-marketplace').classList.contains('active')) await loadMarketplace();
    }
  } catch (error) {
    if (!silent) alert(`Refresh failed: ${error.message}`);
  }
}

async function deleteCryptoHolding(cryptoId, name) {
  if (!window.confirm(`Delete ${name}? This removes the crypto row from the backend.`)) return;
  try {
    await apiFetch(`/api/v1/crypto/${cryptoId}`, { method: 'DELETE' });
    await refreshDashboardAndHoldings();
    await loadTransactions();
    if (document.getElementById('section-marketplace').classList.contains('active')) await loadMarketplace();
  } catch (error) {
    alert(`Delete failed: ${error.message}`);
  }
}

// ─── Crypto Symbol Auto-fetch ─────────────────────────────────────────────
function onCryptoSymbolInput() {
  clearTimeout(cryptoLookupTimer);
  cryptoLookupTimer = setTimeout(fetchCryptoInfo, 700);
}

async function fetchCryptoInfo() {
  const symbolInput = document.getElementById('crypto-symbol');
  const nameInput   = document.getElementById('crypto-name');
  const priceInput  = document.getElementById('crypto-current-price');
  const statusEl    = document.getElementById('crypto-lookup-status');
  const symbol = symbolInput.value.trim().toUpperCase();
  if (!symbol || symbol.length < 2) { if (statusEl) statusEl.textContent = ''; return; }
  if (statusEl) statusEl.textContent = '🔍 Fetching info...';
  try {
    const data = await apiFetch(`/api/v1/crypto/lookup/${encodeURIComponent(symbol)}`);
    if (data.name && !nameInput.value.trim()) nameInput.value = data.name;
    if (data.currentPrice && Number(data.currentPrice) > 0) {
      priceInput.value = Number(data.currentPrice).toFixed(2);
      if (statusEl) statusEl.innerHTML = `<span style="color:var(--success);">✅ ${esc(data.name)} — $${Number(data.currentPrice).toLocaleString('en-US', {minimumFractionDigits:2,maximumFractionDigits:2})}</span>`;
    } else {
      if (statusEl) statusEl.innerHTML = `<span style="color:var(--text-muted);">ℹ️ ${esc(data.name)} — no live price available</span>`;
    }
  } catch (err) {
    if (statusEl) statusEl.innerHTML = `<span style="color:var(--danger);">⚠️ Could not fetch info for "${esc(symbol)}"</span>`;
    console.warn('Crypto symbol lookup failed:', err.message);
  }
}

// ─── Settings ─────────────────────────────────────────────────────────────
function onSettingsAction(action) {
  const statusEl = document.getElementById('settings-status');
  const previewEl = document.getElementById('settings-profile-preview');
  if (!statusEl || !previewEl) return;

  const messages = {
    addProfile: 'Demo: New profile form opened.',
    editProfile: 'Demo: Profile edit mode enabled.',
    showProfile: 'Demo: Showing profile snapshot below.',
    savePreferences: 'Demo: Preferences saved successfully.',
    changePassword: 'Demo: Password reset workflow started.',
    enable2fa: 'Demo: Two-factor authentication setup started.',
    sessionLog: 'Demo: Recent sessions loaded.',
    exportData: 'Demo: Data export queued.',
    downloadReport: 'Demo: Monthly report generated.',
    deactivate: 'Demo: Account deactivation requires confirmation.'
  };

  const isWarn = action === 'deactivate';
  statusEl.className = isWarn ? 'status-error' : 'status-success';
  statusEl.textContent = `${isWarn ? '⚠️' : '✅'} ${messages[action] || 'Action completed.'}`;

  if (action === 'showProfile') {
    previewEl.innerHTML = [
      '<strong>Profile Preview</strong>',
      'Name: Portfolio Manager',
      'Email: manager@portfolio.local',
      `Preferred Currency: ${esc(document.getElementById('settings-currency')?.value || 'USD')}`,
      `Language: ${esc(document.getElementById('settings-language')?.value || 'English')}`
    ].join('<br>');
  } else if (action === 'savePreferences') {
    previewEl.innerHTML = `Saved: ${esc(document.getElementById('settings-currency')?.value || 'USD')} / ${esc(document.getElementById('settings-language')?.value || 'English')}.`;
  } else {
    previewEl.innerHTML = '';
  }
}

// ─── Alerts Center ─────────────────────────────────────────────────────────
function initAlerts() {
  if (!localStorage.getItem(ALERT_RULES_KEY)) localStorage.setItem(ALERT_RULES_KEY, '[]');
  if (!localStorage.getItem(ALERT_EVENTS_KEY)) localStorage.setItem(ALERT_EVENTS_KEY, '[]');
  if (!localStorage.getItem(ALERT_SNAPSHOTS_KEY)) localStorage.setItem(ALERT_SNAPSHOTS_KEY, '{}');
  renderAlertsBadge();
  if (alertPollTimer) clearInterval(alertPollTimer);
  alertPollTimer = setInterval(() => {
    evaluateAlerts(false).catch(error => console.warn('Alert poll failed:', error.message));
  }, 60000);
  evaluateAlerts(false).catch(() => {});
}

function onAlertTypeChange() {
  const type = val('alert-type');
  const assetTypeEl = document.getElementById('alert-asset-type');
  const symbolEl = document.getElementById('alert-symbol');
  const keywordEl = document.getElementById('alert-keyword');
  if (!assetTypeEl || !symbolEl || !keywordEl) return;

  if (type === 'GOAL_PORTFOLIO_VALUE') {
    assetTypeEl.value = 'PORTFOLIO';
    symbolEl.placeholder = 'Optional note';
  } else if (type === 'RISK_CONCENTRATION') {
    assetTypeEl.value = 'PORTFOLIO';
    symbolEl.placeholder = 'STOCK, BOND or CRYPTO';
  } else {
    symbolEl.placeholder = 'e.g. AAPL or BTCUSD';
    keywordEl.placeholder = 'Optional note';
  }
}

function loadAlertRules() {
  try {
    const rules = safeArray(JSON.parse(localStorage.getItem(ALERT_RULES_KEY) || '[]'));
    const filtered = rules.filter(rule => ALLOWED_ALERT_TYPES.includes(rule?.type));
    if (filtered.length !== rules.length) saveAlertRules(filtered);
    return filtered;
  } catch {
    return [];
  }
}

function saveAlertRules(rules) {
  localStorage.setItem(ALERT_RULES_KEY, JSON.stringify(rules));
}

function loadAlertEvents() {
  try {
    return safeArray(JSON.parse(localStorage.getItem(ALERT_EVENTS_KEY) || '[]'));
  } catch {
    return [];
  }
}

function saveAlertEvents(events) {
  localStorage.setItem(ALERT_EVENTS_KEY, JSON.stringify(events.slice(0, 100)));
}

function loadAlertSnapshots() {
  try {
    return JSON.parse(localStorage.getItem(ALERT_SNAPSHOTS_KEY) || '{}');
  } catch {
    return {};
  }
}

function saveAlertSnapshots(snapshots) {
  localStorage.setItem(ALERT_SNAPSHOTS_KEY, JSON.stringify(snapshots));
}

function saveAlertRule() {
  const type = val('alert-type');
  const assetType = val('alert-asset-type');
  const symbol = val('alert-symbol').toUpperCase();
  const threshold = numberValue('alert-threshold');
  const keyword = val('alert-keyword');

  if (!ALLOWED_ALERT_TYPES.includes(type)) {
    setAlertsStatus('This alert type is no longer supported.', true);
    return;
  }

  if (['PRICE_ABOVE', 'DAILY_CHANGE', 'PROFIT_LOSS', 'RISK_CONCENTRATION', 'GOAL_PORTFOLIO_VALUE'].includes(type) && threshold == null) {
    setAlertsStatus('Threshold is required for this alert type.', true);
    return;
  }
  if (['PRICE_ABOVE', 'DAILY_CHANGE', 'PROFIT_LOSS'].includes(type) && !symbol) {
    setAlertsStatus('Symbol / scope is required for this alert type.', true);
    return;
  }

  const rules = loadAlertRules();
  rules.unshift({
    id: `rule-${Date.now()}`,
    type,
    assetType,
    symbol,
    threshold,
    keyword,
    createdAt: new Date().toISOString(),
    active: true
  });
  saveAlertRules(rules);
  renderAlertsSection();
  setAlertsStatus('Alert rule saved successfully.', false);
  pushActionAlert('Alert rule saved. It will be checked automatically.', 'warning', 6000);
  evaluateAlerts(true).catch(error => setAlertsStatus(error.message, true));
}

function deleteAlertRule(ruleId) {
  saveAlertRules(loadAlertRules().filter(rule => rule.id !== ruleId));
  renderAlertsSection();
}

function markAlertsRead() {
  const events = loadAlertEvents().map(event => ({ ...event, read: true }));
  saveAlertEvents(events);
  renderAlertsSection();
  renderAlertsBadge();
}

function renderAlertsBadge() {
  const badge = document.getElementById('alerts-nav-badge');
  if (!badge) return;
  const unread = loadAlertEvents().filter(event => !event.read).length;
  badge.style.display = unread ? 'inline-flex' : 'none';
  badge.textContent = String(unread);
}

function renderAlertsSection() {
  const rulesContainer = document.getElementById('alert-rules-container');
  const eventsContainer = document.getElementById('alert-events-container');
  if (!rulesContainer || !eventsContainer) return;

  const rules = loadAlertRules();
  const events = loadAlertEvents();

  rulesContainer.innerHTML = rules.length ? rules.map(rule => `
    <div class="alert-rule-card">
      <div class="alert-title">${esc(rule.type.replaceAll('_', ' '))}</div>
      <div class="alert-pill-row">
        <span class="alert-pill">${esc(rule.assetType || 'GENERAL')}</span>
        ${rule.symbol ? `<span class="alert-pill">${esc(rule.symbol)}</span>` : ''}
        ${rule.threshold != null ? `<span class="alert-pill">Threshold: ${esc(rule.threshold)}</span>` : ''}
      </div>
      ${rule.keyword ? `<div class="receipt-meta">Keyword / note: ${esc(rule.keyword)}</div>` : ''}
      <div class="receipt-meta">Created ${fmtDate(rule.createdAt)}</div>
      <div class="settings-actions" style="margin-top:10px;">
        <button class="btn btn-secondary btn-sm" onclick="deleteAlertRule('${escJs(rule.id)}')">Delete</button>
      </div>
    </div>`).join('') : emptyState('No alert rules configured yet.');

  eventsContainer.innerHTML = events.length ? events.map(event => `
    <div class="alert-event-card severity-${alertLevelForEvent(event)} ${event.read ? '' : 'unread'}">
      <div class="alert-title">${alertIconForEvent(event)} ${esc(event.title)}</div>
      <div>${esc(event.message)}</div>
      <div class="alert-pill-row">
        <span class="alert-pill">${esc(event.category)}</span>
        ${event.symbol ? `<span class="alert-pill">${esc(event.symbol)}</span>` : ''}
      </div>
      <div class="receipt-meta">${fmtDate(event.timestamp)}</div>
    </div>`).join('') : emptyState('No triggered alerts yet.');

  renderAlertsBadge();
  renderDashboardAlerts();
}

function setAlertsStatus(message, isError) {
  const statusEl = document.getElementById('alerts-status');
  if (!statusEl) return;
  statusEl.innerHTML = `<span class="${isError ? 'status-error' : 'status-success'}">${esc(message)}</span>`;
}

async function evaluateAlerts(showStatus = false) {
  const rules = loadAlertRules().filter(rule => rule.active !== false);
  const context = await buildAlertContext(rules);
  const events = loadAlertEvents();
  let newCount = 0;
  const newEvents = [];

  for (const rule of rules) {
    const triggered = await evaluateAlertRule(rule, context);
    if (!triggered) continue;
    if (tryAddTriggeredEvent(events, newEvents, triggered)) newCount += 1;
  }

  for (const systemEvent of evaluateAutomaticMarketAlerts(context)) {
    if (tryAddTriggeredEvent(events, newEvents, systemEvent)) newCount += 1;
  }

  saveAlertEvents(events);
  renderAlertsSection();
  for (const event of newEvents.slice(0, 3)) {
    pushActionAlert(`${event.title}: ${event.message}`, alertLevelForEvent(event), 11000);
  }
  if (showStatus) {
    if (!rules.length && !newCount) {
      setAlertsStatus('No custom rules found. Showing automatic daily alerts only.', false);
    } else {
      setAlertsStatus(newCount ? `${newCount} new alert(s) triggered.` : 'Alerts checked. No new triggers.', false);
    }
  }
  return { newCount, newEvents };
}

function tryAddTriggeredEvent(events, newEvents, triggered) {
  if (!triggered) return false;
  const duplicate = events.some(event => event.signature === triggered.signature);
  if (duplicate) return false;
  events.unshift(triggered);
  newEvents.push(triggered);
  return true;
}

function evaluateAutomaticMarketAlerts(context) {
  const dayKey = new Date().toISOString().slice(0, 10);
  const systemEvents = [];

  const btcDrop = Number(context.cryptoQuotes.BTCUSD?.dailyChangePercent);
  if (Number.isFinite(btcDrop) && btcDrop <= -5) {
    systemEvents.push(createSystemAlertEvent(
      'DAILY_CHANGE',
      'Bitcoin fell sharply',
      `Bitcoin fell ${Math.abs(btcDrop).toFixed(1)}% today.`,
      `system:btc:drop:${dayKey}`,
      'danger',
      'BTCUSD'
    ));
  }

  const portfolioValue = Number(context.summary?.totalPortfolioValue || 0);
  if (portfolioValue >= 500000) {
    systemEvents.push(createSystemAlertEvent(
      'GOAL_PORTFOLIO_VALUE',
      'Portfolio milestone reached',
      `Portfolio value crossed ${formatInr(500000)}. Current: ${formatInr(portfolioValue)}.`,
      `system:portfolio:value:${dayKey}`,
      'info',
      'PORTFOLIO'
    ));
  }

  const cryptoPercent = Number(context.summary?.cryptoPercent || 0);
  if (cryptoPercent >= 20) {
    systemEvents.push(createSystemAlertEvent(
      'RISK_CONCENTRATION',
      'Crypto concentration risk',
      `Crypto allocation is now ${cryptoPercent.toFixed(1)}% (Target: 20%).`,
      `system:risk:crypto:${dayKey}`,
      'risk',
      'CRYPTO'
    ));
  }

  return systemEvents;
}

async function buildAlertContext(rules) {
  const summary = await apiFetch('/api/v1/portfolio/summary').catch(() => ({}));
  const genericHoldings = safeArray(await apiFetch('/api/v1/portfolio/holdings?type=ALL').catch(() => []));
  const stockHoldings = safeArray(await apiFetch(`/api/portfolios/${DEFAULT_PORTFOLIO_ID}/stocks/holdings`).catch(() => []));
  const cryptoHoldings = safeArray(await apiFetch('/api/v1/crypto').catch(() => []));
  const stockSymbols = [...new Set(rules.filter(rule => rule.assetType === 'STOCK').map(rule => rule.symbol).filter(Boolean))];
  const cryptoSymbols = [...new Set(['BTCUSD', ...rules.filter(rule => rule.assetType === 'CRYPTO').map(rule => rule.symbol).filter(Boolean)])];
  const snapshots = loadAlertSnapshots();

  const stockQuotes = {};
  for (const symbol of stockSymbols) {
    try {
      const details = await apiFetch(`/api/stocks/${encodeURIComponent(symbol)}`);
      stockQuotes[symbol] = {
        companyName: details?.companyName || symbol,
        currentPrice: Number(details?.quote?.currentPrice || 0),
        dailyChangePercent: Number(details?.quote?.changePercent || 0)
      };
    } catch {}
  }

  const cryptoQuotes = {};
  for (const symbol of cryptoSymbols) {
    try {
      let crypto;
      try {
        crypto = await apiFetch(`/api/v1/crypto/symbol/${encodeURIComponent(symbol)}`);
      } catch {
        crypto = await apiFetch(`/api/v1/crypto/${encodeURIComponent(symbol)}/price`, { method: 'PUT' });
      }
      const currentPrice = Number(crypto?.currentPrice || 0);
      const todayKey = new Date().toISOString().slice(0, 10);
      const snapshot = snapshots[symbol];
      const openingPrice = snapshot?.dayKey === todayKey && Number(snapshot?.openingPrice) > 0
        ? Number(snapshot.openingPrice)
        : currentPrice;
      let dailyChangePercent = Number(crypto?.dailyChangePercent || 0);
      if ((!Number.isFinite(dailyChangePercent) || dailyChangePercent === 0) && openingPrice > 0) {
        dailyChangePercent = ((currentPrice - openingPrice) / openingPrice) * 100;
      }
      cryptoQuotes[symbol] = { currentPrice, dailyChangePercent };
      snapshots[symbol] = { openingPrice, lastPrice: currentPrice, dayKey: todayKey, timestamp: new Date().toISOString() };
    } catch {}
  }
  saveAlertSnapshots(snapshots);

  const newsBySymbol = {};
  for (const rule of rules.filter(rule => rule.type === 'NEWS' && rule.symbol)) {
    if (newsBySymbol[rule.symbol]) continue;
    try {
      newsBySymbol[rule.symbol] = safeArray(await apiFetch(`/api/stocks/${encodeURIComponent(rule.symbol)}/news?limit=5`));
    } catch {
      newsBySymbol[rule.symbol] = [];
    }
  }

  return { summary, genericHoldings, stockHoldings, cryptoHoldings, stockQuotes, cryptoQuotes, newsBySymbol };
}

async function evaluateAlertRule(rule, context) {
  switch (rule.type) {
    case 'PRICE_ABOVE':
      return evaluatePriceRule(rule, context, price => price >= Number(rule.threshold), 'Price moved above target');
    case 'DAILY_CHANGE':
      return evaluateDailyChangeRule(rule, context);
    case 'PROFIT_LOSS':
      return evaluateProfitLossRule(rule, context);
    case 'GOAL_PORTFOLIO_VALUE':
      return evaluateGoalRule(rule, Number(context.summary?.totalPortfolioValue || 0), 'Portfolio value goal reached', fmt(Number(context.summary?.totalPortfolioValue || 0)));
    case 'RISK_CONCENTRATION':
      return evaluateRiskConcentrationRule(rule, context);
    default:
      return null;
  }
}

function evaluatePriceRule(rule, context, predicate, title) {
  const price = currentPriceForRule(rule, context);
  if (!Number.isFinite(price) || !predicate(price)) return null;
  const companyName = context.stockQuotes[rule.symbol]?.companyName || rule.symbol;
  if (title.includes('above')) {
    return createAlertEvent(rule, title, `${companyName} crossed your target price of ${fmt(rule.threshold)}.`, undefined, 'success');
  }
  return createAlertEvent(rule, title, `${companyName} fell below your target price of ${fmt(rule.threshold)}. Current: ${fmt(price)}.`, undefined, 'warning');
}

function evaluateDailyChangeRule(rule, context) {
  const change = currentDailyChangeForRule(rule, context);
  if (!Number.isFinite(change) || Math.abs(change) < Number(rule.threshold)) return null;
  return createAlertEvent(rule, 'Daily price change alert', `${rule.symbol} moved ${change.toFixed(2)}% in a day, crossing your ${rule.threshold}% trigger.`, undefined, change < 0 ? 'danger' : 'info');
}

function evaluateProfitLossRule(rule, context) {
  let profitLossPercent = null;
  if (rule.assetType === 'STOCK') {
    const stock = context.stockHoldings.find(item => String(item.symbol || '').toUpperCase() === rule.symbol);
    profitLossPercent = stock ? Number(stock.unrealizedPnLPercent || 0) : null;
  } else if (rule.assetType === 'CRYPTO') {
    const crypto = context.cryptoHoldings.find(item => String(item.symbol || '').toUpperCase() === rule.symbol);
    profitLossPercent = crypto && Number(crypto.investedAmount || 0) > 0
      ? (Number(crypto.profitLoss || 0) / Number(crypto.investedAmount || 1)) * 100
      : null;
  } else if (rule.assetType === 'BOND') {
    const bond = context.genericHoldings.find(item => item.asset_type === 'BOND' && String(item.symbol || '').toUpperCase() === rule.symbol);
    profitLossPercent = bond && Number(bond.amount_invested || 0) > 0
      ? (Number(bond.profit_loss || 0) / Number(bond.amount_invested || 1)) * 100
      : null;
  }
  if (!Number.isFinite(profitLossPercent) || Math.abs(profitLossPercent) < Number(rule.threshold)) return null;
  return createAlertEvent(rule, 'Profit & loss alert', `${rule.symbol} P/L is ${profitLossPercent.toFixed(2)}%, crossing your ${rule.threshold}% threshold.`, undefined, profitLossPercent < 0 ? 'danger' : 'success');
}

function evaluateNewsRule(rule, context) {
  const keyword = String(rule.keyword || '').toLowerCase();
  const articles = safeArray(context.newsBySymbol[rule.symbol]);
  const match = articles.find(article => `${article.headline || ''} ${article.summary || ''}`.toLowerCase().includes(keyword));
  if (!match) return null;
  return createAlertEvent(rule, 'News alert', `${rule.symbol}: ${match.headline}`, `news:${rule.id}:${match.url}`, 'info');
}

function evaluateGoalRule(rule, currentValue, title, currentText) {
  if (!Number.isFinite(currentValue) || currentValue < Number(rule.threshold)) return null;
  return createAlertEvent(rule, title, `Current value is ${currentText}, meeting your target of ${rule.threshold}.`, undefined, 'success');
}

function evaluateRiskConcentrationRule(rule, context) {
  const scope = (rule.symbol || '').toUpperCase();
  const key = scope === 'BOND' ? 'bondsPercent' : scope === 'CRYPTO' ? 'cryptoPercent' : 'stocksPercent';
  const current = Number(context.summary?.[key] || 0);
  if (!Number.isFinite(current) || current < Number(rule.threshold)) return null;
  return createAlertEvent(rule, 'Risk concentration alert', `${scope || 'STOCK'} allocation is ${current.toFixed(2)}%, above your ${rule.threshold}% threshold.`, undefined, 'risk');
}

function evaluateRiskDrawdownRule(rule, context) {
  const current = Number(context.summary?.totalReturnsPercent || 0);
  const threshold = Number(rule.threshold || 0);
  if (!Number.isFinite(current) || current > threshold) return null;
  return createAlertEvent(rule, 'Risk drawdown alert', `Portfolio returns are ${current.toFixed(2)}%, below your limit of ${threshold}%.`, undefined, 'danger');
}

function evaluateMarketMoveRule(rule, context) {
  const change = currentDailyChangeForRule(rule, context);
  if (!Number.isFinite(change) || Math.abs(change) < Number(rule.threshold)) return null;
  return createAlertEvent(rule, 'Market alert', `${rule.symbol} moved ${change.toFixed(2)}%, crossing your market alert threshold.`, undefined, Math.abs(change) >= Number(rule.threshold) * 2 ? 'danger' : 'warning');
}

function currentPriceForRule(rule, context) {
  if (rule.assetType === 'STOCK' || rule.assetType === 'MARKET') return Number(context.stockQuotes[rule.symbol]?.currentPrice);
  if (rule.assetType === 'CRYPTO') return Number(context.cryptoQuotes[rule.symbol]?.currentPrice);
  return NaN;
}

function currentDailyChangeForRule(rule, context) {
  if (rule.assetType === 'STOCK' || rule.assetType === 'MARKET') return Number(context.stockQuotes[rule.symbol]?.dailyChangePercent);
  if (rule.assetType === 'CRYPTO') return Number(context.cryptoQuotes[rule.symbol]?.dailyChangePercent);
  return NaN;
}

function createAlertEvent(rule, title, message, customSignature, level) {
  return {
    id: `event-${Date.now()}-${Math.random().toString(16).slice(2, 8)}`,
    ruleId: rule.id,
    category: rule.type,
    symbol: rule.symbol,
    title,
    message,
    timestamp: new Date().toISOString(),
    read: false,
    level: normalizeAlertLevel(level),
    signature: customSignature || `${rule.id}:${new Date().toISOString().slice(0, 10)}:${title}`
  };
}

function createSystemAlertEvent(category, title, message, signature, level, symbol = '') {
  return {
    id: `event-${Date.now()}-${Math.random().toString(16).slice(2, 8)}`,
    ruleId: `system-${category}`,
    category,
    symbol,
    title,
    message,
    timestamp: new Date().toISOString(),
    read: false,
    level: normalizeAlertLevel(level),
    signature
  };
}

function formatInr(value) {
  if (!Number.isFinite(Number(value))) return '₹0';
  return `₹${Number(value).toLocaleString('en-IN', { maximumFractionDigits: 0 })}`;
}

// ─── API Helper ───────────────────────────────────────────────────────────
async function apiFetch(url, options = {}) {
  const response = await fetch(url, {
    headers: { 'Content-Type': 'application/json', ...(options.headers || {}) },
    ...options
  });
  if (!response.ok) {
    const errorBody = await response.json().catch(() => ({}));
    throw new Error(errorBody.message || `HTTP ${response.status}`);
  }
  if (response.status === 204) return {};
  return response.json();
}

// ─── Helpers ──────────────────────────────────────────────────────────────
function normalizeCryptos(rows) { return safeArray(rows).map(normalizeCrypto); }

function normalizeCrypto(row) {
  return {
    cryptoId: row.cryptoId, symbol: row.symbol, name: row.name,
    quantity: Number(row.quantity || 0), buyPrice: Number(row.buyPrice || 0),
    currentPrice: Number(row.currentPrice || 0), investedAmount: Number(row.investedAmount || 0),
    currentValue: Number(row.currentValue || 0), profitLoss: Number(row.profitLoss || 0)
  };
}

function activeCryptoHoldings(rows) {
  return normalizeCryptos(rows).filter(item => Number(item.quantity) > 0 || Number(item.currentValue) > 0 || Number(item.investedAmount) > 0);
}

async function refreshDashboardAndHoldings() {
  if (document.getElementById('section-dashboard').classList.contains('active')) await loadDashboard();
  if (document.getElementById('section-holdings').classList.contains('active')) await loadHoldings(document.getElementById('holdings-filter').value);
}

function safeArray(value) { return Array.isArray(value) ? value : []; }
function sumBy(rows, key) { return rows.reduce((sum, row) => sum + Number(row[key] || 0), 0); }

function numberValue(id) {
  const raw = val(id);
  if (!raw) return null;
  const parsed = Number(raw);
  return Number.isFinite(parsed) ? parsed : null;
}

function esc(value) {
  if (value == null) return '—';
  return String(value).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;').replace(/'/g,'&#39;');
}

function escJs(value) { return String(value ?? '').replace(/\\/g,'\\\\').replace(/'/g,"\\'"); }
function json(value) { return escJs(JSON.stringify(value)); }

function fmt(value) {
  if (value == null || Number.isNaN(Number(value))) return '—';
  return '$' + Number(value).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function num(value, decimals = 4) {
  if (value == null || Number.isNaN(Number(value))) return '—';
  return Number(value).toLocaleString('en-US', { minimumFractionDigits: 0, maximumFractionDigits: decimals });
}

function fmtDate(value) {
  if (!value) return '—';
  return new Date(value).toLocaleString('en-US', { dateStyle: 'medium', timeStyle: 'short' });
}

function fmtDateOnly(value) {
  if (!value) return '—';
  const m = String(value).match(/^(\d{4})-(\d{2})-(\d{2})/);
  if (m) return `${m[3]}-${m[2]}-${m[1]}`;
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return '—';
  return `${String(d.getDate()).padStart(2,'0')}-${String(d.getMonth()+1).padStart(2,'0')}-${d.getFullYear()}`;
}

function val(id) { return document.getElementById(id)?.value?.trim() || ''; }

function emptyState(message) {
  return `<div class="empty-state"><div style="font-size:3rem;">📭</div><p>${esc(message)}</p></div>`;
}
