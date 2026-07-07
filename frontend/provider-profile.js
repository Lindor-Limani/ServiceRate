const GLOBAL_VIEW_MODE_KEY = 'servicerate_view_mode';
ensureGlobalViewMode();
let providerProfileViewMode = preferredProviderProfileViewMode();
let currentProviderProfile = null;

(function initProviderProfile() {
  loadProviderProfile();
  window.addEventListener('pageshow', refreshProviderProfileViewFromStoredMode);
})();

function ensureGlobalViewMode() {
  if (!localStorage.getItem(GLOBAL_VIEW_MODE_KEY)) {
    localStorage.setItem(GLOBAL_VIEW_MODE_KEY, 'list');
  }
}

function preferredProviderProfileViewMode() {
  const globalMode = localStorage.getItem(GLOBAL_VIEW_MODE_KEY);
  return globalMode === 'grid' ? 'grid' : 'list';
}

async function loadProviderProfile() {
  const root = document.getElementById('providerProfileRoot');
  const id = new URLSearchParams(window.location.search).get('id');
  if (!id) {
    root.innerHTML = `<div class="empty-state"><p>Anbieter-ID fehlt.</p></div>`;
    return;
  }

  try {
    const profile = await fetchAPI(`/providers/${encodeURIComponent(id)}`, 'GET', null, 'customer_jwt');
    renderProviderProfile(profile);
  } catch (e) {
    console.error('Anbieterprofil konnte nicht geladen werden:', e);
    root.innerHTML = `<div class="empty-state"><p>Anbieterprofil konnte nicht geladen werden.</p></div>`;
  }
}

function renderProviderProfile(profile) {
  currentProviderProfile = profile;
  document.title = `${profile.name} - ServiceRate`;
  const root = document.getElementById('providerProfileRoot');
  root.innerHTML = `
    <section class="provider-profile-hero">
      ${avatarHtml(profile.name, profile.profileImageUrl, 'provider-profile-avatar')}
      <div>
        <span class="service-detail-kicker">Anbieterprofil</span>
        <h1>${esc(profile.name)}</h1>
        <p>${profile.serviceCount} Services · ${profile.reviewCount} Bewertungen · ${profile.averageRating ? profile.averageRating.toFixed(1) : '-'} Sterne · Trust Score ${profile.trustScore || 0}</p>
        <div class="filter-chips">${(profile.categories || []).map(c => `<span class="chip active">${esc(CAT_LABELS[c] || c)}</span>`).join('')}</div>
      </div>
    </section>
    <div class="section-header">
      <h2 class="section-title">Services dieses Anbieters</h2>
      <div class="section-actions">
        <span class="count-pill">${profile.serviceCount}</span>
        <div class="view-toggle" aria-label="Darstellung wählen">
          <button type="button" id="providerProfileGridViewBtn" onclick="setProviderProfileViewMode('grid')">Kacheln</button>
          <button type="button" id="providerProfileListViewBtn" onclick="setProviderProfileViewMode('list')">Liste</button>
        </div>
      </div>
    </div>
    <div class="cards-grid is-list-view" id="providerProfileServicesGrid">
      ${(profile.services || []).map(s => preferredProviderProfileViewMode() === 'list' ? renderProviderServiceListCard(s) : renderProviderServiceCard(s)).join('') || `<div class="empty-state"><p>Keine aktiven Services.</p></div>`}
    </div>
  `;
  applyProviderProfileViewMode();
}

function setProviderProfileViewMode(mode) {
  providerProfileViewMode = mode === 'list' ? 'list' : 'grid';
  localStorage.setItem(GLOBAL_VIEW_MODE_KEY, providerProfileViewMode);
  localStorage.setItem('servicerate_customer_market_view', providerProfileViewMode);
  localStorage.setItem('servicerate_customer_bookings_view', providerProfileViewMode);
  localStorage.setItem('servicerate_provider_services_view', providerProfileViewMode);
  localStorage.setItem('servicerate_provider_bookings_view', providerProfileViewMode);
  localStorage.setItem('servicerate_provider_profile_services_view', providerProfileViewMode);
  if (currentProviderProfile) renderProviderProfile(currentProviderProfile);
  else applyProviderProfileViewMode();
}

function applyProviderProfileViewMode() {
  providerProfileViewMode = preferredProviderProfileViewMode();
  const mode = providerProfileViewMode;
  document.getElementById('providerProfileServicesGrid')?.classList.toggle('is-list-view', mode === 'list');
  document.getElementById('providerProfileServicesGrid')?.classList.toggle('is-grid-view', mode === 'grid');
  document.getElementById('providerProfileGridViewBtn')?.classList.toggle('active', mode === 'grid');
  document.getElementById('providerProfileListViewBtn')?.classList.toggle('active', mode === 'list');
}

function refreshProviderProfileViewFromStoredMode() {
  if (currentProviderProfile) renderProviderProfile(currentProviderProfile);
  else applyProviderProfileViewMode();
}

function renderProviderServiceCard(s) {
  return `
    <article class="service-card list-layout-card" onclick="window.location.href='service-detail.html?id=${encodeURIComponent(s.id)}'">
      ${catImage(s.category, s.imageUrl)}
      <div class="list-card-body">
        <div class="card-top">
          <span class="cat-badge">${CAT_LABELS[s.category] || s.category}</span>
          <span class="card-price">
            €${parseFloat(s.price).toFixed(2)}<small>/Std</small>
            <span class="list-rating-inline">${ratingHtml(s)}</span>
          </span>
        </div>
        <div class="trust-badge"><span>Trust Score</span><strong>${Number(s.trustScore || 0)}</strong></div>
        <div class="card-title">${esc(s.title)}</div>
        <div class="card-desc">${esc(s.description)}</div>
        <div class="card-rating">${ratingHtml(s)}</div>
      </div>
    </article>
  `;
}

function renderProviderServiceListCard(s) {
  return `
    <article class="sr-list-card" onclick="window.location.href='service-detail.html?id=${encodeURIComponent(s.id)}'">
      ${listMedia(s.category, s.imageUrl)}
      <div class="sr-list-body">
        <div class="sr-list-top">
          <div class="sr-list-main">
            <span class="cat-badge">${CAT_LABELS[s.category] || s.category}</span>
            <div class="sr-list-title">${esc(s.title)}</div>
            <div class="sr-list-desc">${esc(s.description)}</div>
          </div>
          <div>
            <div class="sr-list-price">€${parseFloat(s.price).toFixed(2)}<small>/Std</small></div>
            <div class="sr-list-rating">${ratingHtml(s)}</div>
          </div>
        </div>
        <div class="sr-list-pills">
          <div class="trust-badge"><span>Trust Score</span><strong>${Number(s.trustScore || 0)}</strong></div>
        </div>
      </div>
    </article>
  `;
}

function listMedia(category, imageUrl = '') {
  if (imageUrl) {
    return `<div class="sr-list-media" style="background-image:url('${esc(imageUrl)}')"></div>`;
  }
  const img = CAT_IMAGES[category] || CAT_IMAGES.OTHER;
  return `<div class="sr-list-media" style="background:${img.bg}">${img.emoji}</div>`;
}

function initials(name) {
  return (name || '?').split(' ').map(n => n[0]).join('').toUpperCase().slice(0, 2);
}

function starString(rating) {
  const full = Math.max(0, Math.min(5, Math.round(rating)));
  return '★★★★★'.slice(0, full) + '☆☆☆☆☆'.slice(0, 5 - full);
}

function ratingHtml(s) {
  const count = s.reviewCount || 0;
  if (!count) return `<span class="stars">☆☆☆☆☆</span> <span>Noch keine Bewertungen</span>`;
  const avg = s.averageRating || 0;
  return `<span class="stars">${starString(avg)}</span> <span class="rating-num">${avg.toFixed(1)}</span> <span>(${count})</span>`;
}
