// customer-app.js – Marktplatz & Buchungen aus Kundensicht
// CAT_LABELS, esc() und notify() kommen aus utils.js

// ── State ─────────────────────────────────────────────────────────────────────
let allServices    = [];
let activeCategory = '';
let editingPaymentMethodId = null;
let cachedPaymentMethods = [];
let activeCheckoutBookingId = null;

// ── Init ──────────────────────────────────────────────────────────────────────
(function init() {
  handleAuthLinks();
  updateNavUI();
  loadWeather();
  loadServices();

  document.getElementById('searchInput').addEventListener('keydown', e => {
    if (e.key === 'Enter') applyFilters();
  });

  document.getElementById('filterChips').addEventListener('click', e => {
    const chip = e.target.closest('.chip');
    if (!chip) return;
    document.querySelectorAll('#filterChips .chip').forEach(c => c.classList.remove('active'));
    chip.classList.add('active');
    activeCategory = chip.dataset.cat;
    document.getElementById('categoryFilter').value = activeCategory;
    renderServices();
  });
})();

// ── Wetter-Widget ─────────────────────────────────────────────────────────────
async function loadWeather() {
  try {
    const d = await fetchAPI('/weather/current?city=Vienna', 'GET', null, 'customer_jwt');
    document.getElementById('weatherIcon').textContent = weatherEmoji(d.main);
    document.getElementById('weatherText').textContent =
      `${d.temperature}°C · ${d.description} · ${d.city || 'Wien'}`;
  } catch {
    document.getElementById('weatherText').textContent = 'Wetter nicht verfügbar';
  }
}

// ── Wetter-Vorhersage für den Wunschtermin ────────────────────────────────────
// Heutiges Datum als YYYY-MM-DD (lokale Zeitzone)
function todayISO() {
  const d = new Date();
  d.setMinutes(d.getMinutes() - d.getTimezoneOffset());
  return d.toISOString().split('T')[0];
}

function weatherEmoji(main) {
  const map = {
    Clear: '☀️', Clouds: '☁️', Rain: '🌧️', Drizzle: '🌦️',
    Thunderstorm: '⛈️', Snow: '❄️', Mist: '🌫️', Fog: '🌫️'
  };
  return map[main] || '🌡️';
}

// Reagiert auf die Datumsauswahl: zeigt nur einen Hinweis, wenn der Termin in den nächsten 5 Tagen liegt
async function onBookingDateChange() {
  const hint  = document.getElementById('bookingWeather');
  const value = document.getElementById('bookingDate').value;
  hint.textContent = '';
  if (!value) return;

  const diffDays = Math.round((new Date(value) - new Date(todayISO())) / 86400000);
  if (diffDays < 0 || diffDays > 5) return; // Forecast reicht nur ~5 Tage

  hint.textContent = 'Wetter wird geladen…';
  try {
    const block = await fetchForecastFor(value);
    if (!block) { hint.textContent = ''; return; }
    hint.textContent = `${weatherEmoji(block.main)} Voraussichtlich ${block.temperature}°C, ${block.description}`;
  } catch {
    hint.textContent = '';
  }
}

async function fetchForecastFor(dateStr) {
  return fetchAPI(`/weather/forecast?city=Vienna&date=${encodeURIComponent(dateStr)}`, 'GET', null, 'customer_jwt');
}

// ── Services laden & rendern ──────────────────────────────────────────────────
async function loadServices() {
  const grid = document.getElementById('servicesGrid');
  grid.innerHTML = `<div class="empty-state"><div class="empty-icon">⏳</div><p>Wird geladen…</p></div>`;
  try {
    allServices = await fetchAPI('/services', 'GET', null, 'customer_jwt');
    renderServices();
  } catch {
    grid.innerHTML = `
      <div class="empty-state">
        <div class="empty-icon">⚠️</div>
        <p>Services konnten nicht geladen werden.<br/>Läuft das Backend auf Port 8081?</p>
      </div>`;
    document.getElementById('countPill').textContent = '0';
  }
}

function applyFilters() {
  const cat = document.getElementById('categoryFilter').value;
  if (cat !== activeCategory) {
    activeCategory = cat;
    document.querySelectorAll('#filterChips .chip').forEach(c => {
      c.classList.toggle('active', c.dataset.cat === cat);
    });
  }
  renderServices();
}

function renderServices() {
  const q   = document.getElementById('searchInput').value.toLowerCase().trim();
  const cat = activeCategory;
  const location = (document.getElementById('locationFilter')?.value || '').toLowerCase().trim();
  const maxPrice = parseFloat(document.getElementById('maxPriceFilter')?.value || '');
  const minRating = parseFloat(document.getElementById('minRatingFilter')?.value || '0');
  const sort = document.getElementById('sortFilter')?.value || 'recommended';

  const filtered = allServices.filter(s => {
    const matchText = !q || (s.title + ' ' + s.description + ' ' + (s.providerName || '')).toLowerCase().includes(q);
    const matchCat  = !cat || s.category === cat;
    const matchLocation = !location || String(s.location || '').toLowerCase().includes(location);
    const matchPrice = Number.isNaN(maxPrice) || Number(s.price || 0) <= maxPrice;
    const matchRating = !minRating || Number(s.averageRating || 0) >= minRating;
    return matchText && matchCat && matchLocation && matchPrice && matchRating;
  }).sort((a, b) => {
    if (sort === 'rating') return Number(b.averageRating || 0) - Number(a.averageRating || 0);
    if (sort === 'priceAsc') return Number(a.price || 0) - Number(b.price || 0);
    if (sort === 'priceDesc') return Number(b.price || 0) - Number(a.price || 0);
    return Number(b.trustScore || 0) - Number(a.trustScore || 0);
  });

  document.getElementById('countPill').textContent = filtered.length + ' gefunden';
  const grid = document.getElementById('servicesGrid');

  if (filtered.length === 0) {
    grid.innerHTML = `
      <div class="empty-state">
        <div class="empty-icon">🔍</div>
        <p>Keine Services gefunden. Versuch einen anderen Suchbegriff.</p>
      </div>`;
    return;
  }

  grid.innerHTML = filtered.map(s => `
    <article class="service-card" onclick="openServicePage('${s.id}')">
      ${catImage(s.category, s.imageUrl)}
      <div class="card-top">
        <span class="cat-badge">${CAT_LABELS[s.category] || s.category}</span>
        <span class="card-price">€${parseFloat(s.price).toFixed(2)}<small>/Std</small></span>
      </div>
      ${trustBadge(s.trustScore)}
      <div class="card-title">${esc(s.title)}</div>
      <div class="card-desc">${esc(s.description)}</div>
      <div class="card-desc">${serviceMetaLine(s)}</div>
      ${serviceRatingPanel(s)}
      <div class="card-footer">
        <div class="provider-row">
          ${avatarHtml(s.providerName, s.providerProfileImageUrl)}
          <span class="provider-name">${esc(s.providerName || 'Unbekannt')}</span>
        </div>
        <span class="avail-dot">● Verfügbar</span>
      </div>
    </article>
  `).join('');
}

function serviceMetaLine(s) {
  const parts = [];
  if (s.estimatedHours) parts.push(`ca. ${Number(s.estimatedHours).toFixed(1)} Std`);
  if (s.deliverableType === 'DIGITAL') parts.push('Digital lieferbar');
  if (s.deliverableType === 'HYBRID') parts.push('Digital & vor Ort');
  return parts.length ? parts.map(esc).join(' · ') : 'Flexible Abwicklung';
}

function openServicePage(id) {
  window.location.href = `service-detail.html?id=${encodeURIComponent(id)}`;
}

// ── Service-Detail-Modal ──────────────────────────────────────────────────────
async function openServiceModal(id) {
  const s = allServices.find(x => x.id === id);
  if (!s) return;

  document.getElementById('serviceModalContent').innerHTML = `
    <div class="modal-header">
      <h3 class="modal-title">${esc(s.title)}</h3>
      <button class="modal-close" onclick="closeServiceModal()">✕</button>
    </div>
    <div class="modal-meta">
      <div class="meta-item">
        <span class="meta-label">Kategorie</span>${CAT_LABELS[s.category] || s.category}
      </div>
      <div class="meta-item">
        <span class="meta-label">Anbieter</span>${esc(s.providerName || '–')}
      </div>
      <div class="meta-item">
        <span class="meta-label">Ort</span>${esc(s.location || '–')}
      </div>
      <div class="meta-item">
        <span class="meta-label">Bewertung</span>${ratingHtml(s)}
      </div>
    </div>
    <p class="modal-desc">${esc(s.description)}</p>
    <section class="service-reviews" id="serviceReviews">
      <div class="service-reviews-head">
        <div>
          <span class="service-reviews-kicker">Kundenstimmen</span>
          <h4>Bewertungen zu diesem Service</h4>
        </div>
        <span class="service-reviews-summary">${ratingHtml(s)}</span>
      </div>
      <div class="service-reviews-list">
        <div class="review-loading">Bewertungen werden geladen…</div>
      </div>
    </section>
    <div class="form-group">
      <label class="form-label">Wunschtermin</label>
      <input class="form-input" type="date" id="bookingDate" min="${todayISO()}" onchange="onBookingDateChange()" />
      <div id="bookingWeather" class="booking-weather"></div>
    </div>
    <div class="price-block">
      <div>
        <div class="price-note">Stundensatz</div>
        <div class="price-big">€${parseFloat(s.price).toFixed(2)}</div>
      </div>
      <button class="btn btn-primary btn-lg" onclick="doBook('${s.id}','${esc(s.title)}',${s.price})">
        Jetzt buchen
      </button>
    </div>
  `;
  document.getElementById('serviceModal').classList.add('open');
  renderServiceReviews(s.reviews || []);
}

function closeServiceModal() {
  document.getElementById('serviceModal').classList.remove('open');
}

// ── Buchung ───────────────────────────────────────────────────────────────────
async function doBook(serviceId, title, price) {
  if (!localStorage.getItem('customer_jwt')) {
    closeServiceModal();
    openAuthModal('login');
    notify('Bitte zuerst anmelden um zu buchen.', 'error');
    return;
  }

  if (!isCurrentCustomerEmailVerified()) {
    closeServiceModal();
    openAuthModal('verify');
    notify('Bitte verifiziere zuerst deine E-Mail-Adresse.', 'error');
    return;
  }

  const bookingDate = document.getElementById('bookingDate').value;
  if (!bookingDate) { notify('Bitte wähle einen Wunschtermin.', 'error'); return; }

  try {
    await fetchAPI('/bookings', 'POST', {
      serviceOfferingId: serviceId,
      bookingDate: bookingDate
    }, 'customer_jwt');

    // Erfolgsmeldung wird erst nach erfolgreicher Antwort des Backends angezeigt
    document.getElementById('serviceModalContent').innerHTML = `
      <div class="book-success">
        <div class="success-icon">✓</div>
        <h3>Buchung erfolgreich!</h3>
        <p>Du hast <strong>${esc(title)}</strong> gebucht.<br/>Der Anbieter meldet sich in Kürze bei dir.</p>
        <br/>
        <button class="btn btn-ghost" onclick="closeServiceModal()">Schließen</button>
      </div>`;
    notify('Buchung wurde gespeichert ✓', 'success');
  } catch {
    notify('Fehler bei der Buchung: Bitte überprüfe deine Daten.', 'error');
  }
}

// ── Auth ──────────────────────────────────────────────────────────────────────
function openAuthModal(tab) {
  switchAuthTab(tab || 'login');
  document.getElementById('authModal').classList.add('open');
}
function closeAuthModal() {
  document.getElementById('authModal').classList.remove('open');
}

function switchAuthTab(tab) {
  document.getElementById('loginForm').style.display = tab === 'login'    ? 'block' : 'none';
  document.getElementById('regForm').style.display   = tab === 'register' ? 'block' : 'none';
  document.getElementById('forgotForm').style.display = tab === 'forgot'   ? 'block' : 'none';
  document.getElementById('resetForm').style.display  = tab === 'reset'    ? 'block' : 'none';
  document.getElementById('verifyNotice').style.display = tab === 'verify' ? 'block' : 'none';
  document.getElementById('tabLogin').classList.toggle('active', tab === 'login');
  document.getElementById('tabReg').classList.toggle('active',   tab === 'register');

  if (tab === 'forgot') {
    document.getElementById('forgotEmail').value = document.getElementById('loginEmail').value.trim();
  }
}

async function doLogin() {
  const email    = document.getElementById('loginEmail').value.trim().toLowerCase();
  const password = document.getElementById('loginPassword').value;
  if (!email || !password) { notify('Bitte E-Mail und Passwort eingeben.', 'error'); return; }
  try {
    const data = await fetchAPI('/auth/login', 'POST', { email, password }, 'customer_jwt');
    localStorage.setItem('customer_jwt', data.token);
    localStorage.setItem('customer_user_id', data.userId);
    localStorage.setItem('customer_email', email);
    localStorage.setItem('customer_email_verified', String(data.emailVerified !== false));
    updateNavUI();
    closeAuthModal();
    if (data.emailVerified === false) {
      notify('Bitte verifiziere deine E-Mail-Adresse, bevor du Services buchst.', 'info');
      return;
    }
    notify('Erfolgreich angemeldet ✓', 'success');
  } catch {
    notify('Login fehlgeschlagen. E-Mail oder Passwort falsch?', 'error');
  }
}

async function doRegister() {
  const firstName   = document.getElementById('regFirst').value.trim();
  const lastName    = document.getElementById('regLast').value.trim();
  const email       = document.getElementById('regEmail').value.trim().toLowerCase();
  const password    = document.getElementById('regPassword').value;
  const accountType = document.getElementById('regType').value;
  if (!firstName || !lastName || !email || !password) {
    notify('Bitte alle Felder ausfüllen.', 'error'); return;
  }
  if (!['CUSTOMER', 'PROVIDER'].includes(accountType)) {
    notify('Bitte wähle Kunde oder Handwerker aus.', 'error'); return;
  }
  try {
    await fetchAPI('/auth/register', 'POST', { firstName, lastName, email, password, accountType }, 'customer_jwt');
    notify('Konto erstellt! Bitte verifiziere deine E-Mail-Adresse über den Link in der Mail.', 'success');
    switchAuthTab('login');
    document.getElementById('loginEmail').value = email;
  } catch (e) {
    notify(e.message || 'Registrierung fehlgeschlagen.', 'error');
  }
}

async function requestPasswordReset() {
  const email = document.getElementById('forgotEmail').value.trim().toLowerCase();
  if (!email) { notify('Bitte gib deine E-Mail ein.', 'error'); return; }

  try {
    const data = await fetchAPI('/auth/forgot-password', 'POST', { email }, 'customer_jwt');
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
    const data = await fetchAPI('/auth/reset-password', 'POST', { token, newPassword }, 'customer_jwt');
    notify(data.message || 'Passwort wurde aktualisiert.', 'success');
    document.getElementById('loginEmail').value = document.getElementById('forgotEmail').value.trim().toLowerCase();
    document.getElementById('loginPassword').value = '';
    document.getElementById('resetNewPassword').value = '';
    switchAuthTab('login');
  } catch (e) {
    notify(e.message || 'Passwort konnte nicht gesetzt werden.', 'error');
  }
}

async function resendVerificationMail() {
  const email = (document.getElementById('loginEmail').value || localStorage.getItem('customer_email') || '').trim().toLowerCase();
  if (!email) { notify('Bitte gib zuerst deine E-Mail im Login-Feld ein.', 'error'); return; }

  try {
    const data = await fetchAPI('/auth/resend-verification', 'POST', { email }, 'customer_jwt');
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
    openAuthModal('reset');
    window.history.replaceState({}, document.title, window.location.pathname);
    return;
  }

  if (params.get('verified') === 'true') {
    localStorage.setItem('customer_email_verified', 'true');
    notify('E-Mail wurde verifiziert. Du kannst dich jetzt anmelden.', 'success');
    window.history.replaceState({}, document.title, window.location.pathname);
  }
}

function isCurrentCustomerEmailVerified() {
  return localStorage.getItem('customer_email_verified') === 'true';
}

function logout() {
  localStorage.removeItem('customer_jwt');
  localStorage.removeItem('customer_user_id');
  localStorage.removeItem('customer_email');
  localStorage.removeItem('customer_email_verified');
  updateNavUI();
  notify('Abgemeldet.', 'info');
}

// Schaltet die Navigation zwischen ein-/ausgeloggtem Zustand um (nur für CUSTOMER-Tokens)
function updateNavUI() {
  const token     = localStorage.getItem('customer_jwt');
  const loginBtn  = document.getElementById('loginNavBtn');
  const logoutBtn = document.getElementById('logoutBtn');
  const paymentBtn = document.getElementById('paymentSettingsBtn');
  const navUser   = document.getElementById('navUser');
  const tabs      = document.getElementById('customerTabs');

  if (token) {
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      if (payload.accountType === 'CUSTOMER') {
        loginBtn.style.display  = 'none';
        logoutBtn.style.display = 'inline-flex';
        if (paymentBtn) paymentBtn.style.display = 'inline-flex';
        navUser.textContent     = payload.sub || '';
        if (tabs) tabs.style.display = 'flex';
        return;
      }
    } catch { navUser.textContent = ''; }
  }

  // Ausgeloggter Zustand
  loginBtn.style.display  = 'inline-flex';
  logoutBtn.style.display = 'none';
  if (paymentBtn) paymentBtn.style.display = 'none';
  navUser.textContent     = '';
  if (tabs) tabs.style.display = 'none';
  if (typeof switchCustomerTab === 'function') switchCustomerTab('market');
}

// ── Helfer ────────────────────────────────────────────────────────────────────
function initials(name) {
  return (name || '?').split(' ').map(n => n[0]).join('').toUpperCase().slice(0, 2);
}

// ── Tabs umschalten (Kunde) ───────────────────────────────────────────────────
function switchCustomerTab(tab) {
  document.getElementById('marketView').style.display   = tab === 'market'   ? 'block' : 'none';
  document.getElementById('bookingsView').style.display = tab === 'bookings' ? 'block' : 'none';
  document.getElementById('tabMarket').classList.toggle('active', tab === 'market');
  document.getElementById('tabMyBookings').classList.toggle('active', tab === 'bookings');

  if (tab === 'bookings') loadCustomerBookings();
}

// ── Buchungen des Kunden laden ────────────────────────────────────────────────
async function loadCustomerBookings() {
  const grid = document.getElementById('customerBookingsGrid');
  grid.innerHTML = `<div class="empty-state"><div class="empty-icon">⏳</div><p>Lade deine Buchungen…</p></div>`;

  if (!localStorage.getItem('customer_jwt')) {
    grid.innerHTML = `<div class="empty-state"><p>Bitte logge dich ein.</p></div>`;
    return;
  }

  try {
    const bookings = await fetchAPI('/bookings/customer/me', 'GET', null, 'customer_jwt');

    if (bookings.length === 0) {
      grid.innerHTML = `<div class="empty-state"><div class="empty-icon">📭</div><p>Du hast noch keine Services gebucht.</p></div>`;
      return;
    }

    grid.innerHTML = bookings.map(b => {
      const statusClass = bookingStatusClass(b.status);
      const statusText  = bookingStatusText(b.status);
      const dateLabel   = b.bookingDate ? `📅 Wunschtermin: ${b.bookingDate}` : '📅 Kein Termin angegeben';
      const review      = b.review;
      const canPay = (b.status === 'ACCEPTED' || b.status === 'COMPLETED') && b.paymentStatus !== 'PAID';

      return `
      <div class="service-card booking-card ${statusClass}" style="cursor: default;">
        <div class="card-top">
          <span class="cat-badge status-badge ${statusClass}">${statusText}</span>
        </div>
        <div class="card-title" style="margin-top: 10px; font-size: 1.1rem;">${esc(b.serviceTitle || '–')}</div>
      <div class="provider-row" style="margin-top:.45rem">
        ${avatarHtml(b.providerName, b.providerProfileImageUrl)}
        <span class="provider-name">Angeboten von: <strong>${esc(b.providerName || '–')}</strong></span>
      </div>
        <div class="booking-date-line">${dateLabel}</div>
        <div class="booking-date-line">Zahlung: <strong>${esc(b.paymentStatus || 'UNPAID')}</strong>${b.actualHours ? ` · Aufwand: <strong>${Number(b.actualHours).toFixed(2)} Std</strong>` : ''}</div>
        ${b.deliveryAvailable && b.deliveryUrl ? `<div class="booking-date-line">Lieferung: <button class="btn btn-ghost btn-sm" onclick="openDelivery('${b.id}')">${esc(b.deliveryLabel || 'Download öffnen')}</button>${b.deliveryExpiresAt ? ` · gültig bis ${formatDateTimeShort(b.deliveryExpiresAt)}` : ''}</div>` : b.deliveryUrl ? `<div class="booking-date-line">Lieferung: nach Zahlung verfügbar</div>` : ''}
        ${b.providerNotes ? `<div class="booking-date-line">Notiz: ${esc(b.providerNotes)}</div>` : ''}
        ${review ? renderBookingReview(review) : renderBookingReviewEmpty(b.status)}
        <div class="booking-actions">
          <button class="btn btn-ghost btn-sm" onclick="openChatModal('${b.id}', 'customer_jwt')">Nachrichten</button>
          ${canPay ? `<button class="btn btn-primary btn-sm" onclick="payBooking('${b.id}')">Sicher bezahlen</button>` : ''}
        </div>
        ${b.status === 'COMPLETED' && !review ? `
        <div class="booking-actions">
          <button class="btn btn-primary btn-sm"
                  onclick="openReviewModal('${b.id || ''}', '${esc(b.serviceTitle || '')}')">⭐ Bewerten</button>
        </div>
        ` : ''}
      </div>`;
    }).join('');
  } catch (e) {
    console.error('Fehler beim Laden der Kunden-Buchungen:', e);
    grid.innerHTML = `<div class="empty-state"><p>❌ Fehler beim Laden der Buchungen.</p></div>`;
    notify('Fehler beim Laden der Buchungen.', 'error');
  }
}

function renderServiceReviews(reviews) {
  const host = document.querySelector('#serviceReviews .service-reviews-list');
  if (!host) return;
  host.innerHTML = renderPublicReviews(reviews);
}

async function openPaymentSettings() {
  document.getElementById('paymentModal').classList.add('open');
  await loadCustomerProfile();
  await loadPaymentMethods();
}

function closePaymentSettings() {
  document.getElementById('paymentModal').classList.remove('open');
}

async function loadPaymentMethods() {
  const host = document.getElementById('paymentMethodsList');
  host.innerHTML = `<div class="review-loading">Zahlungsarten werden geladen...</div>`;
  try {
    const methods = await fetchAPI('/payment-methods', 'GET', null, 'customer_jwt');
    cachedPaymentMethods = methods;
    host.innerHTML = methods.length ? methods.map(m => `
      <div class="public-review">
        <div class="public-review-top">
          <div><strong>${esc(m.brand)} •••• ${esc(m.last4)}</strong><span>${esc(m.holderName || '')}</span></div>
          <span>
            <button class="btn btn-ghost btn-sm" onclick="editPaymentMethod('${m.id}')">Ändern</button>
            <button class="btn btn-danger btn-sm" onclick="deletePaymentMethod('${m.id}')">Löschen</button>
          </span>
        </div>
        <p class="muted">Gültig bis ${String(m.expiryMonth).padStart(2, '0')}/${m.expiryYear}</p>
      </div>
    `).join('') : `<div class="review-empty">Noch keine Zahlungsart gespeichert.</div>`;
  } catch {
    host.innerHTML = `<div class="review-empty">Zahlungsarten konnten nicht geladen werden.</div>`;
  }
}

async function loadCustomerProfile() {
  const userId = localStorage.getItem('customer_user_id');
  if (!userId) return;
  try {
    const user = await fetchAPI(`/users/${userId}`, 'GET', null, 'customer_jwt');
    document.getElementById('settingsFirstName').value = user.firstName || '';
    document.getElementById('settingsLastName').value = user.lastName || '';
    document.getElementById('settingsProfileImage').value = user.profileImageUrl || '';
  } catch (e) {
    notify(e.message || 'Profil konnte nicht geladen werden.', 'error');
  }
}

async function saveCustomerProfile() {
  const userId = localStorage.getItem('customer_user_id');
  if (!userId) return;
  const payload = {
    firstName: document.getElementById('settingsFirstName').value.trim(),
    lastName: document.getElementById('settingsLastName').value.trim(),
    profileImageUrl: document.getElementById('settingsProfileImage').value.trim()
  };
  try {
    await fetchAPI(`/users/${userId}`, 'PUT', payload, 'customer_jwt');
    notify('Profil gespeichert.', 'success');
  } catch (e) {
    notify(e.message || 'Profil konnte nicht gespeichert werden.', 'error');
  }
}

async function savePaymentMethod() {
  const rawNumber = document.getElementById('cardNumber').value.replace(/\D/g, '');
  const holderName = document.getElementById('cardHolder').value.trim();
  const expiryMonth = parseInt(document.getElementById('cardMonth').value, 10);
  const expiryYear = parseInt(document.getElementById('cardYear').value, 10);
  if (rawNumber.length < 12 || !holderName || !expiryMonth || !expiryYear) {
    notify('Bitte Kartendaten vollständig eingeben.', 'error');
    return;
  }
  const last4 = rawNumber.slice(-4);
  const brand = detectCardBrand(rawNumber);
  const providerToken = `tok_demo_${crypto.randomUUID ? crypto.randomUUID() : Date.now()}`;
  try {
    const payload = { brand, last4, holderName, expiryMonth, expiryYear, providerToken, defaultMethod: true };
    if (editingPaymentMethodId) {
      await fetchAPI(`/payment-methods/${editingPaymentMethodId}`, 'PUT', payload, 'customer_jwt');
    } else {
      await fetchAPI('/payment-methods', 'POST', payload, 'customer_jwt');
    }
    editingPaymentMethodId = null;
    document.getElementById('cardNumber').value = '';
    document.getElementById('cardHolder').value = '';
    notify('Zahlungsart gespeichert.', 'success');
    loadPaymentMethods();
  } catch (e) {
    notify(e.message || 'Zahlungsart konnte nicht gespeichert werden.', 'error');
  }
}

function editPaymentMethod(id) {
  const method = cachedPaymentMethods.find(m => m.id === id);
  if (!method) return;
  editingPaymentMethodId = id;
  document.getElementById('cardHolder').value = method.holderName || '';
  document.getElementById('cardMonth').value = method.expiryMonth || '';
  document.getElementById('cardYear').value = method.expiryYear || '';
  document.getElementById('cardNumber').value = '';
  notify('Bitte Kartennummer erneut eingeben. Sie wird nicht gespeichert.', 'info');
}

async function deletePaymentMethod(id) {
  try {
    await fetchAPI(`/payment-methods/${id}`, 'DELETE', null, 'customer_jwt');
    notify('Zahlungsart gelöscht.', 'success');
    loadPaymentMethods();
  } catch (e) {
    notify(e.message || 'Zahlungsart konnte nicht gelöscht werden.', 'error');
  }
}

function detectCardBrand(number) {
  if (number.startsWith('4')) return 'Visa';
  if (/^5[1-5]/.test(number)) return 'Mastercard';
  if (/^3[47]/.test(number)) return 'Amex';
  return 'Card';
}

function payBooking(bookingId) {
  activeCheckoutBookingId = bookingId;
  document.getElementById('checkoutModal').classList.add('open');
  renderCheckoutFields();
}

function closeCheckoutModal() {
  document.getElementById('checkoutModal').classList.remove('open');
  activeCheckoutBookingId = null;
}

function renderCheckoutFields() {
  const method = document.getElementById('checkoutMethod').value;
  const host = document.getElementById('checkoutFields');
  if (method === 'STRIPE') {
    host.innerHTML = `<div class="review-loading">Gespeicherte Karte wird verwendet. Du kannst Karten in den Settings ändern.</div>`;
    return;
  }
  if (method === 'PAYPAL') {
    host.innerHTML = `
      <div class="form-group">
        <label class="form-label">PayPal E-Mail</label>
        <input class="form-input" id="checkoutPaypalEmail" type="email" placeholder="paypal@beispiel.at" />
      </div>`;
    return;
  }
  host.innerHTML = `
    <div class="form-group">
      <label class="form-label">Kontoinhaber</label>
      <input class="form-input" id="checkoutSepaHolder" placeholder="Max Muster" />
    </div>
    <div class="form-group">
      <label class="form-label">IBAN</label>
      <input class="form-input" id="checkoutIban" placeholder="AT00 0000 0000 0000 0000" />
    </div>`;
}

async function confirmBookingPayment() {
  const bookingId = activeCheckoutBookingId;
  if (!bookingId) return;
  const method = document.getElementById('checkoutMethod').value;
  try {
    if (method === 'STRIPE') {
      const methods = await fetchAPI('/payment-methods', 'GET', null, 'customer_jwt');
      if (!Array.isArray(methods) || methods.length === 0) {
        notify('Bitte zuerst eine Karte in den Settings hinterlegen.', 'info');
        closeCheckoutModal();
        openPaymentSettings();
        return;
      }
    }
    if (method === 'PAYPAL' && !document.getElementById('checkoutPaypalEmail').value.trim()) {
      notify('Bitte PayPal E-Mail eingeben.', 'error');
      return;
    }
    if (method === 'SEPA') {
      const iban = document.getElementById('checkoutIban').value.replace(/\s/g, '');
      if (iban.length < 15 || !document.getElementById('checkoutSepaHolder').value.trim()) {
        notify('Bitte SEPA-Daten vollständig eingeben.', 'error');
        return;
      }
    }
    await fetchAPI(`/bookings/${bookingId}/checkout`, 'POST', { provider: method }, 'customer_jwt');
    await fetchAPI(`/bookings/${bookingId}/mark-paid`, 'POST', null, 'customer_jwt');
    notify('Zahlung wurde bestätigt.', 'success');
    closeCheckoutModal();
    loadCustomerBookings();
  } catch (e) {
    notify(e.message || 'Zahlung konnte nicht gestartet werden.', 'error');
  }
}

function formatDateTimeShort(value) {
  if (!value) return '-';
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return '-';
  return d.toLocaleString('de-AT', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' });
}

async function openDelivery(bookingId) {
  try {
    const data = await fetchAPI(`/bookings/${bookingId}/delivery/url`, 'GET', null, 'customer_jwt');
    window.open(data.url, '_blank', 'noopener');
  } catch (e) {
    notify(e.message || 'Lieferung ist nicht verfügbar.', 'error');
  }
}

// Mappt den Status auf die CSS-Klasse / das Anzeige-Label der Buchungs-Kachel
function bookingStatusClass(status) {
  if (status === 'ACCEPTED')  return 'status-accepted';
  if (status === 'REJECTED')  return 'status-rejected';
  if (status === 'COMPLETED') return 'status-completed';
  return 'status-pending';
}
function bookingStatusText(status) {
  if (status === 'ACCEPTED')  return '✓ Akzeptiert';
  if (status === 'REJECTED')  return '❌ Abgelehnt';
  if (status === 'COMPLETED') return 'Abgeschlossen';
  if (status === 'CANCELLED') return 'Storniert';
  return 'Wartet auf Antwort';
}

// ── Bewertungs-Anzeige (Sterne aus den Backend-Werten) ────────────────────────
function starString(rating) {
  const full = Math.max(0, Math.min(5, Math.round(rating)));
  return '★★★★★'.slice(0, full) + '☆☆☆☆☆'.slice(0, 5 - full);
}

function ratingHtml(s) {
  const count = s.reviewCount || 0;
  if (!count) {
    return `<span class="stars">☆☆☆☆☆</span> <span>Noch keine Bewertungen</span>`;
  }
  const avg = s.averageRating || 0;
  return `<span class="stars">${starString(avg)}</span> ` +
         `<span class="rating-num">${avg.toFixed(1)}</span> <span>(${count})</span>`;
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
        <small>${count ? 'Von gebuchten Kunden bewertet' : 'Bewertungen erscheinen nach abgeschlossenen Buchungen'}</small>
      </div>
    </div>
  `;
}

function renderBookingReview(review) {
  return `
    <div class="booking-review">
      <div class="booking-review-head">
        <span class="booking-review-label">Deine Bewertung</span>
        <span class="booking-review-stars">${starString(review.rating || 0)}</span>
      </div>
      ${review.comment ? `<p class="booking-review-comment">“${esc(review.comment)}”</p>` : `
        <p class="booking-review-comment muted">Ohne Kommentar abgegeben.</p>
      `}
    </div>
  `;
}

function renderBookingReviewEmpty(status) {
  const canReview = status === 'COMPLETED';

  return `
    <div class="booking-review is-empty">
      <div class="booking-review-head">
        <span class="booking-review-label">${canReview ? 'Bewertung offen' : 'Bewertung'}</span>
        <span class="booking-review-stars">☆☆☆☆☆</span>
      </div>
      <p class="booking-review-comment muted">
        ${canReview ? 'Du kannst diese Buchung jetzt bewerten.' : 'Eine Bewertung ist möglich, sobald die Buchung abgeschlossen wurde.'}
      </p>
    </div>
  `;
}

function renderPublicReviews(reviews) {
  if (!Array.isArray(reviews) || reviews.length === 0) {
    return `
      <div class="review-empty">
        Noch keine Bewertungen vorhanden. Du kannst nach einer akzeptierten Buchung die erste Bewertung abgeben.
      </div>
    `;
  }

  return reviews.map(review => `
    <article class="public-review">
      <div class="public-review-top">
        <div>
          <strong>${esc(review.reviewerName || 'Kunde')}</strong>
          <span>${esc(review.serviceTitle || '')}</span>
        </div>
        <span class="booking-review-stars">${starString(review.rating || 0)}</span>
      </div>
      ${review.comment ? `<p>${esc(review.comment)}</p>` : `<p class="muted">Ohne Kommentar bewertet.</p>`}
    </article>
  `).join('');
}

// ── Bewertungs-Modal ──────────────────────────────────────────────────────────
let reviewBookingId = null;
let selectedRating  = 0;

function openReviewModal(bookingId, serviceTitle) {
  reviewBookingId = bookingId;
  selectedRating  = 0;
  document.getElementById('reviewServiceTitle').textContent = serviceTitle;
  document.getElementById('reviewComment').value = '';
  renderStarInput();

  document.querySelectorAll('#starInput .star').forEach(star => {
    star.onclick = () => { selectedRating = parseInt(star.dataset.val, 10); renderStarInput(); };
  });

  document.getElementById('reviewModal').classList.add('open');
}

function closeReviewModal() {
  document.getElementById('reviewModal').classList.remove('open');
}

// Färbt die Sterne bis zur aktuellen Auswahl ein
function renderStarInput() {
  document.querySelectorAll('#starInput .star').forEach(star => {
    star.classList.toggle('filled', parseInt(star.dataset.val, 10) <= selectedRating);
  });
}

async function submitReview() {
  if (selectedRating < 1) {
    notify('Bitte wähle 1 bis 5 Sterne aus.', 'error');
    return;
  }
  const comment = document.getElementById('reviewComment').value.trim();

  try {
    await fetchAPI('/reviews', 'POST', {
      bookingId: reviewBookingId,
      rating:    selectedRating,
      comment:   comment
    }, 'customer_jwt');

    closeReviewModal();
    notify('Danke für deine Bewertung ⭐', 'success');
    loadServices();          // Marktplatz-Sterne aktualisieren
    loadCustomerBookings();  // Buchungsliste neu laden
  } catch (e) {
    notify(e.message || 'Bewertung konnte nicht gespeichert werden.', 'error');
  }
}

function trustBadge(score) {
  const value = Number(score || 0);
  return `<div class="trust-badge"><span>Trust Score</span><strong>${value}</strong></div>`;
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
  thread.innerHTML = `<div class="review-loading">Nachrichten werden geladen...</div>`;
  try {
    const messages = await fetchAPI(`/messages/booking/${activeChatBookingId}`, 'GET', null, tokenKey);
    thread.innerHTML = messages.length ? messages.map(renderChatMessage).join('') : `<div class="review-empty">Noch keine Nachrichten.</div>`;
  } catch {
    thread.innerHTML = `<div class="review-empty">Nachrichten konnten nicht geladen werden.</div>`;
  }
}

function renderChatMessage(message) {
  return `
    <div class="chat-message ${message.senderRole === 'PROVIDER' ? 'from-provider' : 'from-customer'}">
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
