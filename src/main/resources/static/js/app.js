'use strict';

const CRYPTO_MARKET_SYMBOLS = [
  'BTCUSD', 'ETHUSD', 'BNBUSD', 'XRPUSD', 'SOLUSD',
  'ADAUSD', 'DOGEUSD', 'TRXUSD', 'AVAXUSD', 'MATICUSD'
];

let pendingSell = null;
let allocationChart = null;
let performanceChart = null;
let cryptoLookupTimer = null;

// ─── Init ─────────────────────────────────────────────────────────────────
document.addEventListener('DOMContentLoaded', () => {
  initNavigation();
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
    case 'dashboard':
      loadDashboard();
      break;
    case 'holdings':
      loadHoldings(document.getElementById('holdings-filter').value);
      break;
    case 'marketplace':
      loadMarketplace();
      break;
    case 'transactions':
      loadTransactions();
      break;
    default:
      break;
  }
}

// ─── Dashboard ────────────────────────────────────────────────────────────
async function loadDashboard() {
  try {
    const cryptos = normalizeCryptos(await apiFetch('/api/v1/crypto'));
    renderDashboardSummary(cryptos);
    renderAllocationChart(cryptos);
    renderPerformanceChart(cryptos);
  } catch (error) {
    console.error('Dashboard error', error);
    document.getElementById('summary-cards').innerHTML = emptyState('Unable to load crypto dashboard right now.');
    document.getElementById('kpi-total-value').textContent = '—';
    document.getElementById('kpi-returns').textContent = '—';
    document.getElementById('kpi-returns-pct').textContent = '—';
  }
}

function renderDashboardSummary(cryptos) {
  const active = cryptos.filter(item => Number(item.quantity) > 0);
  const totalValue = sumBy(active, 'currentValue');
  const totalInvested = sumBy(active, 'investedAmount');
  const totalProfit = sumBy(active, 'profitLoss');
  const returnsPct = totalInvested > 0 ? (totalProfit / totalInvested) * 100 : 0;
  const bestHolding = [...active].sort((a, b) => Number(b.currentValue || 0) - Number(a.currentValue || 0))[0];
  const bestPerformer = [...active].sort((a, b) => Number(b.profitLoss || 0) - Number(a.profitLoss || 0))[0];
  const pos = totalProfit >= 0;

  document.getElementById('kpi-total-value').textContent = fmt(totalValue);
  document.getElementById('kpi-returns').textContent = `${pos ? '+' : ''}${fmt(totalProfit)}`;
  document.getElementById('kpi-returns-pct').textContent = `${pos ? '+' : ''}${returnsPct.toFixed(2)}%`;
  document.getElementById('kpi-returns-pct').style.color = pos ? 'var(--success)' : 'var(--danger)';

  const cards = [
    { label: 'Tracked Coins', value: cryptos.length, sub: `${active.length} active holding(s)` },
    { label: 'Total Invested', value: fmt(totalInvested), sub: '' },
    { label: 'Current Value', value: fmt(totalValue), sub: '' },
    { label: 'Profit / Loss', value: `${pos ? '+' : ''}${fmt(totalProfit)}`, sub: `${pos ? '+' : ''}${returnsPct.toFixed(2)}%`, pos },
    { label: 'Top Holding', value: bestHolding ? bestHolding.symbol : '—', sub: bestHolding ? fmt(bestHolding.currentValue) : 'No active holdings' },
    { label: 'Best Performer', value: bestPerformer ? bestPerformer.symbol : '—', sub: bestPerformer ? fmt(bestPerformer.profitLoss) : 'No active holdings', pos: bestPerformer ? Number(bestPerformer.profitLoss) >= 0 : undefined }
  ];

  document.getElementById('summary-cards').innerHTML = cards.map(card => `
    <div class="summary-card">
      <div class="sc-label">${esc(card.label)}</div>
      <div class="sc-value ${card.pos === true ? 'pos' : card.pos === false ? 'neg' : ''}">${typeof card.value === 'number' ? esc(card.value) : card.value}</div>
      ${card.sub ? `<div class="sc-sub">${esc(card.sub)}</div>` : ''}
    </div>
  `).join('');
}

function renderAllocationChart(cryptos) {
  const source = cryptos.filter(item => Number(item.currentValue) > 0);
  const labels = (source.length ? source : cryptos.slice(0, 5)).map(item => item.symbol);
  const values = (source.length ? source : cryptos.slice(0, 5)).map(item => Number(item.currentValue || item.currentPrice || 0));
  const ctx = document.getElementById('chart-allocation').getContext('2d');

  if (allocationChart) {
    allocationChart.destroy();
  }

  allocationChart = new Chart(ctx, {
    type: 'doughnut',
    data: {
      labels: labels.length ? labels : ['No Data'],
      datasets: [{
        data: values.length ? values : [1],
        backgroundColor: ['#f59e0b', '#8b5cf6', '#3b82f6', '#06b6d4', '#ec4899', '#10b981'],
        borderColor: getComputedStyle(document.documentElement).getPropertyValue('--surface').trim() || '#fff',
        borderWidth: 3
      }]
    },
    options: chartSharedOptions(false)
  });
}

function renderPerformanceChart(cryptos) {
  const source = cryptos.filter(item => Number(item.quantity) > 0 || Number(item.currentValue) > 0).slice(0, 8);
  const ctx = document.getElementById('chart-performance').getContext('2d');

  if (performanceChart) {
    performanceChart.destroy();
  }

  performanceChart = new Chart(ctx, {
    type: 'bar',
    data: {
      labels: source.length ? source.map(item => item.symbol) : ['No Data'],
      datasets: [
        {
          label: 'Invested',
          data: source.length ? source.map(item => Number(item.investedAmount || 0)) : [0],
          backgroundColor: 'rgba(79, 70, 229, 0.55)',
          borderColor: '#4f46e5',
          borderWidth: 1
        },
        {
          label: 'Current Value',
          data: source.length ? source.map(item => Number(item.currentValue || 0)) : [0],
          backgroundColor: 'rgba(16, 185, 129, 0.55)',
          borderColor: '#10b981',
          borderWidth: 1
        }
      ]
    },
    options: chartSharedOptions(true)
  });
}

function chartSharedOptions(showLegend) {
  const textColor = getComputedStyle(document.documentElement).getPropertyValue('--text').trim() || '#000';
  const mutedColor = getComputedStyle(document.documentElement).getPropertyValue('--text-muted').trim() || '#64748b';

  return {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: {
        display: showLegend,
        position: 'bottom',
        labels: { color: textColor }
      },
      tooltip: {
        callbacks: {
          label: context => `${context.dataset?.label ? `${context.dataset.label}: ` : ''}${fmt(context.raw)}`
        }
      }
    },
    scales: showLegend ? {
      x: {
        ticks: { color: mutedColor },
        grid: { color: 'rgba(128,128,128,.08)' }
      },
      y: {
        ticks: {
          color: mutedColor,
          callback: value => '$' + Number(value).toLocaleString('en-US')
        },
        grid: { color: 'rgba(128,128,128,.08)' }
      }
    } : undefined
  };
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
      container.innerHTML = cryptoRows.length
        ? buildCryptoTable(cryptoRows)
        : emptyState('No crypto holdings found. Buy crypto to see it here.');
      statusEl.innerHTML = `<span class="status-success">✅ ${cryptoRows.length} crypto holding(s) loaded</span>`;
      return;
    }

    const genericPromise = apiFetch(`/api/v1/portfolio/holdings?type=${type}`);

    if (type === 'ALL') {
      const [genericResult, cryptoResult] = await Promise.allSettled([
        genericPromise,
        apiFetch('/api/v1/crypto')
      ]);

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
    if (!genericRows.length) {
      container.innerHTML = emptyState(`No ${type.toLowerCase()} holdings found.`);
      return;
    }

    container.innerHTML = type === 'STOCK'
      ? buildStocksTable(genericRows)
      : buildBondsTable(genericRows);
    statusEl.innerHTML = `<span class="status-success">✅ ${genericRows.length} holding(s) loaded</span>`;
  } catch (error) {
    container.innerHTML = `<p class="status-error">❌ Failed: ${esc(error.message)}</p>`;
  }
}

function buildStocksTable(rows) {
  const head = `<tr>
    <th>Symbol</th><th>Company</th><th>Quantity</th>
    <th>Avg Buy Price</th><th>Current Price</th><th>Invested Amount</th>
    <th>P/L %</th><th>Action</th></tr>`;

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
      <td>${fmt(investedAmount)}</td>
      <td class="${plPct >= 0 ? 'pos' : 'neg'}">${plPct >= 0 ? '+' : ''}${plPct.toFixed(2)}%</td>
      <td><button class="sell-btn" onclick='openSellModal(${json({ id: row.asset_id, name: row.asset_name, type: "GENERIC" })})'>Sell</button></td>
    </tr>`;
  }).join('');

  return `<table class="data-table"><thead>${head}</thead><tbody>${body}</tbody></table>`;
}

function buildBondsTable(rows) {
  const head = `<tr>
    <th>Issuer</th><th>Coupon Rate</th><th>Maturity</th>
    <th>Qty</th><th>Face Value</th><th>Total Value</th>
    <th>P/L</th><th>Action</th></tr>`;

  const body = rows.map(row => {
    const profitLoss = Number(row.profit_loss || 0);
    return `<tr>
      <td><strong>${esc(row.issuer || row.asset_name)}</strong></td>
      <td>${num(row.interest_rate)}%</td>
      <td>${esc(row.maturity_date)}</td>
      <td>1</td>
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
    <th>Symbol</th><th>Company</th><th>Quantity</th>
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
          <button class="detail-btn" onclick="refreshCryptoPrice('${escJs(row.symbol)}')">Refresh</button>
          <button class="sell-btn" onclick='openSellModal(${json({ cryptoId: row.cryptoId, symbol: row.symbol, name: row.name, quantity: row.quantity, buyPrice: row.buyPrice, currentPrice: row.currentPrice, type: "CRYPTO" })})'>Sell</button>
          <button class="detail-btn danger-outline" onclick="deleteCryptoHolding(${row.cryptoId}, '${escJs(row.name || row.symbol)}')">Delete</button>
        </div>
      </td>
    </tr>`;
  }).join('');

  return `<table class="data-table"><thead>${head}</thead><tbody>${body}</tbody></table>`;
}

// ─── Marketplace ──────────────────────────────────────────────────────────
async function loadMarketplace() {
  const marketGrid = document.getElementById('market-grid');
  const marketStatus = document.getElementById('market-status');
  marketGrid.innerHTML = '<p style="color:var(--text-muted);padding:12px 0;">Refreshing market data...</p>';
  marketStatus.innerHTML = '';

  try {
    await refreshMarketplacePrices(true);
    const marketData = normalizeCryptos(await apiFetch('/api/v1/crypto/batch', {
      method: 'POST',
      body: JSON.stringify(CRYPTO_MARKET_SYMBOLS)
    }));

    const sorted = [...marketData].sort((a, b) => Number(b.currentPrice || 0) - Number(a.currentPrice || 0));
    marketGrid.innerHTML = sorted.length ? buildMarketplaceCards(sorted) : emptyState('No marketplace crypto data available.');
    marketStatus.innerHTML = `<span class="status-success">✅ ${sorted.length} crypto asset(s) loaded</span>`;
  } catch (error) {
    marketGrid.innerHTML = `<p class="status-error">❌ Failed: ${esc(error.message)}</p>`;
  }
}

function buildMarketplaceCards(rows) {
  return `<div class="market-grid">${rows.map(row => {
    const profitClass = Number(row.profitLoss || 0) >= 0 ? 'pos' : 'neg';
    return `
      <article class="market-card">
        <div class="market-card-header">
          <div>
            <h3>${esc(row.symbol)}</h3>
            <p>${esc(row.name)}</p>
          </div>
          <span class="badge badge-crypto">CRYPTO</span>
        </div>
        <div class="market-metrics">
          <div><span>Current Price</span><strong>${fmt(row.currentPrice)}</strong></div>
          <div><span>Tracked Value</span><strong>${fmt(row.currentValue)}</strong></div>
          <div><span>P/L</span><strong class="${profitClass}">${Number(row.profitLoss || 0) >= 0 ? '+' : ''}${fmt(row.profitLoss)}</strong></div>
        </div>
        <div class="market-card-actions">
          <button class="btn btn-secondary btn-sm" onclick="showCryptoDetails(${row.cryptoId})">Details</button>
          <button class="btn btn-secondary btn-sm" onclick="refreshCryptoPrice('${escJs(row.symbol)}', true)">Refresh</button>
          <button class="btn btn-primary btn-sm" onclick='openAddModal("CRYPTO", ${json({ symbol: row.symbol, name: row.name, currentPrice: row.currentPrice })})'>Buy</button>
        </div>
      </article>
    `;
  }).join('')}</div>`;
}

async function refreshMarketplace(silent = false) {
  await loadMarketplace();
  if (!silent) {
    await refreshDashboardAndHoldings();
  }
}

async function refreshMarketplacePrices(silent = false) {
  const results = await Promise.allSettled(
    CRYPTO_MARKET_SYMBOLS.map(symbol => apiFetch(`/api/v1/crypto/${encodeURIComponent(symbol)}/price`, { method: 'PUT' }))
  );

  if (!silent) {
    const failures = results.filter(result => result.status === 'rejected');
    const marketStatus = document.getElementById('market-status');
    marketStatus.innerHTML = failures.length
      ? `<span class="status-error">⚠️ Refreshed with ${failures.length} warning(s)</span>`
      : '<span class="status-success">✅ Market prices refreshed</span>';
  }
}

async function lookupCryptoBySymbol() {
  const symbolInput = document.getElementById('market-symbol-input');
  const lookupContainer = document.getElementById('market-lookup');
  const symbol = symbolInput.value.trim().toUpperCase();

  if (!symbol) {
    lookupContainer.innerHTML = '<p class="status-error">❌ Enter a symbol like BTCUSD.</p>';
    return;
  }

  lookupContainer.innerHTML = '<p style="color:var(--text-muted);">Searching...</p>';

  try {
    let crypto;
    try {
      crypto = normalizeCrypto(await apiFetch(`/api/v1/crypto/symbol/${encodeURIComponent(symbol)}`));
    } catch (error) {
      crypto = normalizeCrypto(await apiFetch(`/api/v1/crypto/${encodeURIComponent(symbol)}/price`, { method: 'PUT' }));
    }

    if (!CRYPTO_MARKET_SYMBOLS.includes(symbol)) {
      CRYPTO_MARKET_SYMBOLS.push(symbol);
    }

    lookupContainer.innerHTML = buildLookupCard(crypto);
  } catch (error) {
    lookupContainer.innerHTML = `<p class="status-error">❌ ${esc(error.message)}</p>`;
  }
}

function buildLookupCard(crypto) {
  return `
    <div class="lookup-card">
      <div>
        <strong>${esc(crypto.symbol)}</strong>
        <p>${esc(crypto.name)}</p>
      </div>
      <div class="lookup-actions">
        <span class="lookup-price">${fmt(crypto.currentPrice)}</span>
        <button class="btn btn-secondary btn-sm" onclick="showCryptoDetails(${crypto.cryptoId})">Details</button>
        <button class="btn btn-primary btn-sm" onclick='openAddModal("CRYPTO", ${json({ symbol: crypto.symbol, name: crypto.name, currentPrice: crypto.currentPrice })})'>Buy</button>
      </div>
    </div>
  `;
}

// ─── Transactions ─────────────────────────────────────────────────────────
async function loadTransactions() {
  const container = document.getElementById('tx-container');
  const statusEl = document.getElementById('tx-status');
  container.innerHTML = '<p style="color:var(--text-muted);padding:12px 0;">Loading...</p>';
  statusEl.innerHTML = '';

  try {
    const rows = safeArray(await apiFetch('/api/v1/transactions/history'));
    if (!rows.length) {
      container.innerHTML = emptyState('No crypto transactions yet.');
      return;
    }

    const head = `<tr>
      <th>#</th><th>Portfolio</th><th>Symbol</th><th>Name</th>
      <th>Type</th><th>Quantity</th><th>Price</th><th>Total</th><th>Date & Time</th></tr>`;

    const body = rows.map(row => {
      const isBuy = row.transactionType === 'BUY';
      const total = Number(row.quantity || 0) * Number(row.transactionPrice || 0);
      return `<tr>
        <td>${esc(row.transactionId)}</td>
        <td>${esc(row.portfolioId)}</td>
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

// ─── Add Asset Modal ──────────────────────────────────────────────────────
function openAddModal(type = 'STOCK', preset = {}) {
  document.getElementById('add-modal').classList.add('open');
  document.getElementById('add-status').textContent = '';
  document.getElementById('add-type').value = type;
  switchAddForm(type);

  // Reset crypto lookup status
  const lookupStatus = document.getElementById('crypto-lookup-status');
  if (lookupStatus) lookupStatus.textContent = '';

  if (type === 'CRYPTO') {
    document.getElementById('crypto-symbol').value = preset.symbol || '';
    document.getElementById('crypto-name').value = preset.name || '';
    document.getElementById('crypto-current-price').value = preset.currentPrice != null ? preset.currentPrice : '';
    // If no name pre-filled but symbol is set, trigger auto-fetch
    if (preset.symbol && !preset.name) {
      fetchCryptoInfo();
    }
  }
}

function closeAddModal() {
  document.getElementById('add-modal').classList.remove('open');
}

function switchAddForm(type) {
  ['STOCK', 'BOND', 'CRYPTO'].forEach(formType => {
    document.getElementById(`form-${formType}`).style.display = formType === type ? 'grid' : 'none';
  });
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

      if (!payload.symbol || !payload.name || payload.quantity == null || payload.buyPrice == null || payload.currentPrice == null) {
        throw new Error('Please fill all crypto fields');
      }

      await apiFetch('/api/v1/crypto', {
        method: 'POST',
        body: JSON.stringify(payload)
      });
    } else if (type === 'STOCK') {
      const payload = {
        type,
        symbol: val('stock-symbol'),
        assetName: val('stock-name'),
        quantity: val('stock-quantity'),
        purchasePrice: val('stock-price'),
        purchaseDate: val('stock-date') || new Date().toISOString().slice(0, 10)
      };
      if (!payload.symbol || !payload.assetName || !payload.quantity || !payload.purchasePrice) {
        throw new Error('Please fill all stock fields');
      }
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
      if (!payload.issuer || !payload.interestRate || !payload.amountInvested || !payload.startDate || !payload.tenureMonths) {
        throw new Error('Please fill all bond fields');
      }
      await apiFetch('/api/v1/portfolio/holdings', { method: 'POST', body: JSON.stringify(payload) });
    }

    statusEl.innerHTML = '<span class="status-success">✅ Asset added successfully!</span>';
    setTimeout(async () => {
      closeAddModal();
      await refreshDashboardAndHoldings();
      if (document.getElementById('section-transactions').classList.contains('active')) {
        await loadTransactions();
      }
      if (document.getElementById('section-marketplace').classList.contains('active')) {
        await loadMarketplace();
      }
    }, 700);
  } catch (error) {
    statusEl.innerHTML = `<span class="status-error">❌ ${esc(error.message)}</span>`;
  } finally {
    btn.disabled = false;
    btn.textContent = 'Add Asset';
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

async function confirmSell() {
  if (!pendingSell) return;

  try {
    if (pendingSell.type === 'CRYPTO') {
      const quantity = numberValue('sell-quantity');
      if (quantity == null || quantity <= 0) {
        throw new Error('Enter a valid sell quantity');
      }
      if (Number(quantity) > Number(pendingSell.quantity)) {
        throw new Error('Sell quantity cannot exceed current holdings');
      }

      await apiFetch('/api/v1/crypto', {
        method: 'POST',
        body: JSON.stringify({
          symbol: pendingSell.symbol,
          name: pendingSell.name,
          quantity,
          currentPrice: pendingSell.currentPrice,
          transactionType: 'SELL'
        })
      });
    } else {
      await apiFetch(`/api/v1/portfolio/sell/${pendingSell.id}`, { method: 'POST' });
    }

    closeSellModal();
    await refreshDashboardAndHoldings();
    await loadTransactions();
    if (document.getElementById('section-marketplace').classList.contains('active')) {
      await loadMarketplace();
    }
  } catch (error) {
    alert(`Sell failed: ${error.message}`);
  }
}

// ─── Crypto Detail Actions ────────────────────────────────────────────────
async function showCryptoDetails(cryptoId) {
  try {
    const crypto = normalizeCrypto(await apiFetch(`/api/v1/crypto/${cryptoId}`));
    const modal = document.getElementById('crypto-details-modal');
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
      </div>
    `;

    modal.classList.add('open');
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
      if (document.getElementById('section-marketplace').classList.contains('active')) {
        await loadMarketplace();
      }
    }
  } catch (error) {
    if (!silent) {
      alert(`Refresh failed: ${error.message}`);
    }
  }
}

async function deleteCryptoHolding(cryptoId, name) {
  const confirmed = window.confirm(`Delete ${name}? This removes the crypto row from the backend.`);
  if (!confirmed) return;

  try {
    await apiFetch(`/api/v1/crypto/${cryptoId}`, { method: 'DELETE' });
    await refreshDashboardAndHoldings();
    await loadTransactions();
    if (document.getElementById('section-marketplace').classList.contains('active')) {
      await loadMarketplace();
    }
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

  if (!symbol || symbol.length < 2) {
    if (statusEl) statusEl.textContent = '';
    return;
  }

  if (statusEl) statusEl.textContent = '🔍 Fetching info...';

  try {
    const data = await apiFetch(`/api/v1/crypto/lookup/${encodeURIComponent(symbol)}`);
    // Fill in name only if empty (don't override what user typed)
    if (data.name && !nameInput.value.trim()) {
      nameInput.value = data.name;
    }
    // Always fill in current price from live feed
    if (data.currentPrice && Number(data.currentPrice) > 0) {
      priceInput.value = Number(data.currentPrice).toFixed(2);
      if (statusEl) statusEl.innerHTML = `<span style="color:var(--success);">✅ ${esc(data.name)} — $${Number(data.currentPrice).toLocaleString('en-US', {minimumFractionDigits:2, maximumFractionDigits:2})}</span>`;
    } else {
      if (statusEl) statusEl.innerHTML = `<span style="color:var(--text-muted);">ℹ️ ${esc(data.name)} — no live price available</span>`;
    }
  } catch (err) {
    if (statusEl) statusEl.innerHTML = `<span style="color:var(--danger);">⚠️ Could not fetch info for "${esc(symbol)}"</span>`;
    console.warn('Crypto symbol lookup failed:', err.message);
  }
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

  if (response.status === 204) {
    return {};
  }

  return response.json();
}

// ─── Helpers ──────────────────────────────────────────────────────────────
function normalizeCryptos(rows) {
  return safeArray(rows).map(normalizeCrypto);
}

function normalizeCrypto(row) {
  return {
    cryptoId: row.cryptoId,
    symbol: row.symbol,
    name: row.name,
    quantity: Number(row.quantity || 0),
    buyPrice: Number(row.buyPrice || 0),
    currentPrice: Number(row.currentPrice || 0),
    investedAmount: Number(row.investedAmount || 0),
    currentValue: Number(row.currentValue || 0),
    profitLoss: Number(row.profitLoss || 0)
  };
}

function activeCryptoHoldings(rows) {
  return normalizeCryptos(rows).filter(item => Number(item.quantity) > 0 || Number(item.currentValue) > 0 || Number(item.investedAmount) > 0);
}

async function refreshDashboardAndHoldings() {
  if (document.getElementById('section-dashboard').classList.contains('active')) {
    await loadDashboard();
  }
  if (document.getElementById('section-holdings').classList.contains('active')) {
    await loadHoldings(document.getElementById('holdings-filter').value);
  }
}

function safeArray(value) {
  return Array.isArray(value) ? value : [];
}

function sumBy(rows, key) {
  return rows.reduce((sum, row) => sum + Number(row[key] || 0), 0);
}

function numberValue(id) {
  const raw = val(id);
  if (!raw) return null;
  const parsed = Number(raw);
  return Number.isFinite(parsed) ? parsed : null;
}

function esc(value) {
  if (value == null) return '—';
  return String(value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

function escJs(value) {
  return String(value ?? '').replace(/\\/g, '\\\\').replace(/'/g, "\\'");
}

function json(value) {
  return escJs(JSON.stringify(value));
}

function fmt(value) {
  if (value == null || Number.isNaN(Number(value))) return '—';
  return '$' + Number(value).toLocaleString('en-US', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  });
}

function num(value, decimals = 4) {
  if (value == null || Number.isNaN(Number(value))) return '—';
  return Number(value).toLocaleString('en-US', {
    minimumFractionDigits: 0,
    maximumFractionDigits: decimals
  });
}

function fmtDate(value) {
  if (!value) return '—';
  return new Date(value).toLocaleString('en-US', { dateStyle: 'medium', timeStyle: 'short' });
}

function val(id) {
  return document.getElementById(id)?.value?.trim() || '';
}

function emptyState(message) {
  return `<div class="empty-state"><div style="font-size:3rem;">📭</div><p>${esc(message)}</p></div>`;
}

