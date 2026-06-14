// ── State ─────────────────────────────────────────────────────────────────────
let allServices   = [];
let providerBookings = [];
let pendingDelete = null;
let editMode      = false;
let providerCalendarCursor = new Date();
let bookingPollInterval = null;
let lastPendingCount = null;

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
            const payload = JSON.parse(atob(token.split('.')[1]));
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
    refreshProviderNotificationCount();
    startBookingPolling();
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
        const payload = JSON.parse(atob(data.token.split('.')[1]));
        if (payload.accountType !== 'PROVIDER') {
            showLoginAlert('Dieser Account gehört einem Kunden. Bitte nutze den Marktplatz.', 'error');
            return;
        }

        localStorage.setItem('provider_jwt', data.token);
        localStorage.setItem('provider_user_id', data.userId);
        showApp();
    } catch {
        showLoginAlert('Login fehlgeschlagen. Zugangsdaten prüfen.', 'error');
    }
}

function doLogout() {
    stopBookingPolling();
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
    grid.innerHTML = `<div class="empty-state"><div class="empty-icon">⏳</div><p>Lade Kalender…</p></div>`;

    const providerId = localStorage.getItem('provider_user_id');
    try {
        providerBookings = await fetchAPI(`/bookings/provider/${providerId}`, 'GET', null, 'provider_jwt');
        providerBookings.sort((a, b) => new Date(a.serviceDate) - new Date(b.serviceDate));
        renderBookings();
        updateProviderNotificationBadge(providerBookings);
    } catch (error) {
        grid.innerHTML = `<div class="empty-state"><p>Fehler beim Laden der Buchungen.</p></div>`;
    }
}

function renderBookings() {
    const grid = document.getElementById('bookingsGrid');

    const pendingCount = providerBookings.filter(b => b.status === 'PENDING').length;
    const acceptedCount = providerBookings.filter(b => b.status === 'ACCEPTED').length;
    const activeBookings = providerBookings.filter(b => b.status !== 'REJECTED');
    const upcomingBookings = activeBookings
        .filter(b => new Date(b.serviceDate) >= new Date())
        .sort((a, b) => new Date(a.serviceDate) - new Date(b.serviceDate));
    const nextBooking = upcomingBookings[0];

    grid.innerHTML = `
    <section class="appointment-calendar-shell">
      <div class="notification-card ${pendingCount ? 'has-pending' : ''}">
        <div class="notification-left">
          <span class="notification-icon">${pendingCount ? '🔔' : '✓'}</span>
          <div>
            <span class="notification-kicker">Terminstatus</span>
            <strong>${pendingCount ? `${pendingCount} offene Termin${pendingCount === 1 ? '' : 'e'}` : 'Alle Termine sind bearbeitet'}</strong>
            <p>${pendingCount ? 'Neue oder geänderte Termine sind wieder PENDING und müssen bestätigt werden.' : 'Keine offenen Terminänderungen im Moment.'}</p>
          </div>
        </div>
        <div class="notification-metrics">
          <div class="notification-metric"><b>${acceptedCount}</b><span>Akzeptiert</span></div>
          <div class="notification-metric"><b>${nextBooking ? formatDateShort(nextBooking.serviceDate) : '–'}</b><span>Nächster Termin</span></div>
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

function renderProviderCalendar() {
    const year = providerCalendarCursor.getFullYear();
    const month = providerCalendarCursor.getMonth();
    const monthLabel = providerCalendarCursor.toLocaleString('de-AT', { month: 'long', year: 'numeric' });

    const firstDay = new Date(year, month, 1);
    const daysInMonth = new Date(year, month + 1, 0).getDate();
    const startOffset = (firstDay.getDay() + 6) % 7; // Monday first
    const visibleBookings = providerBookings.filter(b => b.status !== 'REJECTED');
    const monthBookings = visibleBookings.filter(b => {
        const d = new Date(b.serviceDate);
        return !Number.isNaN(d.getTime()) && d.getFullYear() === year && d.getMonth() === month;
    });
    const monthPending = monthBookings.filter(b => b.status === 'PENDING').length;
    const monthAccepted = monthBookings.filter(b => b.status === 'ACCEPTED').length;
    const cells = [];

    for (let i = 0; i < startOffset; i++) {
        cells.push(`<div class="calendar-day muted"></div>`);
    }

    for (let day = 1; day <= daysInMonth; day++) {
        const key = `${year}-${pad2(month + 1)}-${pad2(day)}`;
        const dayBookings = visibleBookings
            .filter(b => dateKey(b.serviceDate) === key)
            .sort((a, b) => new Date(a.serviceDate) - new Date(b.serviceDate));
        const isToday = dateKey(new Date().toISOString()) === key;

        cells.push(`
      <div class="calendar-day ${isToday ? 'today' : ''} ${dayBookings.length ? 'has-events' : ''}">
        <div class="calendar-day-header">
          <span class="calendar-day-number">${day}</span>
          ${dayBookings.length ? `<span class="calendar-count">${dayBookings.length}</span>` : ''}
        </div>
        <div class="calendar-bookings">
          ${dayBookings.slice(0, 3).map(b => `
            <div class="calendar-pill ${statusToClass(b.status)}" title="${esc(b.serviceTitle)} · ${formatTime(b.serviceDate)} · ${esc(b.customerName || '')}">
              <span class="calendar-pill-time">${formatTime(b.serviceDate)}</span>
              <span class="calendar-pill-title">${esc(shorten(b.serviceTitle, 20))}</span>
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

function renderProviderBookingCard(b) {
    const statusClass = statusToClass(b.status);

    return `
    <div class="svc-card appointment-card ${statusClass}">
      <div class="svc-top">
        <span class="cat-badge status-badge ${statusClass}">${esc(b.status)}</span>
        <span class="appointment-date">${formatDateTime(b.serviceDate)}</span>
      </div>
      <div class="svc-title">${esc(b.serviceTitle)}</div>
      <div class="svc-desc">Angefragt von: <strong>${esc(b.customerName)}</strong></div>

      ${b.status === 'PENDING' ? `
        <div class="svc-footer">
          <button class="btn btn-primary btn-sm" onclick="updateBookingStatus('${b.id}', 'ACCEPTED')">✅ Akzeptieren</button>
          <button class="btn btn-danger btn-sm" onclick="updateBookingStatus('${b.id}', 'REJECTED')">❌ Ablehnen</button>
        </div>
      ` : `
        <div class="svc-footer muted-text">Status: ${esc(b.status)}</div>
      `}
    </div>
  `;
}

// ── Buchungs-Status ändern (PUT) ──────────────────────────────────────────────
async function updateBookingStatus(bookingId, newStatus) {
    try {
        await fetchAPI(`/bookings/${bookingId}/status`, 'PUT', { status: newStatus }, 'provider_jwt');
        showToast(`Buchung wurde ${newStatus === 'ACCEPTED' ? 'akzeptiert' : 'abgelehnt'}!`);
        await loadBookings();
    } catch (error) {
        showToast('Fehler beim Ändern des Status.');
    }
}

// ── Provider Notification Polling ─────────────────────────────────────────────
function startBookingPolling() {
    stopBookingPolling();
    bookingPollInterval = setInterval(refreshProviderNotificationCount, 30000);
}
function stopBookingPolling() {
    if (bookingPollInterval) clearInterval(bookingPollInterval);
    bookingPollInterval = null;
    lastPendingCount = null;
}
async function refreshProviderNotificationCount() {
    const providerId = localStorage.getItem('provider_user_id');
    if (!providerId || !localStorage.getItem('provider_jwt')) return;

    try {
        const bookings = await fetchAPI(`/bookings/provider/${providerId}`, 'GET', null, 'provider_jwt');
        const pendingCount = bookings.filter(b => b.status === 'PENDING').length;

        if (lastPendingCount !== null && pendingCount > lastPendingCount) {
            showToast('Neue oder geänderte Termin-Anfrage erhalten.');
            if (document.getElementById('bookingsView').style.display !== 'none') {
                providerBookings = bookings;
                renderBookings();
            }
        }

        lastPendingCount = pendingCount;
        updateProviderNotificationBadge(bookings);
    } catch {
        // Silent polling error: the main UI already shows errors when bookings tab is opened.
    }
}
function updateProviderNotificationBadge(bookings) {
    const pendingCount = bookings.filter(b => b.status === 'PENDING').length;
    const tab = document.getElementById('tabMyBookings');
    if (tab) tab.textContent = pendingCount ? `Anfragen (${pendingCount})` : 'Anfragen';
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
function pad2(n) {
    return String(n).padStart(2, '0');
}
function statusToClass(status) {
    if (status === 'ACCEPTED') return 'status-accepted';
    if (status === 'REJECTED') return 'status-rejected';
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
    return d.toLocaleDateString('de-AT', { day: '2-digit', month: '2-digit' });
}
function formatDateTime(value) {
    if (!value) return 'Kein Termin';
    const d = new Date(value);
    if (Number.isNaN(d.getTime())) return 'Ungültiges Datum';
    return d.toLocaleString('de-AT', { dateStyle: 'medium', timeStyle: 'short' });
}
function formatTime(value) {
    const d = new Date(value);
    if (Number.isNaN(d.getTime())) return '--:--';
    return d.toLocaleTimeString('de-AT', { hour: '2-digit', minute: '2-digit' });
}
function shorten(value, max) {
    const str = String(value || '');
    return str.length > max ? `${str.slice(0, max - 1)}…` : str;
}
