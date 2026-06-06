// app.js - Frontend Logik

document.addEventListener("DOMContentLoaded", () => {
    // 1. HTML Elemente greifen
    const loginSection = document.getElementById('loginSection');
    const servicesSection = document.getElementById('servicesSection');
    const loginForm = document.getElementById('loginForm');
    const logoutBtn = document.getElementById('logoutBtn');
    const servicesList = document.getElementById('servicesList');
    const loginError = document.getElementById('loginError');

    // 2. Prüfen, ob wir schon einen Token haben (Auto-Login)
    if (localStorage.getItem('jwt_token')) {
        showServices();
    }

    // 3. Auf den Login-Button reagieren
    loginForm.addEventListener('submit', async (event) => {
        event.preventDefault(); // Verhindert, dass die Seite neu lädt

        const email = document.getElementById('email').value;
        const password = document.getElementById('password').value;

        try {
            // Wir nutzen deine fetchAPI Funktion aus api.js!
            const data = await fetchAPI('/auth/login', 'POST', { email, password });

            // Token sicher im Browser speichern
            localStorage.setItem('jwt_token', data.token);
            loginError.innerText = '';

            // Wechsel zur Service-Ansicht
            showServices();
        } catch (error) {
            loginError.innerText = 'Login fehlgeschlagen. Passwort oder E-Mail falsch.';
        }
    });

    // 4. Logout (Löscht den Token und zeigt wieder das Login-Fenster)
    logoutBtn.addEventListener('click', () => {
        localStorage.removeItem('jwt_token');
        servicesSection.style.display = 'none';
        logoutBtn.style.display = 'none';
        loginSection.style.display = 'block';
    });

    // 5. Services vom Backend laden und anzeigen
    async function showServices() {
        loginSection.style.display = 'none';
        servicesSection.style.display = 'block';
        logoutBtn.style.display = 'block';
        servicesList.innerHTML = '<p>Lade Services...</p>';

        try {
            // Hole die Liste per GET Request vom Backend
            const services = await fetchAPI('/services', 'GET');
            servicesList.innerHTML = ''; // "Lade Services..." Text entfernen

            if (services.length === 0) {
                servicesList.innerHTML = '<p>Noch keine Services vorhanden.</p>';
                return;
            }

            // Für jeden Service im Array bauen wir eine HTML-Karte
            services.forEach(service => {
                const card = document.createElement('div');
                card.className = 'card';
                card.innerHTML = `
                    <div class="card-header">
                        <h3>${service.title}</h3>
                        <span class="category-badge">${service.category}</span>
                    </div>
                    <p class="description">${service.description}</p>
                    <div class="card-footer">
                        <span class="price">ab ${service.price} €</span>
                        <span class="provider">Handwerker: ${service.providerName}</span>
                    </div>
                    <div style="display: flex; gap: 10px; margin-top: 10px;">
                        <button class="book-btn" style="flex: 1;">Anfragen</button>
                        <button class="delete-btn" data-id="${service.id}" style="flex: 1; background-color: #d32f2f; color: white;">Löschen</button>
                    </div>
                `;
                servicesList.appendChild(card);
            });

            // Allen Löschen-Buttons die Klick-Logik geben
            document.querySelectorAll('.delete-btn').forEach(button => {
                button.addEventListener('click', async (event) => {
                    const serviceId = event.target.getAttribute('data-id');

                    // Sicherheitsabfrage im Browser
                    if(confirm("Diesen Service wirklich löschen?")) {
                        try {
                            // M7: DELETE Request über unsere API an das Backend schicken
                            await fetchAPI(`/services/${serviceId}`, 'DELETE');

                            // Wenn erfolgreich gelöscht, laden wir die Liste einfach neu!
                            showServices();
                        } catch (error) {
                            alert("Fehler beim Löschen des Services.");
                        }
                    }
                });
            });

        } catch (error) {
            servicesList.innerHTML = '<p class="error-msg">Fehler beim Laden der Services.</p>';
        }
    }
});