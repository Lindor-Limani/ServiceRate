// provider-dashboard.js – Service- und Buchungsverwaltung des Handwerkers
// CAT_LABELS, esc() und notify() kommen aus utils.js

// ── State ─────────────────────────────────────────────────────────────────────
let allServices   = [];
let pendingDelete = null;
let editMode      = false;
let providerBookings        = []; // zuletzt geladene Buchungen (für die Kalender-Navigation)
let providerCalendarCursor  = new Date(); // aktuell im Kalender angezeigter Monat

// ── Init ──────────────────────────────────────────────────────────────────────
// Token aus dem localStorage lesen und nur PROVIDER automatisch einloggen
(function init() {
  const token = localStorage.getItem('provider_jwt');
  if (token) {
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      if (payload.accountType === 'PROVIDER') showApp();
    } catch { /* ungültiges Token -> Login-Screen bleibt */ }
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
  if (!email || !password) { notify('Bitte E-Mail und Passwort eingeben.', 'error'); return; }

  try {
    const data = await fetchAPI('/auth/login', 'POST', { email, password }, 'provider_jwt');

    // Nur Handwerker dürfen ins Dashboard
    const payload = JSON.parse(atob(data.token.split('.')[1]));
    if (payload.accountType !== 'PROVIDER') {
      notify('Dieser Account gehört einem Kunden. Bitte nutze den Marktplatz.', 'error');
      return;
    }

    localStorage.setItem('provider_jwt', data.token);
    localStorage.setItem('provider_user_id', data.userId);
    showApp();
  } catch {
    notify('Login fehlgeschlagen. Zugangsdaten prüfen.', 'error');
  }
}

function doLogout() {
  localStorage.removeItem('provider_jwt');
  localStorage.removeItem('provider_user_id');
  document.getElementById('appContent').style.display  = 'none';
  document.getElementById('loginScreen').style.display = 'flex';
  document.getElementById('loginPassword').value = '';
}

// ── Services laden & rendern ──────────────────────────────────────────────────
async function loadServices() {
  const grid = document.getElementById('servicesGrid');
  grid.innerHTML = `<div class="empty-state"><div class="empty-icon">⏳</div><p>Wird geladen…</p></div>`;
  try {
    // /services/my liefert nur die Services des eingeloggten Providers (per JWT)
    allServices = await fetchAPI('/services/my', 'GET', null, 'provider_jwt');
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
      ${catImage(s.category)}
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
  const avg   = count > 0 ? (allServices.reduce((sum, s) => sum + s.price, 0) / count) : 0;
  const cats  = new Set(allServices.map(s => s.category)).size;

  document.getElementById('statCount').textContent    = count;
  document.getElementById('statAvgPrice').textContent = count > 0 ? `€${avg.toFixed(0)}` : '–';
  document.getElementById('statCats').textContent     = cats || '–';
}

// ── Service-Modal (Erstellen / Bearbeiten) ────────────────────────────────────
function openCreateModal() {
  editMode = false;
  document.getElementById('modalTitle').textContent    = 'Neuer Service';
  document.getElementById('editServiceId').value       = '';
  document.getElementById('svcTitle').value            = '';
  document.getElementById('svcDesc').value             = '';
  document.getElementById('svcCategory').value         = 'CLEANING';
  document.getElementById('svcPrice').value            = '';
  document.getElementById('svcZip').value              = '';
  document.getElementById('svcZipGroup').style.display = 'block'; // PLZ nur beim Erstellen
  document.getElementById('serviceModal').classList.add('open');
}

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
  // Der Ort lässt sich beim Bearbeiten nicht ändern (Backend-PUT kennt keine PLZ) -> Feld ausblenden
  document.getElementById('svcZipGroup').style.display = 'none';
  document.getElementById('serviceModal').classList.add('open');
}

function closeServiceModal() {
  document.getElementById('serviceModal').classList.remove('open');
}

async function saveService() {
  const title    = document.getElementById('svcTitle').value.trim();
  const desc     = document.getElementById('svcDesc').value.trim();
  const category = document.getElementById('svcCategory').value;
  const price    = parseFloat(document.getElementById('svcPrice').value);

  if (!title || !desc || isNaN(price) || price <= 0) {
    notify('Bitte alle Felder korrekt ausfüllen.', 'error'); return;
  }

  if (editMode) {
    const id = document.getElementById('editServiceId').value;
    try {
      await fetchAPI(`/services/${id}`, 'PUT', { title, description: desc, category, price }, 'provider_jwt');
      notify('Service aktualisiert ✓', 'success');
      closeServiceModal();
      loadServices();
    } catch {
      notify('Fehler beim Aktualisieren.', 'error');
    }
  } else {
    const providerId = localStorage.getItem('provider_user_id');
    if (!providerId) { notify('Provider-ID fehlt. Bitte neu anmelden.', 'error'); return; }
    const zipCode = document.getElementById('svcZip').value.trim();
    if (!zipCode) { notify('Bitte eine Postleitzahl angeben.', 'error'); return; }
    try {
      await fetchAPI('/services', 'POST', { providerId, title, description: desc, category, price, zipCode }, 'provider_jwt');
      notify('Service erstellt ✓', 'success');
      closeServiceModal();
      loadServices();
    } catch (e) {
      // Backend liefert z.B. "Ungültige Postleitzahl" als 400 -> ehrlich anzeigen
      notify(e.message || 'Fehler beim Erstellen.', 'error');
    }
  }
}

// ── Löschen ───────────────────────────────────────────────────────────────────
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
    notify('Service gelöscht.', 'success');
    closeDeleteModal();
    loadServices();
  } catch {
    notify('Fehler beim Löschen.', 'error');
    closeDeleteModal();
  }
}

// ── Tabs umschalten ───────────────────────────────────────────────────────────
function switchDashboardTab(tab) {
  document.getElementById('servicesView').style.display = tab === 'services' ? 'block' : 'none';
  document.getElementById('bookingsView').style.display = tab === 'bookings' ? 'block' : 'none';
  document.getElementById('tabMyServices').classList.toggle('active', tab === 'services');
  document.getElementById('tabMyBookings').classList.toggle('active', tab === 'bookings');

  if (tab === 'bookings') loadBookings();
  else loadServices();
}

// ── Buchungen laden & anzeigen ────────────────────────────────────────────────
// Logik unverändert: lädt per fetchAPI über /bookings/provider/${providerId} (GET).
// Neu ist nur die optische Kalender-Darstellung beim Rendern (renderBookings()).
async function loadBookings() {
  const grid = document.getElementById('bookingsGrid');
  grid.innerHTML = `<div class="empty-state"><div class="empty-icon">⏳</div><p>Lade Anfragen…</p></div>`;

  const providerId = localStorage.getItem('provider_user_id');
  if (!providerId) {
    grid.innerHTML = `<div class="empty-state"><p>Bitte neu anmelden.</p></div>`;
    return;
  }
  try {
    const bookings = await fetchAPI(`/bookings/provider/${providerId}`, 'GET', null, 'provider_jwt');
    providerBookings = Array.isArray(bookings) ? bookings : [];
    renderBookings();
  } catch (e) {
    console.error('Fehler beim Laden der Provider-Buchungen:', e);
    grid.innerHTML = `<div class="empty-state"><p>Fehler beim Laden der Buchungen.</p></div>`;
  }
}

// Zeichnet Übersichtskarte + Kalender + Anfragenliste aus dem State providerBookings
function renderBookings() {
  const grid = document.getElementById('bookingsGrid');

  const pendingCount  = providerBookings.filter(b => b.status === 'PENDING').length;
  const acceptedCount = providerBookings.filter(b => b.status === 'ACCEPTED').length;
  const upcoming = providerBookings
    .filter(b => b.status !== 'REJECTED' && b.bookingDate && new Date(b.bookingDate) >= stripTime(new Date()))
    .sort((a, b) => new Date(a.bookingDate) - new Date(b.bookingDate));
  const nextBooking = upcoming[0];

  grid.innerHTML = `
    <section class="appointment-calendar-shell">
      <div class="notification-card ${pendingCount ? 'has-pending' : ''}">
        <div class="notification-left">
          <span class="notification-icon">${pendingCount ? '🔔' : '✓'}</span>
          <div>
            <span class="notification-kicker">Terminstatus</span>
            <strong>${pendingCount ? `${pendingCount} offene Anfrage${pendingCount === 1 ? '' : 'n'}` : 'Alle Anfragen sind bearbeitet'}</strong>
            <p>${pendingCount ? 'Offene Anfragen warten auf deine Bestätigung.' : 'Aktuell keine offenen Anfragen.'}</p>
          </div>
        </div>
        <div class="notification-metrics">
          <div class="notification-metric"><b>${acceptedCount}</b><span>Akzeptiert</span></div>
          <div class="notification-metric"><b>${nextBooking ? formatDateShort(nextBooking.bookingDate) : '–'}</b><span>Nächster Termin</span></div>
        </div>
      </div>
      ${renderProviderCalendar()}
    </section>

    <section class="appointments-list">
      <div class="section-row-title">
        <div>
          <span>Alle Anfragen & Termine</span>
          <small>Akzeptiere oder lehne offene Buchungen direkt im Postfach ab.</small>
        </div>
      </div>
      ${providerBookings.length ? providerBookings.map(b => renderProviderBookingCard(b)).join('') : `
        <div class="appointment-empty-card">
          <div class="empty-icon">📭</div>
          <p>Du hast aktuell keine Anfragen. Der Kalender bleibt bereit, sobald neue Buchungen eintreffen.</p>
        </div>
      `}
    </section>
  `;
}

// Reine Optik: Monatskalender mit Status-Pillen je Wunschtermin (bookingDate)
function renderProviderCalendar() {
  const year  = providerCalendarCursor.getFullYear();
  const month = providerCalendarCursor.getMonth();
  const monthLabel = providerCalendarCursor.toLocaleString('de-AT', { month: 'long', year: 'numeric' });

  const firstDay = new Date(year, month, 1);
  const daysInMonth = new Date(year, month + 1, 0).getDate();
  const startOffset = (firstDay.getDay() + 6) % 7; // Montag zuerst

  const visibleBookings = providerBookings.filter(b => b.status !== 'REJECTED' && b.bookingDate);
  const monthBookings = visibleBookings.filter(b => {
    const d = new Date(b.bookingDate);
    return !Number.isNaN(d.getTime()) && d.getFullYear() === year && d.getMonth() === month;
  });
  const monthPending  = monthBookings.filter(b => b.status === 'PENDING').length;
  const monthAccepted = monthBookings.filter(b => b.status === 'ACCEPTED').length;
  const cells = [];

  for (let i = 0; i < startOffset; i++) {
    cells.push(`<div class="calendar-day muted"></div>`);
  }

  for (let day = 1; day <= daysInMonth; day++) {
    const key = `${year}-${pad2(month + 1)}-${pad2(day)}`;
    const dayBookings = visibleBookings.filter(b => dateKey(b.bookingDate) === key);
    const isToday = dateKey(new Date()) === key;

    cells.push(`
      <div class="calendar-day ${isToday ? 'today' : ''} ${dayBookings.length ? 'has-events' : ''}">
        <div class="calendar-day-header">
          <span class="calendar-day-number">${day}</span>
          ${dayBookings.length ? `<span class="calendar-count">${dayBookings.length}</span>` : ''}
        </div>
        <div class="calendar-bookings">
          ${dayBookings.slice(0, 3).map(b => `
            <div class="calendar-pill ${statusToClass(b.status)}" title="${esc(b.serviceTitle)} · ${esc(b.customerName || '')}">
              <span class="calendar-pill-title">${esc(shorten(b.serviceTitle, 22))}</span>
            </div>
          `).join('')}
          ${dayBookings.length > 3 ? `<div class="calendar-more">+${dayBookings.length - 3} weitere</div>` : ''}
        </div>
      </div>
    `);
  }

  return `
    <div class="calendar-surface">
      <div class="calendar-toolbar">
        <div class="calendar-title-block">
          <span class="calendar-kicker">Terminkalender</span>
          <h2>${monthLabel}</h2>
          <p>Alle akzeptierten und offenen Termine auf einen Blick.</p>
        </div>
        <div class="calendar-actions">
          <button class="calendar-nav-btn" onclick="moveProviderCalendar(-1)" aria-label="Vorheriger Monat">‹</button>
          <button class="calendar-today-btn" onclick="goToCurrentProviderMonth()">Heute</button>
          <button class="calendar-nav-btn" onclick="moveProviderCalendar(1)" aria-label="Nächster Monat">›</button>
        </div>
      </div>

      <div class="calendar-month-meta">
        <span><b>${monthBookings.length}</b> Termine im Monat</span>
        <span><b>${monthPending}</b> offen</span>
        <span><b>${monthAccepted}</b> akzeptiert</span>
      </div>

      <div class="calendar-legend">
        <span><i class="dot status-pending"></i>PENDING</span>
        <span><i class="dot status-accepted"></i>ACCEPTED</span>
        <span><i class="dot status-rejected"></i>REJECTED</span>
      </div>

      <div class="calendar-scroll">
        <div class="calendar-board">
          <div class="calendar-weekdays">
            <span>Mo</span><span>Di</span><span>Mi</span><span>Do</span><span>Fr</span><span>Sa</span><span>So</span>
          </div>
          <div class="calendar-grid-big">${cells.join('')}</div>
        </div>
      </div>
    </div>
  `;
}

function moveProviderCalendar(delta) {
  providerCalendarCursor = new Date(
    providerCalendarCursor.getFullYear(),
    providerCalendarCursor.getMonth() + delta,
    1
  );
  renderBookings();
}

function goToCurrentProviderMonth() {
  providerCalendarCursor = new Date();
  renderBookings();
}

// Einzelne Anfrage-Karte (Akzeptieren/Ablehnen-Logik unverändert)
function renderProviderBookingCard(b) {
  const statusClass = statusToClass(b.status);

  return `
    <div class="svc-card appointment-card ${statusClass}">
      <div class="svc-top">
        <span class="cat-badge status-badge ${statusClass}">${esc(b.status)}</span>
        <span class="appointment-date">${formatDateShort(b.bookingDate)}</span>
      </div>
      <div class="svc-title">${esc(b.serviceTitle)}</div>
      <div class="svc-desc">Angefragt von: <strong>${esc(b.customerName)}</strong></div>

      ${b.status === 'PENDING' ? `
        <div class="svc-footer">
          <button class="btn btn-primary btn-sm" onclick="updateBookingStatus('${b.id}', 'ACCEPTED')">✅ Akzeptieren</button>
          <button class="btn btn-danger btn-sm" onclick="updateBookingStatus('${b.id}', 'REJECTED')">❌ Ablehnen</button>
        </div>
      ` : `
        <div class="svc-footer muted-text">Buchung ist abgeschlossen/abgelehnt.</div>
      `}
    </div>
  `;
}

// ── Kalender-Helfer (rein optisch/Datum) ──────────────────────────────────────
function pad2(n) { return String(n).padStart(2, '0'); }
function stripTime(d) { return new Date(d.getFullYear(), d.getMonth(), d.getDate()); }
function statusToClass(status) {
  if (status === 'ACCEPTED')  return 'status-accepted';
  if (status === 'REJECTED')  return 'status-rejected';
  if (status === 'COMPLETED') return 'status-completed';
  return 'status-pending';
}
function dateKey(value) {
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return '';
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`;
}
function formatDateShort(value) {
  if (!value) return '–';
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return '–';
  return d.toLocaleDateString('de-AT', { day: '2-digit', month: '2-digit', year: 'numeric' });
}
function shorten(value, max) {
  const str = String(value || '');
  return str.length > max ? `${str.slice(0, max - 1)}…` : str;
}

// ── Buchungs-Status ändern (PUT) ──────────────────────────────────────────────
async function updateBookingStatus(bookingId, newStatus) {
  try {
    await fetchAPI(`/bookings/${bookingId}/status`, 'PUT', { status: newStatus }, 'provider_jwt');
    notify(`Buchung wurde ${newStatus === 'ACCEPTED' ? 'akzeptiert' : 'abgelehnt'}!`, 'success');
    loadBookings();
  } catch {
    notify('Fehler beim Ändern des Status.', 'error');
  }
}
