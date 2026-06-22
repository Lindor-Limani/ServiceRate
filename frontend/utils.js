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
function catImage(category, imageUrl = '') {
  if (imageUrl) {
    return `<div class="card-image has-photo" style="background-image:url('${esc(imageUrl)}')"></div>`;
  }
  const img = CAT_IMAGES[category] || CAT_IMAGES.OTHER;
  return `<div class="card-image" style="background:${img.bg}">${img.emoji}</div>`;
}

function initials(name) {
  return (name || '?').split(' ').map(n => n[0]).join('').toUpperCase().slice(0, 2);
}

function avatarHtml(name, imageUrl = '', extraClass = '') {
  if (imageUrl) {
    return `<div class="avatar has-photo ${esc(extraClass)}" style="background-image:url('${esc(imageUrl)}')" aria-label="${esc(name || 'Profilbild')}"></div>`;
  }
  const lower = String(name || '').toLowerCase();
  const feminineHints = ['a', 'e', 'i'];
  const firstName = lower.split(/\s+/)[0] || '';
  const variant = feminineHints.includes(firstName.slice(-1)) ? 'avatar-pink' : 'avatar-blue';
  return `<div class="avatar ${variant} ${esc(extraClass)}">${initials(name)}</div>`;
}

function readImageFiles(input, maxFiles = 1) {
  const files = Array.from(input.files || []);
  if (files.length > maxFiles) {
    notify(`Bitte maximal ${maxFiles} Bild${maxFiles === 1 ? '' : 'er'} auswählen.`, 'error');
    input.value = '';
    return Promise.resolve([]);
  }
  const tooLarge = files.find(file => file.size > 900 * 1024);
  if (tooLarge) {
    notify('Bitte Bilder unter 900 KB verwenden.', 'error');
    input.value = '';
    return Promise.resolve([]);
  }
  const invalid = files.find(file => !file.type.startsWith('image/'));
  if (invalid) {
    notify('Bitte nur Bilddateien auswählen.', 'error');
    input.value = '';
    return Promise.resolve([]);
  }
  return Promise.all(files.map(file => new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result);
    reader.onerror = reject;
    reader.readAsDataURL(file);
  })));
}

function formatCardNumberValue(value) {
  return String(value || '').replace(/\D/g, '').slice(0, 19).replace(/(.{4})/g, '$1 ').trim();
}

function isValidCardNumber(number) {
  const digits = String(number || '').replace(/\D/g, '');
  if (digits.length < 12 || digits.length > 19) return false;
  let sum = 0;
  let shouldDouble = false;
  for (let i = digits.length - 1; i >= 0; i--) {
    let digit = Number(digits[i]);
    if (shouldDouble) {
      digit *= 2;
      if (digit > 9) digit -= 9;
    }
    sum += digit;
    shouldDouble = !shouldDouble;
  }
  return sum % 10 === 0;
}

function formatIbanValue(value) {
  return String(value || '').replace(/\s/g, '').toUpperCase().slice(0, 34).replace(/(.{4})/g, '$1 ').trim();
}

function isValidIbanBasic(value) {
  const iban = String(value || '').replace(/\s/g, '').toUpperCase();
  return /^[A-Z]{2}\d{2}[A-Z0-9]{11,30}$/.test(iban);
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
