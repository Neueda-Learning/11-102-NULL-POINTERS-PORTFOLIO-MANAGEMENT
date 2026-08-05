/* ── Theme (Dark / Light Mode) with localStorage persistence ─────────────── */
(function () {
  const STORAGE_KEY = 'portfolio-theme';

  function applyTheme(theme) {
    document.documentElement.setAttribute('data-theme', theme);
    localStorage.setItem(STORAGE_KEY, theme);
    _updateBtn(theme);
  }

  function _updateBtn(theme) {
    const btn = document.getElementById('theme-toggle-btn');
    if (!btn) return;
    if (theme === 'dark') {
      btn.textContent = '☀️ Light Mode';
    } else {
      btn.textContent = '🌙 Dark Mode';
    }
  }

  /* Called by the sidebar button */
  window.toggleTheme = function () {
    const current = document.documentElement.getAttribute('data-theme');
    applyTheme(current === 'dark' ? 'light' : 'dark');
  };

  /* Apply saved theme immediately (before DOMContentLoaded to avoid flash) */
  const saved = localStorage.getItem(STORAGE_KEY) || 'light';
  applyTheme(saved);

  /* Re-sync button text once DOM is ready (button may not exist yet above) */
  document.addEventListener('DOMContentLoaded', function () {
    _updateBtn(localStorage.getItem(STORAGE_KEY) || 'light');
  });
})();

