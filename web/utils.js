/* Shared display helpers for the web UI and map adapters. */
(() => {
  const avatarEmojis = [
    '🚗', '🚕', '🚙', '🚌', '🚲', '🐱', '🐶', '🐼', '🐰', '🦊', '🌸', '⭐',
    '🚎', '🚐', '🚓', '🚑', '🚒', '🚚', '🚛', '🛵', '🏍️', '✈️',
    '🐻', '🐨', '🐯', '🦁', '🐸', '🐵', '🐧', '🦉', '🦄', '🐝', '🦋', '🐢',
    '🐬', '🐳', '🦈', '🐙', '🦖', '🐘', '🌈', '☀️', '🌹', '🌻', '🍀', '🍁',
    '🍎', '🍉', '🍓', '🍕', '🧋', '⚽',
  ];

  function passengerEmoji(displayName) {
    return avatarEmojis.find(emoji => String(displayName || '').startsWith(emoji + ' ')) || '';
  }

  function escapeHtml(value) {
    return String(value ?? '').replace(/[&<>"']/g, ch => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[ch]);
  }

  window.MOAGILUtils = { avatarEmojis, passengerEmoji, escapeHtml };
})();
