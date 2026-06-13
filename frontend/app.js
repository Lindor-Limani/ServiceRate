/* =========================================================================
   ServiceRate – app.js (Echte Backend-Anbindung + Claudes UI)
   ========================================================================= */

let services = []; // Wird jetzt vom Backend befüllt!
let activeCategory = "ALL";
let searchTerm     = "";
let minRating      = 0;
let sortMode       = "rating";

const CATEGORY_LABELS = { CLEANING: "Reinigung", REPAIR: "Reparatur", GARDENING: "Garten", MOVING: "Umzug" };
const $ = (id) => document.getElementById(id);

function renderStars(rating) {
    const full = Math.round(rating);
    return "★".repeat(full) + "☆".repeat(5 - full);
}

function showSection(name) {
    $("loginSection").style.display         = name === "login"   ? "block" : "none";
    $("servicesSection").style.display      = name === "market"  ? "block" : "none";
    $("createServiceSection").style.display = name === "create"  ? "block" : "none";

    $("navMarketplace").classList.toggle("active",   name === "market");
    $("navCreateService").classList.toggle("active", name === "create");
    $("editServiceSection").style.display = name === "edit" ? "block" : "none";
}

/* --- 1. DATEN VOM BACKEND LADEN --- */
async function loadServicesFromBackend() {
    try {
        // Echter GET Request an dein Spring Boot Backend!
        const rawServices = await fetchAPI('/services', 'GET');

        // Da wir (noch) keine echten Bewertungen/Standorte in der DB haben, füllen wir das mit Dummy-Werten auf, damit Claudes Design funktioniert
        services = rawServices.map(s => ({
            ...s,
            rating: 4.5, // Dummy Wert
            reviews: 12, // Dummy Wert
            location: "Wien" // Dummy Wert
        }));

        renderServices();
    } catch (error) {
        console.error("Fehler beim Laden der Services:", error);
    }
}

/* --- 2. MARKTPLATZ RENDERN --- */
function getVisibleServices() {
    return services
        .filter(s => activeCategory === "ALL" || s.category === activeCategory)
        .filter(s => s.rating >= minRating)
        .filter(s => {
            if (!searchTerm) return true;
            const haystack = (s.title + " " + s.providerName + " " + s.description).toLowerCase();
            return haystack.includes(searchTerm.toLowerCase());
        })
        .sort((a, b) => {
            if (sortMode === "price-asc")  return a.price - b.price;
            if (sortMode === "price-desc") return b.price - a.price;
            return b.rating - a.rating;
        });
}

function renderServices() {
    const list = getVisibleServices();
    const container = $("servicesList");
    const currentUserId = localStorage.getItem('user_id'); // Um zu prüfen, ob uns der Service gehört

    $("resultsCount").textContent = list.length + (list.length === 1 ? " Service gefunden" : " Services gefunden");

    if (list.length === 0) {
        container.innerHTML = '<div class="empty-state">Keine passenden Services gefunden.</div>';
        return;
    }

    container.innerHTML = list.map(s => `
        <article class="card">
            <div class="card-header">
                <h3>${s.title}</h3>
                <span class="category-badge">${CATEGORY_LABELS[s.category] || s.category}</span>
            </div>
            <div class="rating">
                <span class="stars">${renderStars(s.rating)}</span>
                <span class="rating-value">${s.rating.toFixed(1)}</span>
                <span class="rating-count">(${s.reviews} Bewertungen)</span>
            </div>
            <div class="card-provider">${s.providerName}</div>
            <div class="card-location">📍 ${s.location}</div>
            <p class="description">${s.description}</p>
            <div style="display: flex; gap: 5px;">
                <button class="details-btn">Anfragen</button>
                <button class="details-btn edit-btn" data-id="${s.id}" data-title="${s.title}" data-desc="${s.description}" data-cat="${s.category}" data-price="${s.price}" style="color: #f0a818; border-color: #f0a818;">✎</button>
                <button class="details-btn delete-btn" data-id="${s.id}" style="color: red; border-color: red;">X</button>
            </div>
        </article>
    `).join("");

    // Löschen Logik aktivieren (Echter DELETE Request)
    document.querySelectorAll('.delete-btn').forEach(button => {
        button.addEventListener('click', async (event) => {
            const serviceId = event.target.getAttribute('data-id');
            if(confirm("Diesen Service wirklich löschen?")) {
                try {
                    await fetchAPI(`/services/${serviceId}`, 'DELETE');
                    loadServicesFromBackend(); // Liste neu laden
                } catch (error) {
                    alert("Fehler beim Löschen.");
                }
            }
        });
    });
}

/* --- 3. FILTER EVENTS BINDEN --- */
function setupMarketplaceEvents() {
    $("searchBtn").addEventListener("click", () => { searchTerm = $("searchInput").value.trim(); renderServices(); });
    $("searchInput").addEventListener("keyup", (e) => { searchTerm = $("searchInput").value.trim(); renderServices(); });

    $("categoryChips").addEventListener("click", (e) => {
        const chip = e.target.closest(".chip");
        if (!chip) return;
        activeCategory = chip.dataset.cat;
        document.querySelectorAll(".chip").forEach(c => c.classList.remove("active"));
        chip.classList.add("active");
        renderServices();
    });

    $("ratingFilter").addEventListener("change", (e) => { minRating = parseFloat(e.target.value); renderServices(); });
    $("sortSelect").addEventListener("change",   (e) => { sortMode  = e.target.value; renderServices(); });
}

/* --- 4. LOGIN LOGIK --- */
$("loginForm").addEventListener("submit", async (e) => {
    e.preventDefault();
    const email = $("email").value.trim();
    const password = $("password").value;

    try {
        const data = await fetchAPI('/auth/login', 'POST', { email, password });
        localStorage.setItem('jwt_token', data.token);
        localStorage.setItem('user_id', data.userId);

        $("loginError").textContent = "";
        const name = email.split("@")[0];
        $("welcomeTitle").textContent = "Hallo, " + name + "! 👋";

        $("mainNav").style.display = "flex";
        showSection("market");
        loadServicesFromBackend(); // Echtes Laden!
    } catch (error) {
        $("loginError").textContent = "Login fehlgeschlagen. Passwort falsch?";
    }
});

$("navMarketplace").addEventListener("click",   () => { showSection("market"); loadServicesFromBackend(); });
$("navCreateService").addEventListener("click", () => showSection("create"));
$("logoutBtn").addEventListener("click", () => {
    localStorage.clear();
    $("mainNav").style.display = "none";
    showSection("login");
});

/* --- 5. ECHTES ERSTELLEN (POST) --- */
$("createServiceForm").addEventListener("submit", async (e) => {
    e.preventDefault();

    const title = $("serviceTitle").value;
    const description = $("serviceDescription").value;
    const category = $("serviceCategory").value;
    const price = parseFloat($("servicePrice").value);
    const providerId = localStorage.getItem('user_id');

    try {
        await fetchAPI('/services', 'POST', { providerId, title, description, category, price });

        $("createServiceMessage").textContent = "✓ Service veröffentlicht!";
        $("createServiceMessage").style.color = "green";
        $("createServiceForm").reset();

        setTimeout(() => {
            $("createServiceMessage").textContent = "";
            showSection("market");
            loadServicesFromBackend(); // Echtes neu Laden!
        }, 1200);
    } catch (error) {
        $("createServiceMessage").textContent = "❌ Fehler beim Erstellen.";
        $("createServiceMessage").style.color = "red";
    }
});

/* --- 6. WETTER API (M8) --- */
async function loadWeather() {
    const apiKey = '6d06aea9543246a5433f298cb611335e';
    const city = 'Vienna';
    try {
        const response = await fetch(`https://api.openweathermap.org/data/2.5/weather?q=${city}&appid=${apiKey}&units=metric&lang=de`);
        const data = await response.json();
        if (response.ok) {
            $("weatherIcon").innerHTML = `<img src="https://openweathermap.org/img/wn/${data.weather[0].icon}.png" style="width: 25px; vertical-align: middle;">`;
            $("weatherTemp").innerText = `${Math.round(data.main.temp)}°C in Wien`;
        }
    } catch (e) {
        $("weatherTemp").innerText = 'Wetter offline';
    }
}
/* --- 7. BEARBEITEN (PUT) --- */
// Wenn jemand auf den Stift (Bearbeiten) klickt
document.addEventListener('click', (e) => {
    if (e.target.classList.contains('edit-btn')) {
        // Daten aus dem Button auslesen und ins Formular füllen
        $("editServiceId").value = e.target.getAttribute('data-id');
        $("editServiceTitle").value = e.target.getAttribute('data-title');
        $("editServiceDescription").value = e.target.getAttribute('data-desc');
        $("editServiceCategory").value = e.target.getAttribute('data-cat');
        $("editServicePrice").value = e.target.getAttribute('data-price');

        // Ansicht wechseln
        showSection('edit');
    }
});

// Wenn das Bearbeiten-Formular abgeschickt wird
$("editServiceForm").addEventListener("submit", async (e) => {
    e.preventDefault();

    const id = $("editServiceId").value;
    const updateData = {
        title: $("editServiceTitle").value,
        description: $("editServiceDescription").value,
        category: $("editServiceCategory").value,
        price: parseFloat($("editServicePrice").value)
    };

    try {
        // PUT-Request an unser neues Backend senden!
        await fetchAPI(`/services/${id}`, 'PUT', updateData);

        $("editServiceMessage").textContent = "✓ Erfolgreich aktualisiert!";
        $("editServiceMessage").style.color = "green";

        setTimeout(() => {
            $("editServiceMessage").textContent = "";
            showSection("market");
            loadServicesFromBackend();
        }, 1200);
    } catch (error) {
        $("editServiceMessage").textContent = "❌ Fehler beim Speichern.";
        $("editServiceMessage").style.color = "red";
    }
});

$("cancelEditBtn").addEventListener("click", () => {
    showSection("market");
});
/* --- START --- */

setupMarketplaceEvents();
loadWeather();

// Auto-Login Check
if (localStorage.getItem('jwt_token')) {
    $("mainNav").style.display = "flex";
    showSection("market");
    loadServicesFromBackend();
} else {
    showSection("login");
}