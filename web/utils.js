/* Shared HTML escaping helper for the web UI and map adapters. */
(() => {
  function escapeHtml(value) {
    return String(value ?? '').replace(/[&<>"']/g, ch => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[ch]);
  }

  window.MOAGILUtils = { escapeHtml };
})();
