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
  } catch {
    notify('Admin-Login fehlgeschlagen.', 'error');
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
  await Promise.all([loadAdminStats(), loadAdminUsers(), loadAdminServices()]);
}

async function loadAdminStats() {
  const stats = await fetchAPI('/admin/stats', 'GET', null, 'admin_jwt');
  document.getElementById('adminStats').innerHTML = Object.entries(stats).map(([key, value]) => `
    <div class="stat-card">
      <div class="stat-label">${esc(key)}</div>
      <div class="stat-value green">${value}</div>
    </div>
  `).join('');
}

async function loadAdminUsers() {
  const users = await fetchAPI('/admin/users', 'GET', null, 'admin_jwt');
  document.getElementById('adminUsers').innerHTML = `
    <table class="admin-table">
      <thead><tr><th>Name</th><th>E-Mail</th><th>Rolle</th><th>Status</th></tr></thead>
      <tbody>${users.map(u => `<tr><td>${esc(`${u.firstName || ''} ${u.lastName || ''}`)}</td><td>${esc(u.email)}</td><td>${esc(u.accountType)}</td><td>${esc(u.status)}</td></tr>`).join('')}</tbody>
    </table>
  `;
}

async function loadAdminServices() {
  const services = await fetchAPI('/admin/services', 'GET', null, 'admin_jwt');
  document.getElementById('adminServices').innerHTML = `
    <table class="admin-table">
      <thead><tr><th>Service</th><th>Anbieter</th><th>Preis</th><th>Reviews</th></tr></thead>
      <tbody>${services.map(s => `<tr><td>${esc(s.title)}</td><td>${esc(s.providerName)}</td><td>€${Number(s.price || 0).toFixed(2)}</td><td>${s.reviewCount || 0}</td></tr>`).join('')}</tbody>
    </table>
  `;
}
