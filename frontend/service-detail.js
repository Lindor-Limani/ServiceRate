let detailService = null;

(function initServiceDetail() {
  loadServiceDetail();
})();

async function loadServiceDetail() {
  const root = document.getElementById('serviceDetailRoot');
  const id = new URLSearchParams(window.location.search).get('id');

  if (!id) {
    root.innerHTML = `<div class="empty-state"><p>Service-ID fehlt.</p></div>`;
    return;
  }

  try {
    detailService = await fetchAPI(`/services/${encodeURIComponent(id)}`, 'GET', null, 'customer_jwt');
    renderServiceDetail();
  } catch (e) {
    console.error('Service konnte nicht geladen werden:', e);
    root.innerHTML = `<div class="empty-state"><p>Service konnte nicht geladen werden.</p></div>`;
  }
}

function renderServiceDetail() {
  const s = detailService;
  document.title = `${s.title} - ServiceRate`;

  document.getElementById('serviceDetailRoot').innerHTML = `
    <section class="service-detail-page">
      <div class="service-detail-main">
        ${catImage(s.category, s.imageUrl)}
        <div class="service-detail-kicker">${CAT_LABELS[s.category] || s.category}</div>
        <h1>${esc(s.title)}</h1>
        <p class="service-detail-desc">${esc(s.description)}</p>
        ${trustBadge(s.trustScore)}
        ${serviceRatingPanel(s)}
        <section class="service-reviews">
          <div class="service-reviews-head">
            <div>
              <span class="service-reviews-kicker">Kundenstimmen</span>
              <h4>Bewertungen zu diesem Service</h4>
            </div>
            <span class="service-reviews-summary">${ratingHtml(s)}</span>
          </div>
          <div class="service-reviews-list">${renderPublicReviews(s.reviews || [])}</div>
        </section>
      </div>
      <aside class="service-detail-side">
        <div class="detail-provider">
          ${avatarHtml(s.providerName, s.providerProfileImageUrl)}
          <div>
            <span>Anbieter</span>
            <strong>${esc(s.providerName || 'Unbekannt')}</strong>
            ${s.providerId ? `<a class="provider-profile-link" href="provider-profile.html?id=${encodeURIComponent(s.providerId)}">Profil ansehen</a>` : ''}
          </div>
        </div>
        <div class="detail-price">€${parseFloat(s.price).toFixed(2)}<small>/Std</small></div>
        <div class="booking-date-line">Ort: <strong>${esc(s.location || '-')}</strong></div>
        <div class="booking-date-line">Umfang: <strong>${esc(serviceMetaLine(s))}</strong></div>
        <div class="form-group">
          <label class="form-label">Wunschtermin</label>
          <input class="form-input" type="date" id="detailBookingDate" min="${todayISO()}" />
        </div>
        <button class="btn btn-primary btn-full" onclick="bookDetailService()">Jetzt buchen</button>
      </aside>
    </section>
  `;
}

function serviceMetaLine(s) {
  const parts = [];
  if (s.estimatedHours) parts.push(`ca. ${Number(s.estimatedHours).toFixed(1)} Std`);
  if (s.deliverableType === 'DIGITAL') parts.push('digitale Lieferung');
  if (s.deliverableType === 'HYBRID') parts.push('hybrid');
  return parts.length ? parts.join(' · ') : 'nach Vereinbarung';
}

async function bookDetailService() {
  if (!localStorage.getItem('customer_jwt')) {
    notify('Bitte melde dich im Marktplatz als Kunde an.', 'error');
    return;
  }

  const bookingDate = document.getElementById('detailBookingDate').value;
  if (!bookingDate) {
    notify('Bitte waehle einen Wunschtermin.', 'error');
    return;
  }

  try {
    await fetchAPI('/bookings', 'POST', {
      serviceOfferingId: detailService.id,
      bookingDate
    }, 'customer_jwt');
    notify('Buchung wurde gespeichert.', 'success');
  } catch (e) {
    notify(e.message || 'Buchung konnte nicht gespeichert werden.', 'error');
  }
}

function todayISO() {
  const d = new Date();
  d.setMinutes(d.getMinutes() - d.getTimezoneOffset());
  return d.toISOString().split('T')[0];
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

function serviceRatingPanel(s) {
  const count = s.reviewCount || 0;
  const avg = s.averageRating || 0;
  return `
    <div class="service-rating-panel ${count ? '' : 'is-empty'}">
      <div class="service-rating-score">
        <strong>${count ? avg.toFixed(1) : '-'}</strong>
        <span>${starString(avg)}</span>
      </div>
      <div class="service-rating-copy">
        <b>${count ? `${count} Bewertung${count === 1 ? '' : 'en'}` : 'Noch keine Bewertungen'}</b>
        <small>${count ? 'Von gebuchten Kunden bewertet' : 'Bewertungen erscheinen nach abgeschlossenen Buchungen'}</small>
      </div>
    </div>
  `;
}

function trustBadge(score) {
  return `<div class="trust-badge"><span>Trust Score</span><strong>${Number(score || 0)}</strong></div>`;
}

function renderPublicReviews(reviews) {
  if (!Array.isArray(reviews) || reviews.length === 0) {
    return `<div class="review-empty">Noch keine Bewertungen vorhanden.</div>`;
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
