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
    allServices = await fetchAPI('/services', 'GET');
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
}

function closeServiceModal() {
  document.getElementById('serviceModal').classList.remove('open');
}

// ── Booking ───────────────────────────────────────────────────────────────────
// ── Booking ───────────────────────────────────────────────────────────────────
async function doBook(serviceId, title, price) {
    if (!localStorage.getItem('jwt_token')) {
        closeServiceModal();
        openAuthModal('login');
        showAlert('Bitte zuerst anmelden um zu buchen.', 'error');
        return;
    }

    try {
        // FEHLER 1 BEHOBEN: Wir senden serviceOfferingId (wie das Backend es erwartet)
        await fetchAPI('/bookings', 'POST', {
            serviceOfferingId: serviceId,
            customerId: localStorage.getItem('user_id')
        });

        // FEHLER 2 BEHOBEN: Die Erfolgsmeldung wird NUR angezeigt, wenn das Backend "OK" sagt
        document.getElementById('serviceModalContent').innerHTML = `
      <div class="book-success">
        <div class="success-icon">✓</div>
        <h3>Buchung erfolgreich!</h3>
        <p>Du hast <strong>${esc(title)}</strong> gebucht.<br/>Der Anbieter meldet sich in Kürze bei dir.</p>
        <br/>
        <button class="btn btn-ghost" onclick="closeServiceModal()">Schließen</button>
      </div>
    `;
        showToast('Buchung wurde gespeichert ✓');

    } catch (error) {
        // Wenn das Backend meckert, zeigen wir das jetzt ehrlich an!
        showModalAlert('Fehler bei der Buchung: Bitte überprüfe deine Daten.', 'error');
        console.error("Buchungsfehler:", error);
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
    const data = await fetchAPI('/auth/login', 'POST', { email, password });
    localStorage.setItem('jwt_token', data.token);
    localStorage.setItem('user_id',   data.userId);
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
    await fetchAPI('/auth/register', 'POST', { firstName, lastName, email, password, accountType });
    showAlert('Konto erstellt! Du kannst dich jetzt anmelden.', 'success');
    switchAuthTab('login');
    document.getElementById('loginEmail').value = email;
  } catch (e) {
    showAlert(e.message || 'Registrierung fehlgeschlagen.', 'error');
  }
}

function logout() {
  localStorage.removeItem('jwt_token');
  localStorage.removeItem('user_id');
  updateNavUI();
  showToast('Abgemeldet.');
    function updateNavUI() {
        const token     = localStorage.getItem('jwt_token');
        const loginBtn  = document.getElementById('loginNavBtn');
        const logoutBtn = document.getElementById('logoutBtn');
        const navUser   = document.getElementById('navUser');
        const tabs      = document.getElementById('customerTabs'); // NEU

        if (token) {
            try {
                const payload = JSON.parse(atob(token.split('.')[1]));

                if (payload.accountType === 'CUSTOMER') {
                    loginBtn.style.display  = 'none';
                    logoutBtn.style.display = 'inline-flex';
                    navUser.textContent = payload.sub || '';
                    tabs.style.display = 'flex'; // NEU: Tabs einblenden
                    return;
                }
            } catch { navUser.textContent = ''; }
        }

        // Ausgeloggter Zustand
        loginBtn.style.display  = 'inline-flex';
        logoutBtn.style.display = 'none';
        navUser.textContent     = '';
        tabs.style.display      = 'none'; // NEU: Tabs ausblenden
        if(typeof switchCustomerTab === 'function') switchCustomerTab('market'); // Zurück zum Marktplatz
    }
}

function updateNavUI() {
  const token     = localStorage.getItem('jwt_token');
  const loginBtn  = document.getElementById('loginNavBtn');
  const logoutBtn = document.getElementById('logoutBtn');
  const navUser   = document.getElementById('navUser');

  if (token) {
    loginBtn.style.display  = 'none';
    logoutBtn.style.display = 'inline-flex';
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      navUser.textContent = payload.sub || '';
    } catch { navUser.textContent = ''; }
  } else {
    loginBtn.style.display  = 'inline-flex';
    logoutBtn.style.display = 'none';
    navUser.textContent     = '';
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

// ── Buchungen des Kunden laden ────────────────────────────────────────────────
async function loadCustomerBookings() {
    const grid = document.getElementById('customerBookingsGrid');
    grid.innerHTML = `<div class="empty-state"><div class="empty-icon">⏳</div><p>Lade deine Buchungen…</p></div>`;

    const customerId = localStorage.getItem('user_id');
    if (!customerId) return;

    try {
        const bookings = await fetchAPI(`/bookings/customer/${customerId}`, 'GET');

        if (bookings.length === 0) {
            grid.innerHTML = `<div class="empty-state"><div class="empty-icon">📭</div><p>Du hast noch keine Services gebucht.</p></div>`;
            return;
        }

        grid.innerHTML = bookings.map(b => {
            // Farben und Texte für den Status
            let statusColor = b.status === 'PENDING' ? '#f59e0b' : (b.status === 'ACCEPTED' ? '#10b981' : '#ef4444');
            let statusText  = b.status === 'PENDING' ? 'Wartet auf Antwort' : (b.status === 'ACCEPTED' ? '✓ Akzeptiert' : '❌ Abgelehnt');

            return `
      <div class="service-card" style="cursor: default; border-top: 4px solid ${statusColor};">
        <div class="card-top">
          <span class="cat-badge" style="background: ${statusColor}; color: white; border: none;">${statusText}</span>
        </div>
        <div class="card-title" style="margin-top: 10px; font-size: 1.1rem;">${esc(b.serviceTitle)}</div>
        <div class="card-desc" style="margin-top: 5px;">Angeboten von: <strong>${esc(b.customerName)}</strong></div>
      </div>
      `;
        }).join('');
    } catch (error) {
        grid.innerHTML = `<div class="empty-state"><p>Fehler beim Laden der Buchungen.</p></div>`;
    }
}
