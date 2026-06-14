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
    const token = localStorage.getItem('provider_jwt');
    if (token) {
        try {
            // Wir lesen den JWT Token aus, OHNE das Backend fragen zu müssen!
            const payload = JSON.parse(atob(token.split('.')[1]));

            // Nur wenn der Token einem Provider gehört, loggen wir ihn automatisch ein
            if (payload.accountType === 'PROVIDER') {
                showApp();
            }
        } catch { /* ignore */ }
    }
})();

function showApp() {
  document.getElementById('loginScreen').style.display = 'none';
  document.getElementById('appContent').style.display  = 'block';

  try {
    const payload = JSON.parse(atob(localStorage.getItem('provider_jwt').split('.')[1]));
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
        const data = await fetchAPI('/auth/login', 'POST', { email, password }, 'provider_jwt');

        // NEU: Türsteher-Prüfung!
        const payload = JSON.parse(atob(data.token.split('.')[1]));
        if (payload.accountType !== 'PROVIDER') {
            showLoginAlert('Dieser Account gehört einem Kunden. Bitte nutze den Marktplatz.', 'error');
            return;
        }

        localStorage.setItem('provider_jwt', data.token);
        localStorage.setItem('provider_user_id',   data.userId);
        showApp();
    } catch {
        showLoginAlert('Login fehlgeschlagen. Zugangsdaten prüfen.', 'error');
    }
}

function doLogout() {
  localStorage.removeItem('provider_jwt');
  localStorage.removeItem('provider_user_id');
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
    allServices = await fetchAPI('/services', 'GET', null, 'provider_jwt');
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
      await fetchAPI(`/services/${id}`, 'PUT', { title, description: desc, category, price }, 'provider_jwt');
      showToast('Service aktualisiert ✓');
      closeServiceModal();
      loadServices();
    } catch {
      showModalAlert('Fehler beim Aktualisieren.', 'error');
    }
  } else {
    const providerId = localStorage.getItem('provider_user_id');
    if (!providerId) { showModalAlert('Provider-ID fehlt. Bitte neu anmelden.', 'error'); return; }
    try {
      await fetchAPI('/services', 'POST', { providerId, title, description: desc, category, price }, 'provider_jwt');
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
    await fetchAPI(`/services/${pendingDelete}`, 'DELETE', null, 'provider_jwt');
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

// ── Tabs Umschalten ───────────────────────────────────────────────────────────
function switchDashboardTab(tab) {
    document.getElementById('servicesView').style.display = tab === 'services' ? 'block' : 'none';
    document.getElementById('bookingsView').style.display = tab === 'bookings' ? 'block' : 'none';
    document.getElementById('tabMyServices').classList.toggle('active', tab === 'services');
    document.getElementById('tabMyBookings').classList.toggle('active', tab === 'bookings');

    if (tab === 'bookings') {
        loadBookings();
    } else {
        loadServices();
    }
}

// ── Buchungen laden & anzeigen ────────────────────────────────────────────────
async function loadBookings() {
    const grid = document.getElementById('bookingsGrid');
    grid.innerHTML = `<div class="empty-state"><div class="empty-icon">⏳</div><p>Lade Anfragen…</p></div>`;

    const providerId = localStorage.getItem('provider_user_id');
    try {
        const bookings = await fetchAPI(`/bookings/provider/${providerId}`, 'GET', null, 'provider_jwt');

        if (bookings.length === 0) {
            grid.innerHTML = `<div class="empty-state"><div class="empty-icon">📭</div><p>Du hast aktuell keine Anfragen.</p></div>`;
            return;
        }

        grid.innerHTML = bookings.map(b => {
            // Bestimme die Farbe des Status-Badges
            let statusColor = b.status === 'PENDING' ? '#f59e0b' : (b.status === 'ACCEPTED' ? '#10b981' : '#ef4444');

            return `
      <div class="svc-card">
        <div class="svc-top">
          <span class="cat-badge" style="background: ${statusColor}; color: white; border: none;">${b.status}</span>
        </div>
        <div class="svc-title">${esc(b.serviceTitle)}</div>
        <div class="svc-desc" style="margin-bottom: 10px;">Angefragt von: <strong>${esc(b.customerName)}</strong></div>
        
        ${b.status === 'PENDING' ? `
        <div style="display: flex; gap: 10px; margin-top: auto;">
          <button class="btn btn-primary" style="flex:1;" onclick="updateBookingStatus('${b.id}', 'ACCEPTED')">✅ Akzeptieren</button>
          <button class="btn btn-danger" style="flex:1;" onclick="updateBookingStatus('${b.id}', 'REJECTED')">❌ Ablehnen</button>
        </div>
        ` : `<div style="margin-top: auto; font-size: 0.85rem; color: var(--muted);">Buchung ist abgeschlossen/abgelehnt.</div>`}
      </div>
    `}).join('');
    } catch (error) {
        grid.innerHTML = `<div class="empty-state"><p>Fehler beim Laden der Buchungen.</p></div>`;
    }
}

// ── Buchungs-Status ändern (PUT) ──────────────────────────────────────────────
async function updateBookingStatus(bookingId, newStatus) {
    try {
        await fetchAPI(`/bookings/${bookingId}/status`, 'PUT', { status: newStatus }, 'provider_jwt');
        showToast(`Buchung wurde ${newStatus === 'ACCEPTED' ? 'akzeptiert' : 'abgelehnt'}!`);
        loadBookings(); // Liste neu laden, damit die Buttons verschwinden
    } catch (error) {
        showToast('Fehler beim Ändern des Status.');
    }
}
