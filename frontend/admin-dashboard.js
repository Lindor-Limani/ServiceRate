let adminState = {
  stats: null,
  users: [],
  services: [],
  bookings: [],
  reviews: [],
  reports: [],
  tab: 'users'
};

(function initAdmin() {
  if (localStorage.getItem('admin_jwt')) showAdmin();
})();

async function adminLogin() {
  const email = document.getElementById('adminEmail').value.trim();
  const password = document.getElementById('adminPassword').value;
  try {
    const data = await fetchAPI('/auth/login', 'POST', { email, password }, 'admin_jwt');
    const payload = JSON.parse(atob(data.token.split('.')[1]));
    if (payload.accountType !== 'ADMIN') {
      localStorage.removeItem('admin_jwt');
      notify('Kein Admin-Account.', 'error');
      return;
    }
    localStorage.setItem('admin_jwt', data.token);
    showAdmin();
  } catch (e) {
    notify(e.message || 'Admin-Login fehlgeschlagen.', 'error');
  }
}

function adminLogout() {
  localStorage.removeItem('admin_jwt');
  document.getElementById('adminContent').style.display = 'none';
  document.getElementById('adminLogin').style.display = 'block';
}

async function showAdmin() {
  document.getElementById('adminLogin').style.display = 'none';
  document.getElementById('adminContent').style.display = 'block';
  await loadAdminData();
}

async function loadAdminData() {
  renderAdminLoading();
  try {
    const [stats, users, services, bookings, reviews, reports] = await Promise.all([
      fetchAPI('/admin/stats', 'GET', null, 'admin_jwt'),
      fetchAPI('/admin/users', 'GET', null, 'admin_jwt'),
      fetchAPI('/admin/services', 'GET', null, 'admin_jwt'),
      fetchAPI('/admin/bookings', 'GET', null, 'admin_jwt'),
      fetchAPI('/admin/reviews', 'GET', null, 'admin_jwt'),
      fetchAPI('/admin/reports', 'GET', null, 'admin_jwt')
    ]);
    adminState = { ...adminState, stats, users, services, bookings, reviews, reports };
    renderAdmin();
  } catch (e) {
    document.getElementById('adminMainPanel').innerHTML = `<div class="review-empty">Admin-Daten konnten nicht geladen werden.</div>`;
    notify(e.message || 'Admin-Daten konnten nicht geladen werden.', 'error');
  }
}

function renderAdminLoading() {
  document.getElementById('adminStats').innerHTML = `<div class="review-loading">Dashboard wird geladen...</div>`;
  document.getElementById('adminMainPanel').innerHTML = `<div class="review-loading">Daten werden geladen...</div>`;
}

function renderAdmin() {
  renderAdminStats();
  renderAdminTabs();
  renderAdminPanel();
}

function renderAdminStats() {
  const s = adminState.stats || {};
  const totalRoles = Math.max(1, Number(s.customers || 0) + Number(s.providers || 0) + Number(s.admins || 0));
  const customerPct = Number(s.customers || 0) / totalRoles * 100;
  const providerPct = Number(s.providers || 0) / totalRoles * 100;
  document.getElementById('adminStats').innerHTML = `
    <div class="admin-chart-card">
      <div class="admin-donut" style="background:conic-gradient(#2563eb 0 ${customerPct}%, #f97316 ${customerPct}% ${customerPct + providerPct}%, #6d28d9 ${customerPct + providerPct}% 100%)"></div>
      <div>
        <strong>User Mix</strong>
        <p><span class="role-dot customer"></span>${s.customers || 0} Customers</p>
        <p><span class="role-dot provider"></span>${s.providers || 0} Provider</p>
        <p><span class="role-dot admin"></span>${s.admins || 0} Admins</p>
      </div>
    </div>
    ${statCard('Customers', s.customers, 'customer')}
    ${statCard('Providers', s.providers, 'provider')}
    ${statCard('Services', s.services, 'neutral')}
    ${statCard('Bookings', s.bookings, 'neutral')}
    ${statCard('Reviews', s.reviews, 'neutral')}
    ${statCard('Ø Rating', Number(s.averageRating || 0).toFixed(1), 'neutral')}
    ${statCard('Offen', s.openBookings, 'warn')}
    ${statCard('Completed', s.completedBookings, 'success')}
    ${statCard('Reports offen', s.openReports, 'danger')}
    ${statCard('Revenue', `€${Number(s.paidRevenue || 0).toFixed(0)}`, 'success')}
  `;
}

function statCard(label, value, tone) {
  return `
    <div class="stat-card admin-stat ${tone}">
      <div class="stat-label">${esc(label)}</div>
      <div class="stat-value">${value ?? 0}</div>
    </div>`;
}

function renderAdminTabs() {
  const tabs = [
    ['users', 'User Management'],
    ['services', 'Services'],
    ['bookings', 'Bookings'],
    ['reviews', 'Reviews'],
    ['reports', 'Reports']
  ];
  document.getElementById('adminTabs').innerHTML = tabs.map(([id, label]) => `
    <button class="chip ${adminState.tab === id ? 'active' : ''}" onclick="switchAdminTab('${id}')">${label}</button>
  `).join('');
}

function switchAdminTab(tab) {
  adminState.tab = tab;
  renderAdminTabs();
  renderAdminPanel();
}

function renderAdminPanel() {
  if (adminState.tab === 'users') return renderUsersPanel();
  if (adminState.tab === 'services') return renderServicesPanel();
  if (adminState.tab === 'bookings') return renderBookingsPanel();
  if (adminState.tab === 'reviews') return renderReviewsPanel();
  renderReportsPanel();
}

function renderUsersPanel() {
  const q = (document.getElementById('adminUserSearch')?.value || '').toLowerCase();
  const role = document.getElementById('adminRoleFilter')?.value || '';
  const status = document.getElementById('adminStatusFilter')?.value || '';
  const users = adminState.users.filter(u => {
    const text = `${u.firstName || ''} ${u.lastName || ''} ${u.email || ''}`.toLowerCase();
    return (!q || text.includes(q)) && (!role || u.accountType === role) && (!status || u.status === status);
  });
  document.getElementById('adminMainPanel').innerHTML = `
    <div class="admin-panel-head">
      <h2>User Management</h2>
      <div class="admin-filters">
        <input class="form-input" id="adminUserSearch" placeholder="User suchen" value="${esc(q)}" oninput="renderUsersPanel()">
        <select class="form-input" id="adminRoleFilter" onchange="renderUsersPanel()">
          <option value="">Alle Rollen</option><option value="CUSTOMER" ${role === 'CUSTOMER' ? 'selected' : ''}>Customer</option><option value="PROVIDER" ${role === 'PROVIDER' ? 'selected' : ''}>Provider</option><option value="ADMIN" ${role === 'ADMIN' ? 'selected' : ''}>Admin</option>
        </select>
        <select class="form-input" id="adminStatusFilter" onchange="renderUsersPanel()">
          <option value="">Alle Status</option><option value="ACTIVE" ${status === 'ACTIVE' ? 'selected' : ''}>Aktiv</option><option value="INACTIVE" ${status === 'INACTIVE' ? 'selected' : ''}>Inaktiv</option>
        </select>
      </div>
    </div>
    <div class="admin-card-list">${users.map(renderUserCard).join('') || emptyAdmin('Keine User gefunden.')}</div>
  `;
}

function renderUserCard(u) {
  const name = `${u.firstName || ''} ${u.lastName || ''}`.trim() || 'Unbekannt';
  const active = u.status === 'ACTIVE';
  return `
    <article class="admin-row-card">
      <div>${avatarHtml(name, u.profileImageUrl)}<strong>${esc(name)}</strong><span>${esc(u.email)}</span></div>
      <span class="role-badge ${String(u.accountType || '').toLowerCase()}">${esc(u.accountType)}</span>
      <span class="status-pill ${active ? 'active' : 'inactive'}">${active ? 'Aktiv' : 'Inaktiv'}</span>
      <button class="btn ${active ? 'btn-danger' : 'btn-primary'} btn-sm" onclick="toggleUserStatus('${u.id}', ${!active})">${active ? 'Deaktivieren' : 'Aktivieren'}</button>
    </article>
  `;
}

async function toggleUserStatus(id, active) {
  if (!confirm(active ? 'User wirklich aktivieren?' : 'User wirklich deaktivieren?')) return;
  try {
    await fetchAPI(`/admin/users/${id}/status`, 'PATCH', { active }, 'admin_jwt');
    notify('User-Status aktualisiert.', 'success');
    adminState.users = await fetchAPI('/admin/users', 'GET', null, 'admin_jwt');
    adminState.stats = await fetchAPI('/admin/stats', 'GET', null, 'admin_jwt');
    renderAdmin();
  } catch (e) {
    notify(e.message || 'Status konnte nicht geändert werden.', 'error');
  }
}

function renderServicesPanel() {
  const q = (document.getElementById('adminServiceSearch')?.value || '').toLowerCase();
  const status = document.getElementById('adminServiceStatusFilter')?.value || '';
  const services = adminState.services.filter(s => {
    const text = `${s.title || ''} ${s.providerName || ''} ${s.category || ''} ${s.location || ''}`.toLowerCase();
    return (!q || text.includes(q)) && (!status || (s.status || 'ACTIVE') === status);
  });
  document.getElementById('adminMainPanel').innerHTML = `
    <div class="admin-panel-head">
      <h2>Service Management</h2>
      <div class="admin-filters">
        <input class="form-input" id="adminServiceSearch" placeholder="Services suchen" value="${esc(q)}" oninput="renderServicesPanel()">
        <select class="form-input" id="adminServiceStatusFilter" onchange="renderServicesPanel()">
          <option value="">Alle Status</option>
          <option value="ACTIVE" ${status === 'ACTIVE' ? 'selected' : ''}>Aktiv</option>
          <option value="HIDDEN" ${status === 'HIDDEN' ? 'selected' : ''}>Versteckt</option>
          <option value="UNDER_REVIEW" ${status === 'UNDER_REVIEW' ? 'selected' : ''}>In Prüfung</option>
        </select>
        <span class="count-pill">${services.length}</span>
      </div>
    </div>
    <div class="admin-card-list">${services.map(s => `
      <article class="admin-row-card">
        <div><strong>${esc(s.title)}</strong><span>${esc(s.providerName)} · €${Number(s.price || 0).toFixed(2)} · ${s.reviewCount || 0} Reviews</span></div>
        <span class="status-pill ${s.status === 'ACTIVE' ? 'active' : 'inactive'}">${esc(s.status || 'ACTIVE')}</span>
        <button class="btn btn-ghost btn-sm" onclick="setServiceStatus('${s.id}', '${s.status === 'ACTIVE' ? 'HIDDEN' : 'ACTIVE'}')">${s.status === 'ACTIVE' ? 'Verstecken' : 'Aktivieren'}</button>
        <button class="btn btn-ghost btn-sm" onclick="setServiceStatus('${s.id}', 'UNDER_REVIEW')">Prüfen</button>
      </article>`).join('') || emptyAdmin('Keine Services.')}</div>`;
}

async function setServiceStatus(id, status) {
  try {
    await fetchAPI(`/admin/services/${id}/status`, 'PATCH', { status }, 'admin_jwt');
    adminState.services = await fetchAPI('/admin/services', 'GET', null, 'admin_jwt');
    notify('Service aktualisiert.', 'success');
    renderServicesPanel();
  } catch (e) {
    notify(e.message || 'Service konnte nicht aktualisiert werden.', 'error');
  }
}

function renderBookingsPanel() {
  const q = (document.getElementById('adminBookingSearch')?.value || '').toLowerCase();
  const status = document.getElementById('adminBookingStatusFilter')?.value || '';
  const payment = document.getElementById('adminBookingPaymentFilter')?.value || '';
  const bookings = adminState.bookings.filter(b => {
    const text = `${b.serviceTitle || ''} ${b.customerName || ''} ${b.providerName || ''} ${b.bookingDate || ''}`.toLowerCase();
    return (!q || text.includes(q)) && (!status || b.status === status) && (!payment || (b.paymentStatus || 'UNPAID') === payment);
  });
  const open = bookings.filter(b => b.status === 'PENDING' || b.status === 'ACCEPTED').length;
  document.getElementById('adminMainPanel').innerHTML = `
    <div class="admin-panel-head">
      <h2>Booking Management</h2>
      <div class="admin-filters">
        <input class="form-input" id="adminBookingSearch" placeholder="Bookings suchen" value="${esc(q)}" oninput="renderBookingsPanel()">
        <select class="form-input" id="adminBookingStatusFilter" onchange="renderBookingsPanel()">
          <option value="">Alle Status</option>
          <option value="PENDING" ${status === 'PENDING' ? 'selected' : ''}>Pending</option>
          <option value="ACCEPTED" ${status === 'ACCEPTED' ? 'selected' : ''}>Accepted</option>
          <option value="COMPLETED" ${status === 'COMPLETED' ? 'selected' : ''}>Completed</option>
          <option value="REJECTED" ${status === 'REJECTED' ? 'selected' : ''}>Rejected</option>
          <option value="CANCELLED" ${status === 'CANCELLED' ? 'selected' : ''}>Cancelled</option>
        </select>
        <select class="form-input" id="adminBookingPaymentFilter" onchange="renderBookingsPanel()">
          <option value="">Alle Zahlungen</option>
          <option value="UNPAID" ${payment === 'UNPAID' ? 'selected' : ''}>Unpaid</option>
          <option value="CHECKOUT_CREATED" ${payment === 'CHECKOUT_CREATED' ? 'selected' : ''}>Checkout</option>
          <option value="PAID" ${payment === 'PAID' ? 'selected' : ''}>Paid</option>
        </select>
        <span class="count-pill">${open} offen</span>
      </div>
    </div>
    <div class="admin-card-list">${bookings.map(b => `
      <article class="admin-row-card">
        <div><strong>${esc(b.serviceTitle)}</strong><span>${esc(b.customerName)} → ${esc(b.providerName)} · ${esc(b.bookingDate || '-')}</span></div>
        <span class="status-pill">${esc(b.status)}</span>
        <span class="payment-pill ${b.paymentStatus === 'PAID' ? 'is-paid' : ''}">${esc(b.paymentStatus || 'UNPAID')}</span>
      </article>`).join('') || emptyAdmin('Keine Bookings.')}</div>`;
}

function renderReviewsPanel() {
  const q = (document.getElementById('adminReviewSearch')?.value || '').toLowerCase();
  const minRating = Number(document.getElementById('adminReviewRatingFilter')?.value || 0);
  const reviews = adminState.reviews.filter(r => {
    const text = `${r.serviceTitle || ''} ${r.reviewerName || ''} ${r.comment || ''}`.toLowerCase();
    return (!q || text.includes(q)) && (!minRating || Number(r.rating || 0) <= minRating);
  });
  document.getElementById('adminMainPanel').innerHTML = `
    <div class="admin-panel-head">
      <h2>Review Management</h2>
      <div class="admin-filters">
        <input class="form-input" id="adminReviewSearch" placeholder="Reviews suchen" value="${esc(q)}" oninput="renderReviewsPanel()">
        <select class="form-input" id="adminReviewRatingFilter" onchange="renderReviewsPanel()">
          <option value="0">Alle Ratings</option>
          <option value="2" ${minRating === 2 ? 'selected' : ''}>Kritisch bis 2 Sterne</option>
          <option value="3" ${minRating === 3 ? 'selected' : ''}>Bis 3 Sterne</option>
        </select>
        <span class="count-pill">${reviews.length}</span>
      </div>
    </div>
    <div class="admin-card-list">${reviews.map(r => `
      <article class="admin-row-card ${r.rating <= 2 ? 'flagged' : ''}">
        <div><strong>${esc(r.serviceTitle)}</strong><span>${esc(r.reviewerName)} · ${'★'.repeat(r.rating)}${'☆'.repeat(5 - r.rating)}</span></div>
        <p>${esc(r.comment || 'Ohne Kommentar')}</p>
      </article>`).join('') || emptyAdmin('Keine Reviews.')}</div>`;
}

function renderReportsPanel() {
  const q = (document.getElementById('adminReportSearch')?.value || '').toLowerCase();
  const status = document.getElementById('adminReportStatusFilter')?.value || '';
  const type = document.getElementById('adminReportTypeFilter')?.value || '';
  const reports = adminState.reports.filter(r => {
    const text = `${r.targetType || ''} ${r.reason || ''} ${r.reporterEmail || ''} ${r.details || ''}`.toLowerCase();
    return (!q || text.includes(q)) && (!status || r.status === status) && (!type || r.targetType === type);
  });
  document.getElementById('adminMainPanel').innerHTML = `
    <div class="admin-panel-head">
      <h2>Reports</h2>
      <div class="admin-filters">
        <input class="form-input" id="adminReportSearch" placeholder="Reports suchen" value="${esc(q)}" oninput="renderReportsPanel()">
        <select class="form-input" id="adminReportStatusFilter" onchange="renderReportsPanel()">
          <option value="">Alle Status</option>
          <option value="OPEN" ${status === 'OPEN' ? 'selected' : ''}>Offen</option>
          <option value="IN_REVIEW" ${status === 'IN_REVIEW' ? 'selected' : ''}>In Prüfung</option>
          <option value="RESOLVED" ${status === 'RESOLVED' ? 'selected' : ''}>Geschlossen</option>
          <option value="REJECTED" ${status === 'REJECTED' ? 'selected' : ''}>Abgelehnt</option>
        </select>
        <select class="form-input" id="adminReportTypeFilter" onchange="renderReportsPanel()">
          <option value="">Alle Typen</option>
          <option value="SERVICE" ${type === 'SERVICE' ? 'selected' : ''}>Service</option>
          <option value="REVIEW" ${type === 'REVIEW' ? 'selected' : ''}>Review</option>
          <option value="PROVIDER" ${type === 'PROVIDER' ? 'selected' : ''}>Provider</option>
        </select>
        <span class="count-pill">${reports.filter(r => r.status === 'OPEN').length} offen</span>
      </div>
    </div>
    <div class="admin-card-list">${reports.map(r => `
      <article class="admin-row-card flagged">
        <div><strong>${esc(r.targetType)} · ${esc(r.reason)}</strong><span>${esc(r.reporterEmail)} · ${formatDateTimeShort(r.createdAt)}</span><p>${esc(r.details || '')}</p></div>
        <span class="status-pill">${esc(r.status)}</span>
        <button class="btn btn-ghost btn-sm" onclick="setReportStatus('${r.id}', 'IN_REVIEW')">Prüfen</button>
        <button class="btn btn-primary btn-sm" onclick="setReportStatus('${r.id}', 'RESOLVED')">Schließen</button>
      </article>`).join('') || emptyAdmin('Keine Reports.')}</div>`;
}

async function setReportStatus(id, status) {
  try {
    await fetchAPI(`/admin/reports/${id}/status`, 'PATCH', { status }, 'admin_jwt');
    adminState.reports = await fetchAPI('/admin/reports', 'GET', null, 'admin_jwt');
    adminState.stats = await fetchAPI('/admin/stats', 'GET', null, 'admin_jwt');
    notify('Report aktualisiert.', 'success');
    renderAdmin();
  } catch (e) {
    notify(e.message || 'Report konnte nicht aktualisiert werden.', 'error');
  }
}

function emptyAdmin(text) {
  return `<div class="review-empty">${esc(text)}</div>`;
}

function formatDateTimeShort(value) {
  if (!value) return '-';
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return '-';
  return d.toLocaleString('de-AT', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' });
}
