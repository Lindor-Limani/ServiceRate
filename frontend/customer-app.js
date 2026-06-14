// ── State ─────────────────────────────────────────────────────────────────────
let allServices    = [];
let activeCategory = '';

const CAT_LABELS = {
    CLEANING: 'Reinigung', PLUMBING: 'Installateur',
    ELECTRICAL: 'Elektriker', PAINTING: 'Maler',
    GARDENING: 'Garten', OTHER: 'Sonstiges'
};

// ── Init ──────────────────────────────────────────────────────────────────────
(function init() {
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

// ── Weather Widget ────────────────────────────────────────────────────────────
async function loadWeather() {
    const API_KEY = '6d06aea9543246a5433f298cb611335e';
    try {
        const r = await fetch(
            `https://api.openweathermap.org/data/2.5/weather?q=Vienna&appid=${API_KEY}&units=metric&lang=de`
        );
        const d = await r.json();
        if (!r.ok) throw new Error();
        document.getElementById('weatherIcon').innerHTML =
            `<img src="https://openweathermap.org/img/wn/${d.weather[0].icon}.png" width="26" height="26" alt="" />`;
        document.getElementById('weatherText').textContent =
            `${Math.round(d.main.temp)}°C · ${d.weather[0].description} · Wien`;
    } catch {
        document.getElementById('weatherText').textContent = 'Wetter nicht verfügbar';
    }
}

// ── Services ──────────────────────────────────────────────────────────────────
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
        <p>Services konnten nicht geladen werden.<br/>Läuft das Backend auf Port 8080?</p>
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

    const filtered = allServices.filter(s => {
        const matchText = !q || (s.title + ' ' + s.description).toLowerCase().includes(q);
        const matchCat  = !cat || s.category === cat;
        return matchText && matchCat;
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
    <article class="service-card" onclick="openServiceModal('${s.id}')">
      <div class="card-top">
        <span class="cat-badge">${CAT_LABELS[s.category] || s.category}</span>
        <span class="card-price">€${parseFloat(s.price).toFixed(2)}<small>/Std</small></span>
      </div>
      <div class="card-title">${esc(s.title)}</div>
      <div class="card-desc">${esc(s.description)}</div>
      <div class="card-footer">
        <div class="provider-row">
          <div class="avatar">${initials(s.providerName)}</div>
          <span class="provider-name">${esc(s.providerName || 'Unbekannt')}</span>
        </div>
        <span class="avail-dot">● Verfügbar</span>
      </div>
    </article>
  `).join('');
}

// ── Service Detail Modal ──────────────────────────────────────────────────────
function openServiceModal(id) {
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
        <span class="meta-label">Status</span>${esc(s.status || 'Aktiv')}
      </div>
    </div>
    <p class="modal-desc">${esc(s.description)}</p>

    <div id="bookingAlert" class="alert"></div>

    <div class="booking-date-box">
      <label class="form-label" for="bookingDateInput">Wähle deinen Termin</label>
      <input class="form-input" type="datetime-local" id="bookingDateInput" min="${nowDateTimeLocal()}" />
      <small class="date-help">Der Termin wird zuerst als <strong>PENDING</strong> an den Anbieter gesendet.</small>
    </div>

    <div class="price-block">
      <div>
        <div class="price-note">Stundensatz</div>
        <div class="price-big">€${parseFloat(s.price).toFixed(2)}</div>
      </div>
      <button class="btn btn-primary btn-lg" onclick="doBook('${s.id}')">
        Termin buchen
      </button>
    </div>
  `;
    document.getElementById('serviceModal').classList.add('open');
}

function closeServiceModal() {
    document.getElementById('serviceModal').classList.remove('open');
}

// ── Booking ───────────────────────────────────────────────────────────────────
async function doBook(serviceId) {
    if (!localStorage.getItem('customer_jwt')) {
        closeServiceModal();
        openAuthModal('login');
        showAlert('Bitte zuerst anmelden um zu buchen.', 'error');
        return;
    }

    const service = allServices.find(s => s.id === serviceId);
    const serviceDateValue = document.getElementById('bookingDateInput')?.value;

    if (!serviceDateValue) {
        showBookingAlert('Bitte wähle zuerst Datum und Uhrzeit aus.', 'error');
        return;
    }

    try {
        await fetchAPI('/bookings', 'POST', {
            serviceOfferingId: serviceId,
            customerId: localStorage.getItem('customer_user_id'),
            serviceDate: toOffsetDateTime(serviceDateValue)
        }, 'customer_jwt');

        document.getElementById('serviceModalContent').innerHTML = `
      <div class="book-success">
        <div class="success-icon">✓</div>
        <h3>Termin angefragt!</h3>
        <p>
          Du hast <strong>${esc(service?.title || 'diesen Service')}</strong> für<br/>
          <strong>${formatDateTime(toOffsetDateTime(serviceDateValue))}</strong> angefragt.<br/>
          Der Anbieter sieht die Anfrage als <strong>PENDING</strong>.
        </p>
        <br/>
        <button class="btn btn-ghost" onclick="closeServiceModal(); switchCustomerTab('bookings')">Meine Buchungen ansehen</button>
      </div>
    `;
        showToast('Termin wurde angefragt ✓');
    } catch (error) {
        showBookingAlert('Fehler bei der Buchung: Bitte überprüfe Datum und Login.', 'error');
        console.error('Buchungsfehler:', error);
    }
}

async function loadCustomerBookings() {
    const grid = document.getElementById('customerBookingsGrid');
    grid.innerHTML = `<div class="empty-state"><div class="empty-icon">⏳</div><p>Lade deine Buchungen…</p></div>`;

    const customerId = localStorage.getItem('customer_user_id');
    if (!customerId) return;

    try {
        const bookings = await fetchAPI(`/bookings/customer/${customerId}`, 'GET', null, 'customer_jwt');
        bookings.sort((a, b) => new Date(a.serviceDate) - new Date(b.serviceDate));

        if (bookings.length === 0) {
            grid.innerHTML = `<div class="empty-state"><div class="empty-icon">📭</div><p>Du hast noch keine Services gebucht.</p></div>`;
            return;
        }

        grid.innerHTML = bookings.map(b => renderCustomerBookingCard(b)).join('');
    } catch (error) {
        grid.innerHTML = `<div class="empty-state"><p>Fehler beim Laden der Buchungen.</p></div>`;
    }
}

function renderCustomerBookingCard(b) {
    const statusClass = statusToClass(b.status);
    const statusText = statusToText(b.status);
    const dateInputValue = toDateTimeLocalValue(b.serviceDate);
    const canEdit = b.status !== 'REJECTED' && b.status !== 'COMPLETED';

    return `
    <div class="service-card booking-card ${statusClass}" style="cursor: default;">
      <div class="card-top">
        <span class="cat-badge status-badge ${statusClass}">${statusText}</span>
      </div>
      <div class="card-title" style="margin-top: 10px; font-size: 1.1rem;">${esc(b.serviceTitle)}</div>
      <div class="booking-date-line">📅 ${formatDateTime(b.serviceDate)}</div>

      ${canEdit ? `
        <div class="booking-actions">
          <label class="form-label" for="customerDate-${b.id}">Termin ändern</label>
          <input class="form-input" type="datetime-local" id="customerDate-${b.id}" min="${nowDateTimeLocal()}" value="${dateInputValue}" />
          <div class="booking-action-row">
            <button class="btn btn-primary btn-sm" onclick="updateCustomerBookingDate('${b.id}')">Datum ändern</button>
            <button class="btn btn-danger btn-sm" onclick="cancelCustomerBooking('${b.id}')">Stornieren</button>
          </div>
          <small class="date-help">Änderung setzt die Buchung wieder auf <strong>PENDING</strong>.</small>
        </div>
      ` : `
        <div class="booking-date-line muted">Diese Buchung ist storniert/abgeschlossen.</div>
      `}
    </div>
  `;
}

async function updateCustomerBookingDate(bookingId) {
    const input = document.getElementById(`customerDate-${bookingId}`);
    if (!input?.value) {
        showToast('Bitte Datum und Uhrzeit wählen.');
        return;
    }

    try {
        await fetchAPI(`/bookings/${bookingId}/date`, 'PUT', {
            serviceDate: toOffsetDateTime(input.value)
        }, 'customer_jwt');
        showToast('Termin geändert – Anbieter muss erneut bestätigen.');
        loadCustomerBookings();
    } catch (error) {
        showToast('Fehler beim Ändern des Termins.');
    }
}

async function cancelCustomerBooking(bookingId) {
    if (!confirm('Willst du diese Buchung wirklich stornieren?')) return;

    try {
        await fetchAPI(`/bookings/${bookingId}/status`, 'PUT', { status: 'REJECTED' }, 'customer_jwt');
        showToast('Buchung wurde storniert.');
        loadCustomerBookings();
    } catch (error) {
        showToast('Fehler beim Stornieren.');
    }
}

// ── Auth ──────────────────────────────────────────────────────────────────────
function openAuthModal(tab) {
    switchAuthTab(tab || 'login');
    clearAlert();
    document.getElementById('authModal').classList.add('open');
}
function closeAuthModal() {
    document.getElementById('authModal').classList.remove('open');
}

function switchAuthTab(tab) {
    document.getElementById('loginForm').style.display = tab === 'login'    ? 'block' : 'none';
    document.getElementById('regForm').style.display   = tab === 'register' ? 'block' : 'none';
    document.getElementById('tabLogin').classList.toggle('active', tab === 'login');
    document.getElementById('tabReg').classList.toggle('active',   tab === 'register');
    clearAlert();
}

async function doLogin() {
    const email    = document.getElementById('loginEmail').value.trim();
    const password = document.getElementById('loginPassword').value;
    if (!email || !password) { showAlert('Bitte E-Mail und Passwort eingeben.', 'error'); return; }
    try {
        const data = await fetchAPI('/auth/login', 'POST', { email, password }, 'customer_jwt');
        localStorage.setItem('customer_jwt', data.token);
        localStorage.setItem('customer_user_id', data.userId);
        updateNavUI();
        closeAuthModal();
        showToast('Erfolgreich angemeldet ✓');
    } catch {
        showAlert('Login fehlgeschlagen. E-Mail oder Passwort falsch?', 'error');
    }
}

async function doRegister() {
    const firstName   = document.getElementById('regFirst').value.trim();
    const lastName    = document.getElementById('regLast').value.trim();
    const email       = document.getElementById('regEmail').value.trim();
    const password    = document.getElementById('regPassword').value;
    const accountType = document.getElementById('regType').value;
    if (!firstName || !lastName || !email || !password) {
        showAlert('Bitte alle Felder ausfüllen.', 'error'); return;
    }
    try {
        await fetchAPI('/auth/register', 'POST', { firstName, lastName, email, password, accountType }, 'customer_jwt');
        showAlert('Konto erstellt! Du kannst dich jetzt anmelden.', 'success');
        switchAuthTab('login');
        document.getElementById('loginEmail').value = email;
    } catch (e) {
        showAlert(e.message || 'Registrierung fehlgeschlagen.', 'error');
    }
}

function logout() {
    localStorage.removeItem('customer_jwt');
    localStorage.removeItem('customer_user_id');
    updateNavUI();
    showToast('Abgemeldet.');
}

function updateNavUI() {
    const token     = localStorage.getItem('customer_jwt');
    const loginBtn  = document.getElementById('loginNavBtn');
    const logoutBtn = document.getElementById('logoutBtn');
    const navUser   = document.getElementById('navUser');
    const tabs      = document.getElementById('customerTabs');

    if (token) {
        try {
            const payload = JSON.parse(atob(token.split('.')[1]));
            const isCustomer = payload.accountType === 'CUSTOMER';
            loginBtn.style.display  = 'none';
            logoutBtn.style.display = 'inline-flex';
            navUser.textContent = payload.sub || '';
            if (tabs) tabs.style.display = isCustomer ? 'flex' : 'none';
            if (!isCustomer && typeof switchCustomerTab === 'function') switchCustomerTab('market');
            return;
        } catch {
            // fall through to logged-out UI on parse errors
        }
    }

    loginBtn.style.display  = 'inline-flex';
    logoutBtn.style.display = 'none';
    navUser.textContent     = '';
    if (tabs) tabs.style.display = 'none';
    if (typeof switchCustomerTab === 'function') switchCustomerTab('market');
}

// ── Tabs Umschalten (Kunde) ───────────────────────────────────────────────────
function switchCustomerTab(tab) {
    document.getElementById('marketView').style.display = tab === 'market' ? 'block' : 'none';
    document.getElementById('bookingsView').style.display = tab === 'bookings' ? 'block' : 'none';
    document.getElementById('tabMarket').classList.toggle('active', tab === 'market');
    document.getElementById('tabMyBookings').classList.toggle('active', tab === 'bookings');

    if (tab === 'bookings') {
        loadCustomerBookings();
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────
function esc(str) {
    return String(str || '')
        .replace(/&/g,'&amp;').replace(/</g,'&lt;')
        .replace(/>/g,'&gt;').replace(/"/g,'&quot;');
}
function initials(name) {
    return (name || '?').split(' ').map(n => n[0]).join('').toUpperCase().slice(0, 2);
}
function showToast(msg) {
    const t = document.getElementById('toast');
    t.textContent = msg;
    t.classList.add('show');
    setTimeout(() => t.classList.remove('show'), 3000);
}
function showAlert(msg, type) {
    const el = document.getElementById('authAlert');
    el.textContent = msg;
    el.className   = `alert alert-${type}`;
    el.style.display = 'block';
}
function clearAlert() {
    const el = document.getElementById('authAlert');
    el.style.display = 'none';
    el.textContent   = '';
}
function showBookingAlert(msg, type) {
    const el = document.getElementById('bookingAlert');
    if (!el) return showToast(msg);
    el.textContent = msg;
    el.className = `alert alert-${type}`;
    el.style.display = 'block';
}
function statusToClass(status) {
    if (status === 'ACCEPTED') return 'status-accepted';
    if (status === 'REJECTED') return 'status-rejected';
    if (status === 'COMPLETED') return 'status-completed';
    return 'status-pending';
}
function statusToText(status) {
    if (status === 'ACCEPTED') return '✓ ACCEPTED';
    if (status === 'REJECTED') return '❌ REJECTED';
    if (status === 'COMPLETED') return '✅ COMPLETED';
    return '⏳ PENDING';
}
function pad2(n) {
    return String(n).padStart(2, '0');
}
function nowDateTimeLocal() {
    const d = new Date();
    d.setMinutes(d.getMinutes() - d.getTimezoneOffset());
    return d.toISOString().slice(0, 16);
}
function toDateTimeLocalValue(iso) {
    if (!iso) return '';
    const d = new Date(iso);
    if (Number.isNaN(d.getTime())) return '';
    return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}T${pad2(d.getHours())}:${pad2(d.getMinutes())}`;
}
function getLocalOffset() {
    const offsetMinutes = -new Date().getTimezoneOffset();
    const sign = offsetMinutes >= 0 ? '+' : '-';
    const abs = Math.abs(offsetMinutes);
    return `${sign}${pad2(Math.floor(abs / 60))}:${pad2(abs % 60)}`;
}
function toOffsetDateTime(localValue) {
    const withSeconds = localValue.length === 16 ? `${localValue}:00` : localValue;
    return `${withSeconds}${getLocalOffset()}`;
}
function formatDateTime(value) {
    if (!value) return 'Kein Termin gesetzt';
    const d = new Date(value);
    if (Number.isNaN(d.getTime())) return 'Ungültiges Datum';
    return d.toLocaleString('de-AT', { dateStyle: 'medium', timeStyle: 'short' });
}
