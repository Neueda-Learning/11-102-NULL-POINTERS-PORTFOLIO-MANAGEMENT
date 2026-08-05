const API_BASE = '/api/v1/bonds';

let pendingDeleteId = null;

// ─── Load all bonds on page load ───────────────────────────────────────────
document.addEventListener('DOMContentLoaded', loadBonds);

async function loadBonds() {
    const container = document.getElementById('bonds-container');
    const status = document.getElementById('bonds-status');
    container.innerHTML = '<p style="color:var(--text-muted);">Loading...</p>';
    status.textContent = '';

    try {
        const res = await fetch(API_BASE);
        if (!res.ok) throw new Error(`Server responded with ${res.status}`);
        const bonds = await res.json();

        if (bonds.length === 0) {
            container.innerHTML = `
                <div class="empty-state">
                    <div style="font-size:3rem;">📭</div>
                    <p>No bonds found. Add your first bond above!</p>
                </div>`;
            return;
        }

        container.innerHTML = buildTable(bonds);
        status.innerHTML = `<span class="status-success">✅ ${bonds.length} bond(s) loaded</span>`;
    } catch (err) {
        container.innerHTML = `<p class="status-error">❌ Failed to load bonds: ${err.message}</p>`;
    }
}

// ─── Build HTML table ───────────────────────────────────────────────────────
function buildTable(bonds) {
    const rows = bonds.map(b => `
        <tr>
            <td><strong>${esc(b.id)}</strong></td>
            <td>${esc(b.issuer)}</td>
            <td><span class="badge">${esc(b.interestRate)}%</span></td>
            <td>₹${fmt(b.amountInvested)}</td>
            <td>${esc(b.startDate)}</td>
            <td>${esc(b.tenureMonths)} mo</td>
            <td>${esc(b.maturityDate)}</td>
            <td>₹${fmt(b.totalInvestment)}</td>
            <td>₹${fmt(b.annualIncome)}</td>
            <td>₹${fmt(b.maturityAmount)}</td>
            <td>${esc(b.approxYTM)}%</td>
            <td>
                <button class="delete-btn" onclick="openDeleteModal(${b.id}, '${esc(b.issuer)}')">
                    🗑️ Delete
                </button>
            </td>
        </tr>
    `).join('');

    return `
        <table class="bonds-table">
            <thead>
                <tr>
                    <th>ID</th>
                    <th>Issuer</th>
                    <th>Rate</th>
                    <th>Invested</th>
                    <th>Start Date</th>
                    <th>Tenure</th>
                    <th>Maturity</th>
                    <th>Total Investment</th>
                    <th>Annual Income</th>
                    <th>Maturity Amount</th>
                    <th>Approx YTM</th>
                    <th>Action</th>
                </tr>
            </thead>
            <tbody>${rows}</tbody>
        </table>`;
}

// ─── Submit new bond ────────────────────────────────────────────────────────
async function submitBond(event) {
    event.preventDefault();
    const btn = document.getElementById('submit-btn');
    const statusEl = document.getElementById('form-status');
    btn.disabled = true;
    btn.textContent = 'Adding...';
    statusEl.textContent = '';

    const payload = {
        issuer: document.getElementById('issuer').value.trim(),
        interestRate: parseFloat(document.getElementById('interestRate').value),
        amountInvested: parseFloat(document.getElementById('amountInvested').value),
        startDate: document.getElementById('startDate').value,
        tenureMonths: parseInt(document.getElementById('tenureMonths').value)
    };

    try {
        const res = await fetch(API_BASE, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });

        if (!res.ok) {
            const err = await res.json().catch(() => ({}));
            throw new Error(err.message || `Server error ${res.status}`);
        }

        statusEl.innerHTML = '<span class="status-success">✅ Bond added successfully!</span>';
        document.getElementById('bond-form').reset();
        loadBonds();
    } catch (err) {
        statusEl.innerHTML = `<span class="status-error">❌ ${err.message}</span>`;
    } finally {
        btn.disabled = false;
        btn.textContent = 'Add Bond';
    }
}

// ─── Delete modal ───────────────────────────────────────────────────────────
function openDeleteModal(id, issuer) {
    pendingDeleteId = id;
    document.getElementById('modal-issuer').textContent = issuer;
    document.getElementById('modal-overlay').style.display = 'flex';
}

function closeModal() {
    pendingDeleteId = null;
    document.getElementById('modal-overlay').style.display = 'none';
}

async function confirmDelete() {
    if (!pendingDeleteId) return;
    const id = pendingDeleteId;
    closeModal();

    try {
        const res = await fetch(`${API_BASE}/${id}`, { method: 'DELETE' });
        if (res.status === 204) {
            document.getElementById('bonds-status').innerHTML =
                `<span class="status-success">✅ Bond #${id} deleted.</span>`;
            loadBonds();
        } else if (res.status === 404) {
            document.getElementById('bonds-status').innerHTML =
                `<span class="status-error">❌ Bond not found.</span>`;
        } else {
            throw new Error(`Unexpected status ${res.status}`);
        }
    } catch (err) {
        document.getElementById('bonds-status').innerHTML =
            `<span class="status-error">❌ Delete failed: ${err.message}</span>`;
    }
}

// ─── Helpers ────────────────────────────────────────────────────────────────
function esc(val) {
    if (val === null || val === undefined) return '—';
    return String(val).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

function fmt(val) {
    if (val === null || val === undefined) return '—';
    return Number(val).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

