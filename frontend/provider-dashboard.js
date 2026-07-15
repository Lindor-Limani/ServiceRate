// provider-dashboard.js – Service- und Buchungsverwaltung des Handwerkers
// CAT_LABELS, esc() und notify() kommen aus utils.js

// ── State ─────────────────────────────────────────────────────────────────────
let allServices   = [];
let pendingDelete = null;
let editMode      = false;
let providerBookings        = []; // zuletzt geladene Buchungen (für die Kalender-Navigation)
let providerCalendarCursor  = new Date(); // aktuell im Kalender angezeigter Monat
let activeBookingId = null;
let serviceImageUrls = [];
const GLOBAL_VIEW_MODE_KEY = 'servicerate_view_mode';
ensureGlobalViewMode();
let providerViewModes = {
  services: preferredViewMode('services'),
  bookings: preferredViewMode('bookings')
};

document.addEventListener('click', event => {
  const button = event.target.closest('#providerPaypalConnectBtn');
  if (!button) return;
  event.preventDefault();
  startPayPalOnboarding();
});

document.addEventListener('click', event => {
  const button = event.target.closest('#providerPaypalRefreshBtn');
  if (!button) return;
  event.preventDefault();
  refreshPayPalOnboardingStatus();
});

document.addEventListener('click', event => {
  const button = event.target.closest('#providerStripeConnectBtn');
  if (!button) return;
  event.preventDefault();
  startStripeOnboarding();
});

document.addEventListener('click', event => {
  const button = event.target.closest('#providerStripeRefreshBtn');
  if (!button) return;
  event.preventDefault();
  refreshStripeOnboardingStatus();
});

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
  wireProviderFormatting();
  applyProviderViewMode('services');
  applyProviderViewMode('bookings');
  loadServices();
  loadProviderOverview();
  window.addEventListener('pageshow', refreshProviderViewsFromStoredMode);
  notifyPendingPayPalOnboardingWithoutParams();
  notifyPendingStripeOnboardingWithoutParams();
}

function preferredViewMode(area) {
  return readServiceRateViewMode();
}

function ensureGlobalViewMode() {
  ensureServiceRateViewMode();
}

function setProviderViewMode(area, mode) {
  setGlobalViewMode(mode);
  rerenderProviderView('services');
  rerenderProviderView('bookings');
  applyAllProviderViewModes();
}

function applyProviderViewMode(area) {
  providerViewModes[area] = preferredViewMode(area);
  const mode = providerViewModes[area];
  const gridId = area === 'bookings' ? 'bookingsGrid' : 'servicesGrid';
  const prefix = area === 'bookings' ? 'providerBookings' : 'providerServices';
  document.getElementById(gridId)?.classList.toggle('is-list-view', mode === 'list');
  document.getElementById(gridId)?.classList.toggle('is-grid-view', mode === 'grid');
  document.getElementById(`${prefix}GridViewBtn`)?.classList.toggle('active', mode === 'grid');
  document.getElementById(`${prefix}ListViewBtn`)?.classList.toggle('active', mode === 'list');
  document.getElementById('providerDefaultGridViewBtn')?.classList.toggle('active', mode === 'grid');
  document.getElementById('providerDefaultListViewBtn')?.classList.toggle('active', mode === 'list');
}

function applyAllProviderViewModes() {
  applyProviderViewMode('services');
  applyProviderViewMode('bookings');
}

function rerenderProviderView(area) {
  if (area === 'services') {
    if (allServices.length) renderServices();
    return;
  }
  if (area === 'bookings') {
    if (providerBookings.length) renderBookings();
  }
}

function refreshProviderViewsFromStoredMode() {
  if (allServices.length) renderServices();
  if (providerBookings.length) renderBookings();
  applyAllProviderViewModes();
}

function setGlobalViewMode(mode) {
  writeServiceRateViewMode(mode);
}

async function startPayPalOnboarding() {
  const button = document.getElementById('providerPaypalConnectBtn');
  if (button) {
    button.disabled = true;
    button.textContent = 'PayPal wird geöffnet...';
  }

  try {
    if (!localStorage.getItem('provider_jwt')) {
      throw new Error('Bitte zuerst als Handwerker anmelden.');
    }
    const data = await fetchAPI('/providers/me/paypal/onboarding-link', 'POST', null, 'provider_jwt');
    if (!data || !data.actionUrl) {
      throw new Error('PayPal hat keinen Onboarding-Link geliefert.');
    }
    notify('Weiterleitung zu PayPal...', 'info');
    localStorage.setItem('provider_paypal_onboarding_started', 'true');
    window.location.href = data.actionUrl;
  } catch (e) {
    notify(e.message || 'PayPal-Verbindung konnte nicht gestartet werden.', 'error');
    if (button) {
      button.disabled = false;
      button.innerHTML = `<img src="https://www.paypalobjects.com/paypal-ui/logos/svg/paypal-color.svg" alt="PayPal" /> Continue with PayPal`;
    }
  }
}
window.startPayPalOnboarding = startPayPalOnboarding;

async function startStripeOnboarding() {
  const button = document.getElementById('providerStripeConnectBtn');
  if (button) {
    button.disabled = true;
    button.textContent = 'Stripe wird geöffnet...';
  }

  try {
    if (!localStorage.getItem('provider_jwt')) {
      throw new Error('Bitte zuerst als Handwerker anmelden.');
    }
    const data = await fetchAPI('/providers/me/stripe/onboarding-link', 'POST', null, 'provider_jwt');
    if (!data || !data.onboardingUrl) {
      throw new Error('Stripe hat keinen Onboarding-Link geliefert.');
    }
    notify('Weiterleitung zu Stripe...', 'info');
    localStorage.setItem('provider_stripe_onboarding_started', 'true');
    window.location.href = data.onboardingUrl;
  } catch (e) {
    notify(e.message || 'Stripe-Verbindung konnte nicht gestartet werden.', 'error');
    if (button) {
      button.disabled = false;
      button.textContent = 'Stripe verbinden';
    }
  }
}
window.startStripeOnboarding = startStripeOnboarding;

async function refreshStripeOnboardingStatus() {
  const button = document.getElementById('providerStripeRefreshBtn');
  if (button) {
    button.disabled = true;
    button.textContent = 'Status wird geprüft...';
  }
  try {
    const user = await fetchAPI('/providers/me/stripe/onboarding-status', 'POST', null, 'provider_jwt');
    renderProviderStripeStatus(user);
    notify(user.stripeOnboardingStatus === 'CONNECTED'
      ? 'Stripe-Verbindung wurde erkannt.'
      : 'Stripe-Status wurde aktualisiert. Eventuell ist noch ein Schritt bei Stripe offen.', 'success');
  } catch (e) {
    notify(e.message || 'Stripe-Status konnte nicht geprüft werden.', 'error');
  } finally {
    if (button) {
      button.disabled = false;
      button.textContent = 'Status bei Stripe prüfen';
    }
  }
}

async function refreshPayPalOnboardingStatus() {
  const button = document.getElementById('providerPaypalRefreshBtn');
  if (button) {
    button.disabled = true;
    button.textContent = 'Status wird geprüft...';
  }
  try {
    const user = await fetchAPI('/providers/me/paypal/onboarding-status', 'POST', null, 'provider_jwt');
    renderProviderPayPalStatus(user);
    notify(user.paypalOnboardingStatus === 'CONNECTED'
      ? 'PayPal-Verbindung wurde erkannt.'
      : 'PayPal-Status wurde aktualisiert. Eventuell ist noch ein Schritt bei PayPal offen.', 'success');
  } catch (e) {
    notify(e.message || 'PayPal-Status konnte nicht geprüft werden.', 'error');
  } finally {
    if (button) {
      button.disabled = false;
      button.textContent = 'Status bei PayPal prüfen';
    }
  }
}

async function completePayPalOnboarding(state) {
  try {
    const user = await fetchAPI('/providers/me/paypal/onboarding-complete', 'POST', { state }, 'provider_jwt');
    renderProviderPayPalStatus(user);
    notify(user.paypalOnboardingStatus === 'CONNECTED'
      ? 'PayPal-Verbindung wurde sicher bestätigt.'
      : 'PayPal-Rückkehr wurde bestätigt. Eventuell ist noch ein Schritt bei PayPal offen.', 'success');
  } catch (e) {
    notify(e.message || 'PayPal-Rückkehr ist ungültig oder abgelaufen. Bitte starte die Verbindung erneut.', 'error');
  }
}

function wireProviderFormatting() {
  document.getElementById('providerPayoutIban')?.addEventListener('input', e => {
    e.target.value = formatIbanValue(e.target.value);
  });
}

async function loadProviderOverview() {
  try {
    const bookings = await fetchAPI('/bookings/provider/me', 'GET', null, 'provider_jwt');
    const list = Array.isArray(bookings) ? bookings : [];
    const revenue = list
      .filter(b => b.paymentStatus === 'PAID')
      .reduce((sum, b) => sum + (Number(b.actualHours || 1) * Number(b.servicePrice || 0)), 0);
    const completed = list.filter(b => b.status === 'COMPLETED').length;
    const open = list.filter(b => b.status === 'PENDING' || b.status === 'ACCEPTED').length;
    document.getElementById('statCount').textContent = `€${revenue.toFixed(0)}`;
    document.getElementById('statAvgPrice').textContent = completed;
    document.getElementById('statCats').textContent = open;
  } catch {
    document.getElementById('statCount').textContent = '–';
    document.getElementById('statAvgPrice').textContent = '–';
    document.getElementById('statCats').textContent = '–';
  }
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

async function openProviderSettings() {
  document.getElementById('providerSettingsModal').classList.add('open');
  applyAllProviderViewModes();
  await loadProviderProfileSettings();
}

function closeProviderSettings() {
  document.getElementById('providerSettingsModal').classList.remove('open');
}

async function loadProviderProfileSettings() {
  const userId = localStorage.getItem('provider_user_id');
  if (!userId) return;
  try {
    const user = await fetchAPI(`/users/${userId}`, 'GET', null, 'provider_jwt');
    const name = `${user.firstName || ''} ${user.lastName || ''}`.trim() || user.email || 'Profil';
    document.getElementById('providerSettingsFirstName').value = user.firstName || '';
    document.getElementById('providerSettingsLastName').value = user.lastName || '';
    document.getElementById('providerSettingsProfileImage').value = user.profileImageUrl || '';
    document.getElementById('providerSettingsName').textContent = name;
    document.getElementById('providerSettingsAvatar').innerHTML = avatarHtml(name, user.profileImageUrl, 'profile-large');
    document.getElementById('providerSettingsProfilePreview').innerHTML = user.profileImageUrl ? `<img src="${esc(user.profileImageUrl)}" alt="Profilbild Vorschau">` : '';
    document.getElementById('providerPayoutIban').value = user.payoutIban || '';
    document.getElementById('providerPaypalMerchantId').value = user.paypalMerchantId || '';
    document.getElementById('providerPaypalEmail').value = user.paypalEmail || '';
    document.getElementById('providerStripeAccountId').value = user.stripeConnectedAccountId || '';
    renderProviderPayPalStatus(user);
    renderProviderStripeStatus(user);
  } catch (e) {
    notify(e.message || 'Profil konnte nicht geladen werden.', 'error');
  }
}

function renderProviderPayPalStatus(user) {
  const host = document.getElementById('providerPaypalStatus');
  if (!host) return;
  const status = user.paypalOnboardingStatus || 'NOT_CONNECTED';
  const labels = {
    NOT_CONNECTED: 'Verbinde PayPal, damit Kunden online direkt an dich zahlen können.',
    LINK_CREATED: 'Onboarding-Link erstellt. Bitte PayPal-Verbindung abschliessen.',
    CONNECTED: 'Online-Zahlungen können an dein PayPal-Konto geleitet werden.',
    ACTION_REQUIRED: 'PayPal-Konto erkannt, aber es fehlen noch Berechtigungen oder E-Mail-Bestätigung.',
    RETURNED_INCOMPLETE: 'PayPal-Onboarding wurde nicht vollständig abgeschlossen.'
  };
  const meta = [];
  if (user.paypalMerchantId) meta.push(`Merchant ID: ${user.paypalMerchantId}`);
  if (user.paypalPermissionsGranted === false) meta.push('Berechtigungen fehlen');
  if (user.paypalEmailConfirmed === false) meta.push('PayPal-E-Mail nicht bestätigt');
  host.innerHTML = providerPaymentCard('paypal', 'PayPal Marketplace', status, labels[status] || status, meta);
}

function renderProviderStripeStatus(user) {
  const host = document.getElementById('providerStripeStatus');
  if (!host) return;
  const status = user.stripeOnboardingStatus || 'NOT_CONNECTED';
  const labels = {
    NOT_CONNECTED: 'Verbinde Stripe, damit Kunden per Karte zahlen können.',
    ONBOARDING_STARTED: 'Onboarding gestartet. Bitte Stripe-Verbindung abschliessen.',
    CONNECTED: 'Kartenzahlungen können direkt über Stripe Connect verteilt werden.',
    ACTION_REQUIRED: 'Stripe-Konto erkannt, aber es fehlen noch Angaben.'
  };
  const meta = [];
  if (user.stripeConnectedAccountId) meta.push(`Account: ${user.stripeConnectedAccountId}`);
  host.innerHTML = providerPaymentCard('stripe', 'Stripe Connect', status, labels[status] || status, meta);
}

function providerPaymentCard(kind, title, status, copy, meta = []) {
  const statusClass = status === 'CONNECTED'
    ? 'connected'
    : (status === 'ACTION_REQUIRED' || status === 'RETURNED_INCOMPLETE' ? 'action' : 'pending');
  const statusLabel = {
    NOT_CONNECTED: 'Nicht verbunden',
    LINK_CREATED: 'Gestartet',
    ONBOARDING_STARTED: 'Gestartet',
    CONNECTED: 'Verbunden',
    ACTION_REQUIRED: 'Aktion nötig',
    RETURNED_INCOMPLETE: 'Unvollstaendig'
  }[status] || status;
  return `
    <div class="payment-provider-card">
      <div class="payment-provider-head">
        <div class="payment-provider-brand">
          <span class="payment-provider-mark ${kind}">${kind === 'paypal' ? 'P' : 'S'}</span>
          <span>${esc(title)}</span>
        </div>
        <span class="payment-status-badge ${statusClass}">${esc(statusLabel)}</span>
      </div>
      <p>${esc(copy)}</p>
      ${meta.length ? `<div class="payment-meta-list">${meta.map(item => `<span>${esc(item)}</span>`).join('')}</div>` : ''}
    </div>
  `;
}

async function handleProviderProfileFile() {
  const input = document.getElementById('providerSettingsProfileFile');
  const images = await readImageFiles(input, 1);
  if (!images.length) return;
  document.getElementById('providerSettingsProfileImage').value = images[0];
  document.getElementById('providerSettingsProfilePreview').innerHTML = `<img src="${esc(images[0])}" alt="Profilbild Vorschau">`;
  const first = document.getElementById('providerSettingsFirstName').value;
  const last = document.getElementById('providerSettingsLastName').value;
  document.getElementById('providerSettingsAvatar').innerHTML = avatarHtml(`${first} ${last}`.trim(), images[0], 'profile-large');
}

async function saveProviderProfile() {
  const userId = localStorage.getItem('provider_user_id');
  if (!userId) return;
  const payload = {
    firstName: document.getElementById('providerSettingsFirstName').value.trim(),
    lastName: document.getElementById('providerSettingsLastName').value.trim(),
    profileImageUrl: document.getElementById('providerSettingsProfileImage').value.trim(),
    payoutIban: document.getElementById('providerPayoutIban').value.trim()
  };
  try {
    const user = await fetchAPI(`/users/${userId}`, 'PUT', payload, 'provider_jwt');
    const name = `${user.firstName || ''} ${user.lastName || ''}`.trim() || user.email || '';
    document.getElementById('headerUser').textContent = user.email || localStorage.getItem('provider_email') || '';
    document.getElementById('providerSettingsName').textContent = name || 'Profil';
    document.getElementById('providerSettingsAvatar').innerHTML = avatarHtml(name, user.profileImageUrl, 'profile-large');
    notify('Profil gespeichert.', 'success');
    loadServices();
    loadProviderOverview();
  } catch (e) {
    notify(e.message || 'Profil konnte nicht gespeichert werden.', 'error');
  }
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
  const paypalCode = params.get('code');
  if (paypalCode) {
    localStorage.removeItem('provider_paypal_onboarding_started');
    notify('Diese PayPal-Rückkehr wird aus Sicherheitsgründen nicht unterstützt. Bitte starte die Verbindung erneut.', 'error');
    window.history.replaceState({}, document.title, window.location.pathname);
  }

  const paypalOnboarding = params.get('paypalOnboarding');
  if (!paypalCode && (paypalOnboarding === 'return' || hasPayPalReturnParams(params))) {
    localStorage.removeItem('provider_paypal_onboarding_started');
    const paypalState = params.get('state');
    if (paypalState) {
      completePayPalOnboarding(paypalState);
    } else {
      notify('PayPal-Rückkehr enthält keinen gültigen Sicherheitsnachweis. Bitte starte die Verbindung erneut.', 'error');
    }
    window.history.replaceState({}, document.title, window.location.pathname);
  }

  const stripeStatus = params.get('stripe');
  if (stripeStatus === 'return' || stripeStatus === 'refresh') {
    refreshStripeOnboardingStatus();
    localStorage.removeItem('provider_stripe_onboarding_started');
    window.history.replaceState({}, document.title, window.location.pathname);
  }

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

async function notifyPendingPayPalOnboardingWithoutParams() {
  const params = new URLSearchParams(window.location.search);
  if (params.toString() || localStorage.getItem('provider_paypal_onboarding_started') !== 'true') return;
  notify('PayPal wurde geöffnet, hat aber keine verwertbaren Onboarding-Daten zurückgegeben. Der Status wird jetzt direkt bei PayPal geprüft.', 'info');
  await refreshPayPalOnboardingStatus();
}

async function notifyPendingStripeOnboardingWithoutParams() {
  const params = new URLSearchParams(window.location.search);
  if (params.toString() || localStorage.getItem('provider_stripe_onboarding_started') !== 'true') return;
  notify('Stripe wurde geöffnet. Der Status wird jetzt geprüft.', 'info');
  localStorage.removeItem('provider_stripe_onboarding_started');
  await refreshStripeOnboardingStatus();
}

function hasPayPalReturnParams(params) {
  return ['merchantIdInPayPal', 'merchant_id', 'payerId', 'payer_id', 'permissionsGranted', 'permissions_granted', 'isEmailConfirmed', 'is_email_confirmed']
    .some(key => params.has(key));
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
  grid.innerHTML = skeletonCards(preferredViewMode('services') === 'list' ? 4 : 6, preferredViewMode('services'));
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

  if (preferredViewMode('services') === 'list') {
    grid.innerHTML = allServices.map(renderProviderServiceListCard).join('');
    applyProviderViewMode('services');
    return;
  }

  grid.innerHTML = allServices.map(s => `
    <div class="svc-card list-layout-card">
      ${catImage(s.category, s.imageUrl)}
      <div class="list-card-body">
        <div class="svc-top">
          <span class="cat-badge">${esc(CAT_LABELS[s.category] || s.category)}</span>
          <span class="svc-price">
            €${parseFloat(s.price).toFixed(2)}<small>/Std</small>
            <span class="list-rating-inline">${serviceRatingInline(s)}</span>
          </span>
        </div>
        <div class="svc-title">${esc(s.title)}</div>
        <div class="svc-desc">${esc(s.description)}</div>
        <div class="svc-desc">${serviceMetaLine(s)}</div>
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
    </div>
  `).join('');
  applyProviderViewMode('services');
}

function renderProviderServiceListCard(s) {
  return `
    <article class="sr-list-card">
      ${listMedia(s.category, s.imageUrl)}
      <div class="sr-list-body">
        <div class="sr-list-top">
          <div class="sr-list-main">
            <span class="cat-badge">${esc(CAT_LABELS[s.category] || s.category)}</span>
            <div class="sr-list-title">${esc(s.title)}</div>
            <div class="sr-list-desc">${esc(s.description)}</div>
            <div class="sr-list-meta">${serviceMetaLine(s)}</div>
          </div>
          <div>
            <div class="sr-list-price">€${parseFloat(s.price).toFixed(2)}<small>/Std</small></div>
            <div class="sr-list-rating">${serviceRatingInline(s)}</div>
          </div>
        </div>
        <div class="sr-list-pills">
          <div class="trust-badge"><span>Trust Score</span><strong>${Number(s.trustScore || 0)}</strong></div>
        </div>
        <div class="sr-list-footer">
          <div class="sr-list-meta">Anbieter: <strong>${esc(s.providerName || '–')}</strong> · Status: <strong>${esc(s.status || 'ACTIVE')}</strong></div>
          <div class="sr-list-actions" style="border-top:0;padding-top:0">
            <button class="btn btn-ghost btn-sm" onclick="openEditModal('${s.id}')">✎ Bearbeiten</button>
            <button class="btn btn-danger btn-sm" onclick="openDeleteModal('${s.id}')">🗑 Löschen</button>
          </div>
        </div>
      </div>
    </article>
  `;
}

function listMedia(category, imageUrl = '') {
  if (imageUrl) {
    return `<div class="sr-list-media"><img src="${esc(imageUrl)}" alt="" loading="lazy" decoding="async"></div>`;
  }
  const img = CAT_IMAGES[category] || CAT_IMAGES.OTHER;
  return `<div class="sr-list-media" style="background:${img.bg}">${img.emoji}</div>`;
}

function bookingServiceImageUrl(booking) {
  if (booking?.serviceImageUrl) return booking.serviceImageUrl;
  if (booking?.serviceHasImage && booking?.serviceId) return `${BASE_URL}/services/${encodeURIComponent(booking.serviceId)}/image`;
  return '';
}

function updateStats() {
  loadProviderOverview();
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
  document.getElementById('svcHours').value            = '';
  document.getElementById('svcDeliverable').value      = 'ON_SITE';
  document.getElementById('svcImage').value            = '';
  serviceImageUrls = [];
  renderServiceImagePreview();
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
  document.getElementById('svcHours').value          = s.estimatedHours || '';
  document.getElementById('svcDeliverable').value    = s.deliverableType || 'ON_SITE';
  document.getElementById('svcImage').value          = s.imageUrl || '';
  serviceImageUrls = Array.isArray(s.imageUrls) && s.imageUrls.length ? s.imageUrls : (s.imageUrl ? [s.imageUrl] : []);
  renderServiceImagePreview();
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
  const estimatedHours = parseFloat(document.getElementById('svcHours').value);
  const deliverableType = document.getElementById('svcDeliverable').value;
  const imageUrl = serviceImageUrls[0] || document.getElementById('svcImage').value.trim();

  if (!title || !desc || isNaN(price) || price <= 0) {
    notify('Bitte alle Felder korrekt ausfüllen.', 'error'); return;
  }
  const payload = {
    title,
    description: desc,
    category,
    price,
    estimatedHours: Number.isNaN(estimatedHours) ? null : estimatedHours,
    deliverableType,
    imageUrl,
    imageUrls: serviceImageUrls
  };

  if (editMode) {
    const id = document.getElementById('editServiceId').value;
    try {
      await fetchAPI(`/services/${id}`, 'PUT', payload, 'provider_jwt');
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
      await fetchAPI('/services', 'POST', { ...payload, zipCode }, 'provider_jwt');
      notify('Service erstellt ✓', 'success');
      closeServiceModal();
      loadServices();
    } catch (e) {
      // Backend liefert z.B. "Ungültige Postleitzahl" als 400 -> ehrlich anzeigen
      notify(e.message || 'Fehler beim Erstellen.', 'error');
    }
  }
}

async function handleServiceImageFiles() {
  const input = document.getElementById('svcImages');
  const images = await readImageFiles(input, 10);
  serviceImageUrls = images;
  document.getElementById('svcImage').value = images[0] || '';
  renderServiceImagePreview();
}

function renderServiceImagePreview() {
  const host = document.getElementById('svcImagePreview');
  if (!host) return;
  host.innerHTML = serviceImageUrls.length
    ? serviceImageUrls.map(src => `<img src="${esc(src)}" alt="Servicefoto Vorschau">`).join('')
    : `<span class="muted-text">Noch keine Bilder ausgewählt.</span>`;
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
  document.getElementById('providerChatsView').style.display = tab === 'chats' ? 'block' : 'none';
  document.getElementById('guideView').style.display = tab === 'guide' ? 'block' : 'none';
  document.getElementById('tabMyServices').classList.toggle('active', tab === 'services');
  document.getElementById('tabMyBookings').classList.toggle('active', tab === 'bookings');
  document.getElementById('tabProviderChats').classList.toggle('active', tab === 'chats');
  document.getElementById('tabProviderGuide').classList.toggle('active', tab === 'guide');

  if (tab === 'bookings') {
    providerBookings.length ? renderBookings() : loadBookings();
  } else if (tab === 'services') {
    allServices.length ? renderServices() : loadServices();
  } else if (tab === 'chats') {
    openProviderChatHub();
  }
}

// ── Buchungen laden & anzeigen ────────────────────────────────────────────────
async function loadBookings() {
  const grid = document.getElementById('bookingsGrid');
  grid.innerHTML = skeletonCards(preferredViewMode('bookings') === 'list' ? 4 : 6, preferredViewMode('bookings'));

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

function serviceMetaLine(s) {
  const parts = [];
  if (s.estimatedHours) parts.push(`Geschätzter Aufwand: ca. ${Number(s.estimatedHours).toFixed(1)} Std`);
  if (s.deliverableType === 'DIGITAL') parts.push('digitale Lieferung');
  if (s.deliverableType === 'HYBRID') parts.push('hybrid');
  return parts.length ? parts.map(esc).join(' · ') : 'nach Vereinbarung';
}

// Zeichnet Übersichtskarte + Kalender + Anfragenliste aus dem State providerBookings
function renderBookings() {
  const grid = document.getElementById('bookingsGrid');
  const filteredBookings = filteredProviderBookings();

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

    ${renderBookingFilters()}

    <section class="appointments-list">
      <div class="section-row-title">
        <div>
          <span>Alle Anfragen & Termine</span>
          <small>${filteredBookings.length} von ${providerBookings.length} Aufträgen angezeigt.</small>
        </div>
      </div>
      ${filteredBookings.length ? filteredBookings.map(b => preferredViewMode('bookings') === 'list' ? renderProviderBookingListCard(b) : renderProviderBookingCard(b)).join('') : `
        <div class="appointment-empty-card">
          <div class="empty-icon">📭</div>
          <p>Keine Aufträge passen zu den aktuellen Filtern.</p>
        </div>
      `}
    </section>
  `;
  applyProviderViewMode('bookings');
}

function renderProviderBookingListCard(b) {
  const statusClass = statusToClass(b.status);
  const review = b.review;
  return `
    <article class="sr-list-card ${statusClass}" onclick="openBookingDrawer('${b.id}')">
      ${listMedia(b.serviceCategory || 'OTHER', bookingServiceImageUrl(b))}
      <div class="sr-list-body">
        <div class="sr-list-top">
          <div class="sr-list-main">
            <span class="cat-badge status-badge ${statusClass}">${esc(b.status)}</span>
            <div class="sr-list-title">${esc(b.serviceTitle)}</div>
            <div class="provider-row">
              ${avatarHtml(b.customerName, b.customerProfileImageUrl)}
              <span class="provider-name">Angefragt von: <strong>${esc(b.customerName)}</strong></span>
            </div>
          </div>
          <div class="sr-list-meta">${formatDateShort(b.bookingDate)}</div>
        </div>
        <div class="sr-list-split">
          <div class="sr-mini-panel">
            <div class="payment-badges">
              ${providerPaymentPill(b.paymentProvider, b.paymentProvider ? '' : 'Noch nicht gewählt')}
              <span class="payment-pill ${b.paymentStatus === 'PAID' ? 'bank' : 'muted'}">${esc(b.paymentStatus || 'UNPAID')}</span>
            </div>
            <p>${b.actualHours ? `Aufwand: ${Number(b.actualHours).toFixed(2)} Std` : 'Zahlungsstatus und Aufwand auf einen Blick.'}</p>
          </div>
          <div class="sr-mini-panel">
            <div class="sr-mini-panel-head">
              <span>${review ? 'Kundenbewertung' : 'Kundenbewertung'}</span>
              <span class="booking-review-stars">${review ? starString(review.rating || 0) : '☆☆☆☆☆'}</span>
            </div>
            <p>${review?.comment ? esc(review.comment) : 'Bewertungen erscheinen hier nach abgeschlossenen Buchungen.'}</p>
          </div>
        </div>
        ${b.deliveryUrl ? `<div class="sr-list-meta">Lieferung bereitgestellt${b.deliveryExpiresAt ? ` · bis ${formatDateTimeShort(b.deliveryExpiresAt)}` : ''}</div>` : ''}
        ${b.status === 'PENDING' ? `
          <div class="sr-list-actions" onclick="event.stopPropagation()">
            <button class="btn btn-primary btn-sm" onclick="updateBookingStatus('${b.id}', 'ACCEPTED')">Akzeptieren</button>
            <button class="btn btn-danger btn-sm" onclick="updateBookingStatus('${b.id}', 'REJECTED')">Ablehnen</button>
            <button class="btn btn-ghost btn-sm" onclick="openBookingDrawer('${b.id}')">Details</button>
          </div>
        ` : b.status === 'ACCEPTED' ? `
          <div class="sr-list-actions" onclick="event.stopPropagation()">
            <button class="btn btn-primary btn-sm" onclick="updateBookingStatus('${b.id}', 'COMPLETED')">✓ Abschließen</button>
            <button class="btn btn-ghost btn-sm" onclick="openBookingDrawer('${b.id}')">Details</button>
          </div>
        ` : `
          <div class="sr-list-actions" onclick="event.stopPropagation()">
            <span class="muted-text">Buchung ist abgeschlossen/abgelehnt.</span>
            <button class="btn btn-ghost btn-sm" onclick="openBookingDrawer('${b.id}')">Details</button>
          </div>
        `}
      </div>
    </article>
  `;
}

function renderBookingFilters() {
  const q = esc(document.getElementById('bookingSearchFilter')?.value || '');
  const status = document.getElementById('bookingStatusFilter')?.value || '';
  const payment = document.getElementById('bookingPaymentFilter')?.value || '';
  const from = document.getElementById('bookingFromFilter')?.value || '';
  const to = document.getElementById('bookingToFilter')?.value || '';
  return `
    <div class="booking-filters">
      <div class="booking-filters-head">
        <div>
          <strong>Aufträge filtern</strong>
          <span>Suche nach Auftrag, Kunde, Zahlungsstatus oder Zeitraum.</span>
        </div>
      </div>
      <input class="form-input" id="bookingSearchFilter" placeholder="Auftrag, Kunde oder Notiz suchen" value="${q}" oninput="renderBookings()" />
      <select class="form-input" id="bookingStatusFilter" onchange="renderBookings()">
        <option value="" ${status === '' ? 'selected' : ''}>Alle Status</option>
        <option value="PENDING" ${status === 'PENDING' ? 'selected' : ''}>Offen</option>
        <option value="ACCEPTED" ${status === 'ACCEPTED' ? 'selected' : ''}>Akzeptiert</option>
        <option value="COMPLETED" ${status === 'COMPLETED' ? 'selected' : ''}>Abgeschlossen</option>
        <option value="REJECTED" ${status === 'REJECTED' ? 'selected' : ''}>Abgelehnt</option>
      </select>
      <select class="form-input" id="bookingPaymentFilter" onchange="renderBookings()">
        <option value="" ${payment === '' ? 'selected' : ''}>Alle Zahlungen</option>
        <option value="UNPAID" ${payment === 'UNPAID' ? 'selected' : ''}>Unbezahlt</option>
        <option value="CHECKOUT_CREATED" ${payment === 'CHECKOUT_CREATED' ? 'selected' : ''}>Checkout gestartet</option>
        <option value="AWAITING_OFFLINE_PAYMENT" ${payment === 'AWAITING_OFFLINE_PAYMENT' ? 'selected' : ''}>Offline vorgemerkt</option>
        <option value="PAID" ${payment === 'PAID' ? 'selected' : ''}>Bezahlt</option>
      </select>
      <label class="filter-field">
        <span>Von</span>
        <input class="form-input" type="date" id="bookingFromFilter" value="${esc(from)}" onchange="renderBookings()" />
      </label>
      <label class="filter-field">
        <span>Bis</span>
        <input class="form-input" type="date" id="bookingToFilter" value="${esc(to)}" onchange="renderBookings()" />
      </label>
    </div>
  `;
}

function filteredProviderBookings() {
  const q = (document.getElementById('bookingSearchFilter')?.value || '').toLowerCase().trim();
  const status = document.getElementById('bookingStatusFilter')?.value || '';
  const payment = document.getElementById('bookingPaymentFilter')?.value || '';
  const from = document.getElementById('bookingFromFilter')?.value || '';
  const to = document.getElementById('bookingToFilter')?.value || '';

  return providerBookings.filter(booking => {
    const text = [
      booking.serviceTitle,
      booking.customerName,
      booking.providerNotes,
      booking.customerNotes,
      booking.paymentProvider,
      booking.paymentStatus
    ].join(' ').toLowerCase();
    const date = booking.bookingDate || '';
    return (!q || text.includes(q))
      && (!status || booking.status === status)
      && (!payment || booking.paymentStatus === payment)
      && (!from || date >= from)
      && (!to || date <= to);
  });
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
    <div class="svc-card appointment-card list-layout-card ${statusClass}" onclick="openBookingDrawer('${b.id}')">
      ${catImage(b.serviceCategory || 'OTHER', bookingServiceImageUrl(b))}
      <div class="list-card-body">
        <div class="svc-top">
          <span class="cat-badge status-badge ${statusClass}">${esc(b.status)}</span>
          <span class="appointment-date">${formatDateShort(b.bookingDate)}</span>
        </div>
        <div class="svc-title">${esc(b.serviceTitle)}</div>
        <div class="svc-desc booking-person-line" style="display:flex;align-items:center;gap:.5rem">
          ${avatarHtml(b.customerName, b.customerProfileImageUrl)}
          <span>Angefragt von: <strong>${esc(b.customerName)}</strong></span>
        </div>
        <div class="svc-desc booking-payment-line">
          Zahlung: ${providerPaymentPill(b.paymentProvider, b.paymentProvider ? '' : 'Noch nicht gewählt')}
          <span class="payment-pill ${b.paymentStatus === 'PAID' ? 'bank' : 'muted'}">${esc(b.paymentStatus || 'UNPAID')}</span>
          ${b.actualHours ? ` · Aufwand: <strong>${Number(b.actualHours).toFixed(2)} Std</strong>` : ''}
        </div>
        ${review ? renderProviderReview(review) : renderProviderReviewEmpty(b.status)}
        ${b.deliveryUrl ? `<div class="svc-desc">Lieferung bereitgestellt${b.deliveryExpiresAt ? ` · bis ${formatDateTimeShort(b.deliveryExpiresAt)}` : ''}</div>` : ''}

        ${b.status === 'PENDING' ? `
          <div class="svc-footer" onclick="event.stopPropagation()">
            <button class="btn btn-primary btn-sm" onclick="updateBookingStatus('${b.id}', 'ACCEPTED')">Akzeptieren</button>
            <button class="btn btn-danger btn-sm" onclick="updateBookingStatus('${b.id}', 'REJECTED')">Ablehnen</button>
            <button class="btn btn-ghost btn-sm" onclick="openBookingDrawer('${b.id}')">Details</button>
          </div>
        ` : b.status === 'ACCEPTED' ? `
          <div class="svc-footer" onclick="event.stopPropagation()">
            <button class="btn btn-primary btn-sm" onclick="updateBookingStatus('${b.id}', 'COMPLETED')">✓ Abschließen</button>
            <button class="btn btn-ghost btn-sm" onclick="openBookingDrawer('${b.id}')">Details</button>
          </div>
        ` : `
          <div class="svc-footer" onclick="event.stopPropagation()">
            <span class="muted-text">Buchung ist abgeschlossen/abgelehnt.</span>
            <button class="btn btn-ghost btn-sm" onclick="openBookingDrawer('${b.id}')">Details</button>
          </div>
        `}
      </div>
    </div>
  `;
}

async function saveWorkLog(bookingId) {
  const actualHours = parseFloat(document.getElementById('drawerTotalHours').value);
  const providerNotes = document.getElementById('drawerProviderNotes').value.trim();
  try {
    const updated = await fetchAPI(`/bookings/${bookingId}/work`, 'PUT', {
      actualHours: Number.isNaN(actualHours) ? null : actualHours,
      providerNotes
    }, 'provider_jwt');
    notify('Aufwand gespeichert.', 'success');
    replaceProviderBooking(updated);
    activeBookingId = bookingId;
    renderBookingDrawer();
    loadProviderOverview();
  } catch (e) {
    notify(e.message || 'Aufwand konnte nicht gespeichert werden.', 'error');
  }
}

async function publishDelivery(bookingId) {
  const deliveryUrl = document.getElementById('drawerDeliveryUrl').value.trim();
  const deliveryLabel = document.getElementById('drawerDeliveryLabel').value.trim();
  const expiresInHours = parseInt(document.getElementById('drawerDeliveryHours').value || '72', 10);
  try {
    await fetchAPI(`/bookings/${bookingId}/delivery`, 'POST', { deliveryUrl, deliveryLabel, expiresInHours }, 'provider_jwt');
    notify('Lieferung bereitgestellt.', 'success');
    await loadBookings();
    activeBookingId = bookingId;
    renderBookingDrawer();
  } catch (e) {
    notify(e.message || 'Lieferung konnte nicht bereitgestellt werden.', 'error');
  }
}

function openBookingDrawer(bookingId) {
  activeBookingId = bookingId;
  renderBookingDrawer();
  document.getElementById('bookingDrawer').classList.add('open');
  loadDrawerChatMessages();
}

function closeBookingDrawer() {
  activeBookingId = null;
  document.getElementById('bookingDrawer').classList.remove('open');
}

function renderBookingDrawer() {
  const b = providerBookings.find(x => x.id === activeBookingId);
  if (!b) return;
  document.getElementById('bookingDrawerTitle').textContent = b.serviceTitle || 'Auftrag';
  const revenue = Number(b.actualHours || 1) * Number(b.servicePrice || 0);
  document.getElementById('bookingDrawerContent').innerHTML = `
    <div class="drawer-grid">
      <div class="drawer-section">
        <h3>Eckdaten</h3>
        <div style="display:flex;align-items:center;gap:.7rem;margin-bottom:.7rem">
          ${avatarHtml(b.customerName, b.customerProfileImageUrl, 'profile-large')}
          <div>
            <p><strong>Kunde:</strong> ${esc(b.customerName || '-')}</p>
            <p class="muted-text">Auftrag ${esc(b.id || '')}</p>
          </div>
        </div>
        <p><strong>Termin:</strong> ${esc(b.bookingDate || '-')}</p>
        <p><strong>Status:</strong> ${esc(b.status || '-')}</p>
        <p><strong>Stundensatz:</strong> €${Number(b.servicePrice || 0).toFixed(2)}</p>
      </div>
      <div class="drawer-section">
        <h3>Payment</h3>
        <p>
          ${providerPaymentPill(b.paymentProvider, b.paymentProvider ? '' : 'Noch nicht gewählt')}
          <span class="payment-pill ${b.paymentStatus === 'PAID' ? 'is-paid' : 'muted'}">${esc(b.paymentStatus || 'UNPAID')}</span>
        </p>
        <p><strong>Bezahlt am:</strong> ${b.paidAt ? formatDateTimeShort(b.paidAt) : '-'}</p>
        <p><strong>Zahlungsart:</strong> ${esc(providerPaymentLabel(b.paymentProvider))}</p>
        ${b.paymentNote ? `<p><strong>Notiz:</strong> ${esc(b.paymentNote)}</p>` : ''}
        <p><strong>Umsatz:</strong> €${revenue.toFixed(2)}</p>
        <p><strong>Brutto:</strong> €${Number(b.grossAmount || revenue).toFixed(2)}</p>
        <p><strong>Plattformprovision:</strong> €${Number(b.platformFeeAmount || 0).toFixed(2)}</p>
        <p><strong>Provider-Netto:</strong> €${Number(b.providerReceivableAmount || revenue).toFixed(2)}</p>
        <p><strong>Abrechnung:</strong> ${esc(settlementLabel(b.settlementStatus || 'NOT_READY'))}</p>
        ${b.settlementNote ? `<p class="muted-text">${esc(b.settlementNote)}</p>` : ''}
        ${b.paymentStatus === 'PAID' ? `<button class="btn btn-ghost btn-sm" style="margin-top:.6rem" onclick="printProviderInvoice('${b.id}')">Rechnung drucken</button>` : ''}
        ${b.paymentStatus !== 'PAID' ? `
          <select class="form-input" id="drawerPaymentMethod" style="margin-top:.7rem">
            <option value="CASH">Barzahlung</option>
            <option value="BANK_TRANSFER">Banküberweisung</option>
            <option value="MANUAL">Manuell geprüft</option>
            <option value="PAYPAL">PayPal</option>
            <option value="CARD">Kredit-/Debitkarte</option>
            <option value="SEPA">SEPA</option>
          </select>
          <input class="form-input" id="drawerPaymentNote" style="margin-top:.6rem" placeholder="Zahlungsnotiz, z.B. vor Ort bar erhalten" />
          <button class="btn btn-primary btn-sm" style="margin-top:.6rem" onclick="recordProviderPayment('${b.id}')">Zahlung verbuchen</button>
        ` : ''}
      </div>
    </div>

    <section class="drawer-section">
      <h3>Zeitaufzeichnung</h3>
      <div class="drawer-grid">
        <input class="form-input" type="date" id="drawerWorkDate" value="${todayISO()}" />
        <input class="form-input" type="number" id="drawerEntryHours" min="0.25" step="0.25" placeholder="Stunden" />
      </div>
      <input class="form-input" style="margin-top:.6rem" id="drawerEntryNote" placeholder="Tätigkeit / Notiz" />
      <button class="btn btn-primary btn-sm" style="margin-top:.6rem" onclick="addTimeEntry('${b.id}')">Zeitbuchung hinzufügen</button>
      <button class="btn btn-ghost btn-sm" style="margin-top:.6rem" onclick="exportTimeEntriesPdf('${b.id}')">PDF exportieren</button>
      <div style="margin-top:.8rem">
        ${(b.timeEntries || []).length ? b.timeEntries.map(entry => `
          <div class="time-entry-row">
            <strong>${esc(entry.workDate || '-')}</strong>
            <span>${Number(entry.hours || 0).toFixed(2)}h</span>
            <span>${esc(entry.note || '')}</span>
          </div>
        `).join('') : `<p class="muted-text">Noch keine Zeitbuchungen.</p>`}
      </div>
      <div class="drawer-grid" style="margin-top:.8rem">
        <input class="form-input" type="number" id="drawerTotalHours" min="0" step="0.25" value="${b.actualHours || ''}" placeholder="Gesamtstunden" />
        <input class="form-input" id="drawerProviderNotes" value="${esc(b.providerNotes || '')}" placeholder="Interne Notiz" />
      </div>
      <button class="btn btn-ghost btn-sm" style="margin-top:.6rem" onclick="saveWorkLog('${b.id}')">Gesamtaufwand speichern</button>
    </section>

    <section class="drawer-section">
      <h3>Digitale Lieferung</h3>
      <p class="muted-text">Der echte Link wird Kunden erst nach Zahlung über einen geschützten Backend-Link freigegeben.</p>
      <input class="form-input" type="url" id="drawerDeliveryUrl" placeholder="Privater Download-Link" />
      <div class="drawer-grid" style="margin-top:.6rem">
        <input class="form-input" id="drawerDeliveryLabel" value="${esc(b.deliveryLabel || '')}" placeholder="Label, z.B. Finale Dateien" />
        <input class="form-input" type="number" id="drawerDeliveryHours" min="1" max="336" value="72" />
      </div>
      <button class="btn btn-primary btn-sm" style="margin-top:.6rem" onclick="publishDelivery('${b.id}')">Lieferung bereitstellen</button>
      ${b.deliveryUrl ? `<p style="margin-top:.7rem"><strong>Status:</strong> Bereitgestellt${b.deliveryExpiresAt ? ` bis ${formatDateTimeShort(b.deliveryExpiresAt)}` : ''}</p>` : ''}
    </section>

    <section class="drawer-section">
      <h3>Kommunikation</h3>
      <div class="chat-thread" id="drawerChatThread"></div>
      <div class="chat-compose">
        <textarea class="form-input" id="drawerChatInput" rows="2" maxlength="1000" placeholder="Nachricht schreiben..."></textarea>
        <label class="chat-attach-btn" title="Bild senden">
          <span class="chat-attach-text">Bild</span>
          <input type="file" id="drawerChatImage" accept="image/*" onchange="updateChatImageLabel('drawerChatImage')" />
        </label>
        <button class="btn btn-primary btn-sm" onclick="sendDrawerChatMessage()">Senden</button>
      </div>
    </section>
  `;
}

function settlementLabel(status) {
  const labels = {
    NOT_READY: 'Noch nicht abrechnungsbereit',
    PAYPAL_PLATFORM_FEE_PENDING: 'PayPal Split vorbereitet',
    PAYPAL_SPLIT_COMPLETED: 'PayPal Split abgeschlossen',
    STRIPE_DESTINATION_CHARGE_PENDING: 'Stripe Connect vorbereitet',
    STRIPE_DESTINATION_CHARGE_COMPLETED: 'Stripe Connect abgeschlossen',
    PLATFORM_COLLECTED_PENDING_PROVIDER_PAYOUT: 'Provider-Auszahlung offen',
    PROVIDER_PAYOUT_SENT: 'Provider-Auszahlung gesendet',
    PLATFORM_FEE_DUE_FROM_PROVIDER: 'Provision vom Provider offen',
    PLATFORM_FEE_SETTLED: 'Provision beglichen',
    DISPUTED: 'In Klärung'
  };
  return labels[status] || status;
}

function providerPaymentLabel(provider) {
  const labels = {
    PAYPAL: 'PayPal',
    CARD: 'Stripe Karte',
    STRIPE: 'Stripe Karte',
    BANK_TRANSFER: 'Banküberweisung',
    CASH: 'Barzahlung',
    MANUAL: 'Manuell geprüft',
    SEPA: 'SEPA'
  };
  return labels[String(provider || '').toUpperCase()] || provider || '-';
}

function providerPaymentPill(provider, fallback = '') {
  const normalized = String(provider || '').toUpperCase();
  const classes = {
    PAYPAL: 'paypal',
    CARD: 'stripe',
    STRIPE: 'stripe',
    BANK_TRANSFER: 'bank',
    CASH: 'cash',
    MANUAL: 'muted',
    SEPA: 'bank'
  };
  return `<span class="payment-pill ${classes[normalized] || 'muted'}">${esc(fallback || providerPaymentLabel(normalized))}</span>`;
}

async function recordProviderPayment(bookingId) {
  const provider = document.getElementById('drawerPaymentMethod').value;
  const note = document.getElementById('drawerPaymentNote').value.trim();
  try {
    await fetchAPI(`/bookings/${bookingId}/record-payment`, 'POST', { provider, note }, 'provider_jwt');
    notify('Zahlung verbucht.', 'success');
    await loadBookings();
    activeBookingId = bookingId;
    renderBookingDrawer();
    loadDrawerChatMessages();
    loadProviderOverview();
  } catch (e) {
    notify(e.message || 'Zahlung konnte nicht verbucht werden.', 'error');
  }
}

async function loadDrawerChatMessages() {
  const thread = document.getElementById('drawerChatThread');
  if (!thread || !activeBookingId) return;
  thread.innerHTML = `<div class="muted-text">Nachrichten werden geladen...</div>`;
  try {
    const messages = await fetchAPI(`/messages/booking/${activeBookingId}`, 'GET', null, 'provider_jwt');
    thread.innerHTML = messages.length ? messages.map(renderChatMessage).join('') : `<div class="muted-text">Noch keine Nachrichten.</div>`;
    thread.scrollTop = thread.scrollHeight;
  } catch {
    thread.innerHTML = `<div class="muted-text">Nachrichten konnten nicht geladen werden.</div>`;
  }
}

async function sendDrawerChatMessage() {
  const payload = await buildChatPayload('drawerChatInput', 'drawerChatImage');
  if ((!hasMeaningfulChatContent(payload.content) && !payload.imageDataUrl) || !activeBookingId) {
    notify('Bitte schreibe eine Nachricht oder wähle ein Bild aus.', 'info');
    return;
  }
  try {
    const message = await fetchAPI(`/messages/booking/${activeBookingId}`, 'POST', payload, 'provider_jwt');
    resetChatInputs('drawerChatInput', 'drawerChatImage');
    const thread = document.getElementById('drawerChatThread');
    if (thread) {
      upsertChatMessage(thread, withLocalChatImage(message, payload), renderChatMessage, '.muted-text');
    } else {
      await loadDrawerChatMessages();
    }
  } catch (e) {
    notify(e.message || 'Nachricht konnte nicht gesendet werden.', 'error');
  }
}

async function addTimeEntry(bookingId) {
  const workDate = document.getElementById('drawerWorkDate').value;
  const hours = parseFloat(document.getElementById('drawerEntryHours').value);
  const note = document.getElementById('drawerEntryNote').value.trim();
  if (!workDate || Number.isNaN(hours) || hours <= 0) {
    notify('Bitte Datum und positive Stunden angeben.', 'error');
    return;
  }
  try {
    const updated = await fetchAPI(`/bookings/${bookingId}/time-entries`, 'POST', { workDate, hours, note }, 'provider_jwt');
    notify('Zeitbuchung hinzugefügt.', 'success');
    replaceProviderBooking(updated);
    activeBookingId = bookingId;
    renderBookingDrawer();
    loadProviderOverview();
  } catch (e) {
    notify(e.message || 'Zeitbuchung konnte nicht gespeichert werden.', 'error');
  }
}

function replaceProviderBooking(updated) {
  if (!updated?.id) return;
  providerBookings = providerBookings.map(booking => booking.id === updated.id ? updated : booking);
  renderBookings();
}

function exportTimeEntriesPdf(bookingId) {
  const booking = providerBookings.find(item => item.id === bookingId);
  if (!booking) return;
  const rows = (booking.timeEntries || []).map(entry => `
    <tr>
      <td>${esc(entry.workDate || '-')}</td>
      <td>${Number(entry.hours || 0).toFixed(2)}h</td>
      <td>${esc(entry.note || '')}</td>
    </tr>
  `).join('') || `<tr><td colspan="3">Keine Zeitbuchungen vorhanden.</td></tr>`;
  const win = window.open('', '_blank');
  win.document.write(`
    <html>
    <head>
      <title>Zeitaufzeichnung ${esc(booking.serviceTitle || '')}</title>
      <style>
        body{font-family:Arial,sans-serif;color:#0f172a;margin:32px}
        header{border-bottom:4px solid #00877C;padding-bottom:16px;margin-bottom:24px}
        h1{margin:0;color:#00877C}
        table{width:100%;border-collapse:collapse;margin-top:20px}
        th,td{padding:12px;border-bottom:1px solid #dbe3e7;text-align:left}
        th{background:#e0f2f1;color:#00685f}
        .total{margin-top:20px;font-size:18px;font-weight:700}
      </style>
    </head>
    <body>
      <header><h1>ServiceRate Zeitaufzeichnung</h1><p>${esc(booking.serviceTitle || '-')} · ${esc(booking.customerName || '-')}</p></header>
      <p><strong>Termin:</strong> ${esc(booking.bookingDate || '-')}</p>
      <table><thead><tr><th>Datum</th><th>Stunden</th><th>Tätigkeit</th></tr></thead><tbody>${rows}</tbody></table>
      <p class="total">Gesamtaufwand: ${Number(booking.actualHours || 0).toFixed(2)}h</p>
      <script>window.print();</script>
    </body>
    </html>
  `);
  win.document.close();
}

function printProviderInvoice(bookingId) {
  const booking = providerBookings.find(item => item.id === bookingId);
  if (!booking) return;
  const revenue = Number(booking.grossAmount || (Number(booking.actualHours || 1) * Number(booking.servicePrice || 0)) || 0);
  const win = window.open('', '_blank');
  if (!win) {
    notify('Popup blockiert. Bitte Popups erlauben, um die Rechnung zu öffnen.', 'error');
    return;
  }
  win.document.write(`
    <html>
    <head>
      <title>Provider Rechnung ${esc(booking.id || '')}</title>
      <style>
        body{font-family:Arial,sans-serif;color:#0f172a;margin:36px;background:#f5f8f8}
        .invoice{background:white;border:1px solid #dbe3e7;border-top:6px solid #00877C;border-radius:18px;padding:32px;max-width:820px;margin:auto}
        header{display:flex;justify-content:space-between;gap:24px;border-bottom:1px solid #dbe3e7;padding-bottom:20px;margin-bottom:24px}
        h1{margin:0;color:#00877C;font-size:28px} h2{margin:0 0 8px;font-size:18px}
        .logo{font-weight:900;font-size:24px}.logo span{color:#00877C}
        .grid{display:grid;grid-template-columns:1fr 1fr;gap:18px;margin:20px 0}
        .box{border:1px solid #dbe3e7;border-radius:14px;padding:16px;background:#f8fbfb}
        table{width:100%;border-collapse:collapse;margin-top:18px} th,td{padding:12px;border-bottom:1px solid #dbe3e7;text-align:left}
        th{background:#e0f2f1;color:#00685f}.total{text-align:right;font-size:22px;font-weight:900;color:#00877C;margin-top:20px}
        @media print{body{background:white;margin:0}.invoice{border-radius:0;border-left:0;border-right:0}}
      </style>
    </head>
    <body>
      <main class="invoice">
        <header>
          <div><div class="logo">Service<span>Rate</span></div><p>${esc(booking.serviceTitle || '-')}</p></div>
          <div><h1>Provider Rechnung</h1><p>Rechnungsdatum: ${new Date().toLocaleDateString('de-AT')}</p></div>
        </header>
        <section class="grid">
          <div class="box"><h2>Kunde</h2><p>${esc(booking.customerName || '-')}</p></div>
          <div class="box"><h2>Auftrag</h2><p>${esc(booking.id || '-')}<br>Termin: ${esc(booking.bookingDate || '-')}</p></div>
        </section>
        <table>
          <thead><tr><th>Leistung</th><th>Zahlungsart</th><th>Aufwand</th><th>Betrag</th></tr></thead>
          <tbody><tr><td>${esc(booking.serviceTitle || '-')}</td><td>${esc(providerPaymentLabel(booking.paymentProvider))}</td><td>${Number(booking.actualHours || 0).toFixed(2)}h</td><td>€${revenue.toFixed(2)}</td></tr></tbody>
        </table>
        <p>Plattformprovision: €${Number(booking.platformFeeAmount || 0).toFixed(2)}</p>
        <p>Provider-Netto: €${Number(booking.providerReceivableAmount || revenue).toFixed(2)}</p>
        <p class="total">Brutto: €${revenue.toFixed(2)}</p>
      </main>
      <script>window.print();</script>
    </body>
    </html>
  `);
  win.document.close();
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

function serviceRatingInline(s) {
  const count = s.reviewCount || 0;
  const avg = s.averageRating || 0;
  if (!count) return `<span class="stars">☆☆☆☆☆</span> <span>Noch keine Bewertungen</span>`;
  return `<span class="stars">${starString(avg)}</span> <span>${avg.toFixed(1)} (${count})</span>`;
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
function formatDateTimeShort(value) {
  if (!value) return '-';
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return '-';
  return d.toLocaleString('de-AT', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' });
}
function todayISO() {
  const d = new Date();
  d.setMinutes(d.getMinutes() - d.getTimezoneOffset());
  return d.toISOString().split('T')[0];
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
let activeProviderHubBookingId = null;
let providerChatEventSource = null;

async function openProviderChatHub() {
  if (document.getElementById('providerChatsView')?.style.display !== 'block') {
    switchDashboardTab('chats');
    return;
  }
  document.getElementById('providerChatList').innerHTML = chatListSkeleton();
  document.getElementById('providerChatHubThread').innerHTML = chatThreadSkeleton();
  await ensureProviderBookingsForChat();
  renderProviderChatList();
  if (!activeProviderHubBookingId && providerBookings.length) {
    await selectProviderHubChat(providerBookings[0].id);
  } else if (activeProviderHubBookingId) {
    await selectProviderHubChat(activeProviderHubBookingId);
  }
}

function closeProviderChatHub() {
  closeProviderChatStream();
  switchDashboardTab('bookings');
}

async function ensureProviderBookingsForChat() {
  if (providerBookings.length) return;
  try {
    const bookings = await fetchAPI('/bookings/provider/me', 'GET', null, 'provider_jwt');
    providerBookings = Array.isArray(bookings) ? bookings : [];
  } catch (e) {
    notify(e.message || 'Chats konnten nicht geladen werden.', 'error');
  }
}

function renderProviderChatList() {
  const list = document.getElementById('providerChatList');
  if (!list) return;
  list.innerHTML = providerBookings.length ? providerBookings.map(booking => `
    <button class="chat-list-item ${booking.id === activeProviderHubBookingId ? 'active' : ''}" onclick="selectProviderHubChat('${booking.id}')">
      ${avatarHtml(booking.customerName, booking.customerProfileImageUrl)}
      <span class="chat-list-copy">
        <strong>${esc(booking.serviceTitle || 'Service')}</strong>
        <span>${esc(booking.customerName || 'Kunde')} · ${esc(booking.bookingDate || 'kein Termin')}</span>
      </span>
    </button>
  `).join('') : `<div class="muted-text">Noch keine Chats vorhanden.</div>`;
}

async function selectProviderHubChat(bookingId) {
  closeProviderChatStream();
  activeProviderHubBookingId = bookingId;
  const booking = providerBookings.find(item => item.id === bookingId);
  document.getElementById('providerChatTitle').innerHTML = booking
    ? `${avatarHtml(booking.customerName, booking.customerProfileImageUrl)}<span><strong>${esc(booking.customerName || 'Kunde')}</strong><small>${esc(booking.serviceTitle || 'Service')}</small></span>`
    : 'Chat';
  renderProviderChatList();
  await loadProviderHubMessages();
  openProviderChatStream();
}

async function loadProviderHubMessages() {
  const thread = document.getElementById('providerChatHubThread');
  if (!thread || !activeProviderHubBookingId) return;
  thread.innerHTML = chatThreadSkeleton();
  try {
    const messages = await fetchAPI(`/messages/booking/${activeProviderHubBookingId}`, 'GET', null, 'provider_jwt');
    thread.innerHTML = messages.length ? messages.map(renderChatMessage).join('') : `<div class="muted-text">Noch keine Nachrichten.</div>`;
    thread.scrollTop = thread.scrollHeight;
  } catch {
    thread.innerHTML = `<div class="muted-text">Nachrichten konnten nicht geladen werden.</div>`;
  }
}

function openProviderChatStream() {
  const token = localStorage.getItem('provider_jwt');
  if (!token || !activeProviderHubBookingId || typeof EventSource === 'undefined') return;
  providerChatEventSource = new EventSource(`${BASE_URL}/messages/booking/${encodeURIComponent(activeProviderHubBookingId)}/stream?token=${encodeURIComponent(token)}`);
  providerChatEventSource.addEventListener('message', event => {
    appendProviderHubMessage(JSON.parse(event.data));
  });
  providerChatEventSource.onerror = () => {
    closeProviderChatStream();
  };
}

function closeProviderChatStream() {
  if (providerChatEventSource) {
    providerChatEventSource.close();
    providerChatEventSource = null;
  }
}

function appendProviderHubMessage(message) {
  const thread = document.getElementById('providerChatHubThread');
  upsertChatMessage(thread, message, renderChatMessage, '.muted-text');
}

async function sendProviderHubMessage() {
  const payload = await buildChatPayload('providerChatHubInput', 'providerChatHubImage');
  if ((!hasMeaningfulChatContent(payload.content) && !payload.imageDataUrl) || !activeProviderHubBookingId) {
    notify('Bitte schreibe eine Nachricht oder wähle ein Bild aus.', 'info');
    return;
  }
  try {
    const message = await fetchAPI(`/messages/booking/${activeProviderHubBookingId}`, 'POST', payload, 'provider_jwt');
    resetChatInputs('providerChatHubInput', 'providerChatHubImage');
    appendProviderHubMessage(withLocalChatImage(message, payload));
  } catch (e) {
    notify(e.message || 'Nachricht konnte nicht gesendet werden.', 'error');
  }
}

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
    <div class="chat-message ${message.senderRole === 'PROVIDER' ? 'from-provider' : 'from-customer'}" id="chat-message-${esc(message.id || '')}">
      <strong>${esc(message.senderName || 'User')}</strong>
      ${message.content ? `<p>${esc(message.content || '')}</p>` : ''}
      ${message.imageDataUrl ? `<img class="chat-image" src="${esc(message.imageDataUrl)}" alt="${esc(message.imageName || 'Chat-Bild')}" loading="lazy" decoding="async">` : ''}
    </div>
  `;
}

async function sendChatMessage(tokenKey) {
  const payload = await buildChatPayload('chatInput', 'chatImageInput');
  if ((!hasMeaningfulChatContent(payload.content) && !payload.imageDataUrl) || !activeChatBookingId) {
    notify('Bitte schreibe eine Nachricht oder wähle ein Bild aus.', 'info');
    return;
  }
  try {
    const message = await fetchAPI(`/messages/booking/${activeChatBookingId}`, 'POST', payload, tokenKey);
    resetChatInputs('chatInput', 'chatImageInput');
    const thread = document.getElementById('chatThread');
    if (thread) {
      upsertChatMessage(thread, withLocalChatImage(message, payload), renderChatMessage, '.muted-text');
    } else {
      await loadChatMessages(tokenKey);
    }
  } catch (e) {
    notify(e.message || 'Nachricht konnte nicht gesendet werden.', 'error');
  }
}
