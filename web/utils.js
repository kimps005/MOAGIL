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

  function passengerName(displayName) {
    const emoji = passengerEmoji(displayName);
    return emoji ? String(displayName).slice(emoji.length).trim() : String(displayName || '');
  }

  function escapeHtml(value) {
    return String(value ?? '').replace(/[&<>"']/g, ch => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[ch]);
  }

  window.MOAGILUtils = { avatarEmojis, passengerEmoji, passengerName, escapeHtml };
})();
