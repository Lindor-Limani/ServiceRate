// ── State ─────────────────────────────────────────────────────────────────────
let allServices   = [];
let pendingDelete = null;
let editMode      = false;

const CAT_LABELS = {
  CLEANING: 'Reinigung', PLUMBING: 'Installateur',
  ELECTRICAL: 'Elektriker', PAINTING: 'Maler',
  GARDENING: 'Garten', OTHER: 'Sonstiges'
};

// ── Init ──────────────────────────────────────────────────────────────────────
(function init() {
  const token = localStorage.getItem('jwt_token');
  if (token) {
    showApp();
  }
  // If no token, the login screen is already visible by default
})();

function showApp() {
  document.getElementById('loginScreen').style.display = 'none';
  document.getElementById('appContent').style.display  = 'block';

  try {
    const payload = JSON.parse(atob(localStorage.getItem('jwt_token').split('.')[1]));
    document.getElementById('headerUser').textContent = payload.sub || '';
  } catch { /* ignore */ }

  loadServices();
}

// ── Auth ──────────────────────────────────────────────────────────────────────
async function doLogin() {
  const email    = document.getElementById('loginEmail').value.trim();
  const password = document.getElementById('loginPassword').value;

  if (!email || !password) {
    showLoginAlert('Bitte E-Mail und Passwort eingeben.', 'error'); return;
  }

  try {
    const data = await fetchAPI('/auth/login', 'POST', { email, password });
    localStorage.setItem('jwt_token', data.token);
    localStorage.setItem('user_id',   data.userId);
    showApp();
  } catch {
    showLoginAlert('Login fehlgeschlagen. Zugangsdaten prüfen.', 'error');
  }
}

function doLogout() {
  localStorage.removeItem('jwt_token');
  localStorage.removeItem('user_id');
  document.getElementById('appContent').style.display  = 'none';
  document.getElementById('loginScreen').style.display = 'flex';
  document.getElementById('loginPassword').value = '';
  hideLoginAlert();
}

function showLoginAlert(msg, type) {
  const el = document.getElementById('loginAlert');
  el.textContent   = msg;
  el.className     = `alert alert-${type}`;
  el.style.display = 'block';
}
function hideLoginAlert() {
  document.getElementById('loginAlert').style.display = 'none';
}

// ── Load Services ─────────────────────────────────────────────────────────────
async function loadServices() {
  const grid = document.getElementById('servicesGrid');
  grid.innerHTML = `<div class="empty-state"><div class="empty-icon">⏳</div><p>Wird geladen…</p></div>`;
  try {
    allServices = await fetchAPI('/services', 'GET');
    renderServices();
    updateStats();
  } catch {
    grid.innerHTML = `
      <div class="empty-state">
        <div class="empty-icon">⚠️</div>
        <p>Services konnten nicht geladen werden.<br/>Läuft das Backend auf Port 8080?</p>
      </div>`;
  }
}

function renderServices() {
  const grid = document.getElementById('servicesGrid');

  if (allServices.length === 0) {
    grid.innerHTML = `
      <div class="empty-state">
        <div class="empty-icon">📭</div>
        <p>Noch keine Services vorhanden.<br/>Erstelle deinen ersten Service!</p>
      </div>`;
    return;
  }

  grid.innerHTML = allServices.map(s => `
    <div class="svc-card">
      <div class="svc-top">
        <span class="cat-badge">${CAT_LABELS[s.category] || s.category}</span>
        <span class="svc-price">€${parseFloat(s.price).toFixed(2)}<small>/Std</small></span>
      </div>
      <div class="svc-title">${esc(s.title)}</div>
      <div class="svc-desc">${esc(s.description)}</div>
      <div style="font-size:.78rem;color:var(--muted)">
        Anbieter: <strong>${esc(s.providerName || '–')}</strong>
        &nbsp;·&nbsp; Status: <strong>${esc(s.status || 'ACTIVE')}</strong>
      </div>
      <div class="svc-footer">
        <button class="btn btn-ghost btn-sm" onclick="openEditModal('${s.id}')">✎ Bearbeiten</button>
        <button class="btn btn-danger btn-sm" onclick="openDeleteModal('${s.id}')">🗑 Löschen</button>
      </div>
    </div>
  `).join('');
}

function updateStats() {
  const count = allServices.length;
  const avg   = count > 0
    ? (allServices.reduce((sum, s) => sum + s.price, 0) / count)
    : 0;
  const cats  = new Set(allServices.map(s => s.category)).size;

  document.getElementById('statCount').textContent    = count;
  document.getElementById('statAvgPrice').textContent = count > 0 ? `€${avg.toFixed(0)}` : '–';
  document.getElementById('statCats').textContent     = cats || '–';
}

// ── Create Modal ──────────────────────────────────────────────────────────────
function openCreateModal() {
  editMode = false;
  document.getElementById('modalTitle').textContent = 'Neuer Service';
  document.getElementById('editServiceId').value    = '';
  document.getElementById('svcTitle').value         = '';
  document.getElementById('svcDesc').value          = '';
  document.getElementById('svcCategory').value      = 'CLEANING';
  document.getElementById('svcPrice').value         = '';
  hideModalAlert();
  document.getElementById('serviceModal').classList.add('open');
}

// ── Edit Modal ────────────────────────────────────────────────────────────────
function openEditModal(id) {
  const s = allServices.find(x => x.id === id);
  if (!s) return;
  editMode = true;
  document.getElementById('modalTitle').textContent  = 'Service bearbeiten';
  document.getElementById('editServiceId').value     = s.id;
  document.getElementById('svcTitle').value          = s.title;
  document.getElementById('svcDesc').value           = s.description;
  document.getElementById('svcCategory').value       = s.category;
  document.getElementById('svcPrice').value          = s.price;
  hideModalAlert();
  document.getElementById('serviceModal').classList.add('open');
}

function closeServiceModal() {
  document.getElementById('serviceModal').classList.remove('open');
}

// ── Save (Create or Update) ───────────────────────────────────────────────────
async function saveService() {
  const title    = document.getElementById('svcTitle').value.trim();
  const desc     = document.getElementById('svcDesc').value.trim();
  const category = document.getElementById('svcCategory').value;
  const price    = parseFloat(document.getElementById('svcPrice').value);

  if (!title || !desc || isNaN(price) || price <= 0) {
    showModalAlert('Bitte alle Felder korrekt ausfüllen.', 'error'); return;
  }

  if (editMode) {
    const id = document.getElementById('editServiceId').value;
    try {
      await fetchAPI(`/services/${id}`, 'PUT', { title, description: desc, category, price });
      showToast('Service aktualisiert ✓');
      closeServiceModal();
      loadServices();
    } catch {
      showModalAlert('Fehler beim Aktualisieren.', 'error');
    }
  } else {
    const providerId = localStorage.getItem('user_id');
    if (!providerId) { showModalAlert('Provider-ID fehlt. Bitte neu anmelden.', 'error'); return; }
    try {
      await fetchAPI('/services', 'POST', { providerId, title, description: desc, category, price });
      showToast('Service erstellt ✓');
      closeServiceModal();
      loadServices();
    } catch {
      showModalAlert('Fehler beim Erstellen.', 'error');
    }
  }
}

// ── Delete ────────────────────────────────────────────────────────────────────
function openDeleteModal(id) {
  pendingDelete = id;
  document.getElementById('deleteModal').classList.add('open');
}
function closeDeleteModal() {
  pendingDelete = null;
  document.getElementById('deleteModal').classList.remove('open');
}
async function confirmDelete() {
  if (!pendingDelete) return;
  try {
    await fetchAPI(`/services/${pendingDelete}`, 'DELETE');
    showToast('Service gelöscht.');
    closeDeleteModal();
    loadServices();
  } catch {
    showToast('Fehler beim Löschen.');
    closeDeleteModal();
  }
}

// ── Helpers ───────────────────────────────────────────────────────────────────
function esc(str) {
  return String(str || '')
    .replace(/&/g,'&amp;').replace(/</g,'&lt;')
    .replace(/>/g,'&gt;').replace(/"/g,'&quot;');
}
function showToast(msg) {
  const t = document.getElementById('toast');
  t.textContent = msg;
  t.classList.add('show');
  setTimeout(() => t.classList.remove('show'), 3000);
}
function showModalAlert(msg, type) {
  const el = document.getElementById('modalAlert');
  el.textContent   = msg;
  el.className     = `alert alert-${type}`;
  el.style.display = 'block';
}
function hideModalAlert() {
  document.getElementById('modalAlert').style.display = 'none';
}
