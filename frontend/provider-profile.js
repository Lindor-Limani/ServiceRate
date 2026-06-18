(function initProviderProfile() {
  loadProviderProfile();
})();

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
  document.title = `${profile.name} - ServiceRate`;
  const root = document.getElementById('providerProfileRoot');
  root.innerHTML = `
    <section class="provider-profile-hero">
      <div class="avatar provider-profile-avatar">${initials(profile.name)}</div>
      <div>
        <span class="service-detail-kicker">Anbieterprofil</span>
        <h1>${esc(profile.name)}</h1>
        <p>${profile.serviceCount} Services · ${profile.reviewCount} Bewertungen · ${profile.averageRating ? profile.averageRating.toFixed(1) : '-'} Sterne · Trust Score ${profile.trustScore || 0}</p>
        <div class="filter-chips">${(profile.categories || []).map(c => `<span class="chip active">${esc(CAT_LABELS[c] || c)}</span>`).join('')}</div>
      </div>
    </section>
    <div class="section-header">
      <h2 class="section-title">Services dieses Anbieters</h2>
      <span class="count-pill">${profile.serviceCount}</span>
    </div>
    <div class="cards-grid">
      ${(profile.services || []).map(renderProviderServiceCard).join('') || `<div class="empty-state"><p>Keine aktiven Services.</p></div>`}
    </div>
  `;
}

function renderProviderServiceCard(s) {
  return `
    <article class="service-card" onclick="window.location.href='service-detail.html?id=${encodeURIComponent(s.id)}'">
      ${catImage(s.category)}
      <div class="card-top">
        <span class="cat-badge">${CAT_LABELS[s.category] || s.category}</span>
        <span class="card-price">€${parseFloat(s.price).toFixed(2)}<small>/Std</small></span>
      </div>
      <div class="trust-badge"><span>Trust Score</span><strong>${Number(s.trustScore || 0)}</strong></div>
      <div class="card-title">${esc(s.title)}</div>
      <div class="card-desc">${esc(s.description)}</div>
      <div class="card-rating">${ratingHtml(s)}</div>
    </article>
  `;
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
