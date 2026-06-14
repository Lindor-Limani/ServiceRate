// utils.js – Gemeinsame Helfer für Kunden- und Provider-Frontend

// Anzeige-Labels für die Service-Kategorien
const CAT_LABELS = {
  CLEANING: 'Reinigung', PLUMBING: 'Installateur',
  ELECTRICAL: 'Elektriker', PAINTING: 'Maler',
  GARDENING: 'Garten', OTHER: 'Sonstiges'
};

// Emoji + Hintergrundfarbe je Kategorie für die Karten-Platzhalterbilder
const CAT_IMAGES = {
  CLEANING:   { emoji: '🧹', bg: '#e0f2f1' },
  PLUMBING:   { emoji: '🔧', bg: '#e3f2fd' },
  ELECTRICAL: { emoji: '⚡', bg: '#fff9e6' },
  PAINTING:   { emoji: '🎨', bg: '#fce4ec' },
  GARDENING:  { emoji: '🌿', bg: '#e8f5e9' },
  OTHER:      { emoji: '🛠️', bg: '#f3e5f5' }
};

// Liefert das HTML für das Kategorie-Platzhalterbild oben auf einer Karte
function catImage(category) {
  const img = CAT_IMAGES[category] || CAT_IMAGES.OTHER;
  return `<div class="card-image" style="background:${img.bg}">${img.emoji}</div>`;
}

// Escaped Nutzereingaben, bevor sie ins DOM geschrieben werden (XSS-Schutz)
function esc(str) {
  return String(str || '')
    .replace(/&/g, '&amp;').replace(/</g, '&lt;')
    .replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

// Zentrale Benachrichtigung als selbstschließender Toast.
// type: 'success' | 'error' | 'info'
function notify(message, type = 'info') {
  let container = document.getElementById('notifyContainer');
  if (!container) {
    container = document.createElement('div');
    container.id = 'notifyContainer';
    container.style.cssText =
      'position:fixed;top:20px;right:20px;z-index:9999;display:flex;flex-direction:column;gap:10px';
    document.body.appendChild(container);
  }

  const colors = { success: '#10b981', error: '#ef4444', info: '#00877C' };
  const el = document.createElement('div');
  el.textContent = message;
  el.style.cssText =
    `background:${colors[type] || colors.info};color:#fff;padding:12px 18px;border-radius:10px;` +
    'box-shadow:0 6px 20px rgba(0,0,0,.18);font:600 .9rem system-ui,sans-serif;max-width:320px;' +
    'opacity:0;transform:translateX(20px);transition:opacity .2s,transform .2s';
  container.appendChild(el);

  // Einblenden, dann nach kurzer Zeit automatisch wieder entfernen
  requestAnimationFrame(() => { el.style.opacity = '1'; el.style.transform = 'translateX(0)'; });
  setTimeout(() => {
    el.style.opacity = '0';
    el.style.transform = 'translateX(20px)';
    setTimeout(() => el.remove(), 250);
  }, 3200);
}
