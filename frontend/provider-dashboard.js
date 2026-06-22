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
  if (handleAuthLinks()) return;
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

  updateProviderVerifyBanner();
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
    localStorage.setItem('provider_email', email);
    localStorage.setItem('provider_email_verified', String(data.emailVerified !== false));
    showApp();
    if (data.emailVerified === false) {
      notify('Bitte verifiziere deine E-Mail-Adresse, bevor du Services erstellst.', 'info');
    }
  } catch {
    notify('Login fehlgeschlagen. Zugangsdaten prüfen.', 'error');
  }
}

function doLogout() {
  localStorage.removeItem('provider_jwt');
  localStorage.removeItem('provider_user_id');
  localStorage.removeItem('provider_email');
  localStorage.removeItem('provider_email_verified');
  document.getElementById('appContent').style.display  = 'none';
  document.getElementById('loginScreen').style.display = 'flex';
  document.getElementById('loginPassword').value = '';
}

function showProviderResetForm() {
  document.getElementById('providerLoginForm').style.display = 'none';
  document.getElementById('providerForgotForm').style.display = 'block';
  document.getElementById('providerResetForm').style.display = 'none';
  document.getElementById('forgotEmail').value = document.getElementById('loginEmail').value.trim();
}

function showProviderLoginForm() {
  document.getElementById('providerForgotForm').style.display = 'none';
  document.getElementById('providerResetForm').style.display = 'none';
  document.getElementById('providerLoginForm').style.display = 'block';
}

function showProviderPasswordForm() {
  document.getElementById('providerLoginForm').style.display = 'none';
  document.getElementById('providerForgotForm').style.display = 'none';
  document.getElementById('providerResetForm').style.display = 'block';
}

async function requestPasswordReset() {
  const email = document.getElementById('forgotEmail').value.trim().toLowerCase();
  if (!email) { notify('Bitte gib deine E-Mail ein.', 'error'); return; }

  try {
    const data = await fetchAPI('/auth/forgot-password', 'POST', { email }, 'provider_jwt');
    document.getElementById('forgotHint').textContent =
      'Falls die E-Mail existiert und verifiziert ist, wurde ein Reset-Link versendet. Öffne den Link aus der Mail, um dein Passwort neu zu setzen.';
    document.getElementById('forgotHint').style.display = 'block';
    notify(data.message || 'Reset-Mail wurde versendet.', 'success');
  } catch (e) {
    notify(e.message || 'Reset konnte nicht vorbereitet werden.', 'error');
  }
}

async function doResetPassword() {
  const token = document.getElementById('resetToken').value.trim();
  const newPassword = document.getElementById('resetNewPassword').value;
  if (!token || !newPassword) { notify('Bitte Token und neues Passwort eingeben.', 'error'); return; }

  try {
    const data = await fetchAPI('/auth/reset-password', 'POST', { token, newPassword }, 'provider_jwt');
    notify(data.message || 'Passwort wurde aktualisiert.', 'success');
    document.getElementById('loginEmail').value = document.getElementById('forgotEmail').value.trim().toLowerCase();
    document.getElementById('loginPassword').value = '';
    document.getElementById('resetNewPassword').value = '';
    showProviderLoginForm();
  } catch (e) {
    notify(e.message || 'Passwort konnte nicht gesetzt werden.', 'error');
  }
}

async function resendVerificationMail() {
  const email = (document.getElementById('loginEmail').value || localStorage.getItem('provider_email') || '').trim().toLowerCase();
  if (!email) { notify('Bitte gib zuerst deine E-Mail im Login-Feld ein.', 'error'); return; }

  try {
    const data = await fetchAPI('/auth/resend-verification', 'POST', { email }, 'provider_jwt');
    notify(data.message || 'Verifizierungs-Mail wurde vorbereitet.', 'success');
  } catch (e) {
    notify(e.message || 'Verifizierungs-Mail konnte nicht gesendet werden.', 'error');
  }
}

function handleAuthLinks() {
  const params = new URLSearchParams(window.location.search);
  const resetToken = params.get('resetToken');
  if (resetToken) {
    document.getElementById('resetToken').value = resetToken;
    document.getElementById('loginScreen').style.display = 'flex';
    document.getElementById('appContent').style.display = 'none';
    showProviderPasswordForm();
    window.history.replaceState({}, document.title, window.location.pathname);
    return true;
  }

  if (params.get('verified') === 'true') {
    localStorage.setItem('provider_email_verified', 'true');
    notify('E-Mail wurde verifiziert. Du kannst dich jetzt anmelden.', 'success');
    window.history.replaceState({}, document.title, window.location.pathname);
  }
  return false;
}

function isProviderEmailVerified() {
  return localStorage.getItem('provider_email_verified') === 'true';
}

function updateProviderVerifyBanner() {
  const banner = document.getElementById('providerVerifyBanner');
  if (banner) banner.style.display = isProviderEmailVerified() ? 'none' : 'flex';
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
        <p>Services konnten nicht geladen werden.<br/>Läuft das Backend auf Port 8081?</p>
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
      <div class="trust-badge"><span>Trust Score</span><strong>${Number(s.trustScore || 0)}</strong></div>
      ${serviceRatingPanel(s)}
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
  if (!isProviderEmailVerified()) {
    updateProviderVerifyBanner();
    notify('Bitte verifiziere zuerst deine E-Mail-Adresse.', 'error');
    return;
  }

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
    const zipCode = document.getElementById('svcZip').value.trim();
    if (!zipCode) { notify('Bitte eine Postleitzahl angeben.', 'error'); return; }
    try {
      await fetchAPI('/services', 'POST', { title, description: desc, category, price, zipCode }, 'provider_jwt');
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
async function loadBookings() {
  const grid = document.getElementById('bookingsGrid');
  grid.innerHTML = `<div class="empty-state"><div class="empty-icon">⏳</div><p>Lade Anfragen…</p></div>`;

  if (!localStorage.getItem('provider_jwt')) {
    grid.innerHTML = `<div class="empty-state"><p>Bitte neu anmelden.</p></div>`;
    return;
  }
  try {
    const bookings = await fetchAPI('/bookings/provider/me', 'GET', null, 'provider_jwt');
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
  const review = b.review;

  return `
    <div class="svc-card appointment-card ${statusClass}">
      <div class="svc-top">
        <span class="cat-badge status-badge ${statusClass}">${esc(b.status)}</span>
        <span class="appointment-date">${formatDateShort(b.bookingDate)}</span>
      </div>
      <div class="svc-title">${esc(b.serviceTitle)}</div>
      <div class="svc-desc">Angefragt von: <strong>${esc(b.customerName)}</strong></div>
      ${review ? renderProviderReview(review) : renderProviderReviewEmpty(b.status)}

      ${b.status === 'PENDING' ? `
        <div class="svc-footer">
          <button class="btn btn-primary btn-sm" onclick="updateBookingStatus('${b.id}', 'ACCEPTED')">✅ Akzeptieren</button>
          <button class="btn btn-danger btn-sm" onclick="updateBookingStatus('${b.id}', 'REJECTED')">❌ Ablehnen</button>
          <button class="btn btn-ghost btn-sm" onclick="openChatModal('${b.id}', 'provider_jwt')">Nachrichten</button>
        </div>
      ` : b.status === 'ACCEPTED' ? `
        <div class="svc-footer">
          <button class="btn btn-primary btn-sm" onclick="updateBookingStatus('${b.id}', 'COMPLETED')">✓ Abschließen</button>
          <button class="btn btn-ghost btn-sm" onclick="openChatModal('${b.id}', 'provider_jwt')">Nachrichten</button>
        </div>
      ` : `
        <div class="svc-footer">
          <span class="muted-text">Buchung ist abgeschlossen/abgelehnt.</span>
          <button class="btn btn-ghost btn-sm" onclick="openChatModal('${b.id}', 'provider_jwt')">Nachrichten</button>
        </div>
      `}
    </div>
  `;
}

function serviceRatingPanel(s) {
  const count = s.reviewCount || 0;
  const avg = s.averageRating || 0;

  return `
    <div class="service-rating-panel ${count ? '' : 'is-empty'}">
      <div class="service-rating-score">
        <strong>${count ? avg.toFixed(1) : '–'}</strong>
        <span>${starString(avg)}</span>
      </div>
      <div class="service-rating-copy">
        <b>${count ? `${count} Bewertung${count === 1 ? '' : 'en'}` : 'Noch keine Bewertungen'}</b>
        <small>${count ? 'Kundenfeedback zu diesem Service' : 'Erscheint nach der ersten Kundenbewertung'}</small>
      </div>
    </div>
  `;
}

function renderProviderReview(review) {
  return `
    <div class="booking-review">
      <div class="booking-review-head">
        <span class="booking-review-label">Kundenbewertung</span>
        <span class="booking-review-stars">${starString(review.rating || 0)}</span>
      </div>
      <div class="booking-review-meta">${esc(review.reviewerName || 'Kunde')}</div>
      ${review.comment ? `<p class="booking-review-comment">“${esc(review.comment)}”</p>` : `
        <p class="booking-review-comment muted">Ohne Kommentar abgegeben.</p>
      `}
    </div>
  `;
}

function renderProviderReviewEmpty(status) {
  const waiting = status === 'COMPLETED';

  return `
    <div class="booking-review is-empty">
      <div class="booking-review-head">
        <span class="booking-review-label">${waiting ? 'Bewertung ausstehend' : 'Kundenbewertung'}</span>
        <span class="booking-review-stars">☆☆☆☆☆</span>
      </div>
      <p class="booking-review-comment muted">
        ${waiting ? 'Der Kunde hat diese Buchung noch nicht bewertet.' : 'Bewertungen erscheinen hier nach abgeschlossenen Buchungen.'}
      </p>
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
function starString(rating) {
  const full = Math.max(0, Math.min(5, Math.round(rating)));
  return '★★★★★'.slice(0, full) + '☆☆☆☆☆'.slice(0, 5 - full);
}

// ── Buchungs-Status ändern (PUT) ──────────────────────────────────────────────
async function updateBookingStatus(bookingId, newStatus) {
  try {
    await fetchAPI(`/bookings/${bookingId}/status`, 'PUT', { status: newStatus }, 'provider_jwt');
    const label = newStatus === 'ACCEPTED' ? 'akzeptiert' : newStatus === 'COMPLETED' ? 'abgeschlossen' : 'abgelehnt';
    notify(`Buchung wurde ${label}!`, 'success');
    loadBookings();
  } catch {
    notify('Fehler beim Ändern des Status.', 'error');
  }
}

let activeChatBookingId = null;

async function openChatModal(bookingId, tokenKey) {
  activeChatBookingId = bookingId;
  document.getElementById('chatModal').classList.add('open');
  await loadChatMessages(tokenKey);
}

function closeChatModal() {
  document.getElementById('chatModal').classList.remove('open');
  activeChatBookingId = null;
}

async function loadChatMessages(tokenKey) {
  const thread = document.getElementById('chatThread');
  thread.innerHTML = `<div class="muted-text">Nachrichten werden geladen...</div>`;
  try {
    const messages = await fetchAPI(`/messages/booking/${activeChatBookingId}`, 'GET', null, tokenKey);
    thread.innerHTML = messages.length ? messages.map(renderChatMessage).join('') : `<div class="muted-text">Noch keine Nachrichten.</div>`;
  } catch {
    thread.innerHTML = `<div class="muted-text">Nachrichten konnten nicht geladen werden.</div>`;
  }
}

function renderChatMessage(message) {
  return `
    <div class="chat-message">
      <strong>${esc(message.senderName || 'User')}</strong>
      <p>${esc(message.content || '')}</p>
    </div>
  `;
}

async function sendChatMessage(tokenKey) {
  const input = document.getElementById('chatInput');
  const content = input.value.trim();
  if (!content || !activeChatBookingId) return;
  try {
    await fetchAPI(`/messages/booking/${activeChatBookingId}`, 'POST', { content }, tokenKey);
    input.value = '';
    await loadChatMessages(tokenKey);
  } catch (e) {
    notify(e.message || 'Nachricht konnte nicht gesendet werden.', 'error');
  }
}
