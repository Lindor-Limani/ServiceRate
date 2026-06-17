# ServiceRate 🛠️⭐

ServiceRate ist eine moderne, webbasierte Plattform, die Endkunden mit lokalen Dienstleistern und Handwerkern zusammenbringt. Kunden können gezielt nach Dienstleistungen suchen, Termine buchen und Services bewerten. Dienstleister verwalten ihre Angebote über ein interaktives Dashboard und steuern eingehende Buchungsanfragen.

Das Projekt wurde im Rahmen einer Lehrveranstaltung als Demonstration für eine saubere, entkoppelte **Client-Server-Architektur** (Fullstack-Webanwendung) entwickelt.

---

## 🏗️ Architektur & Kernkonzepte

ServiceRate setzt konsequent auf aktuelle Software-Engineering-Prinzipien und erfüllt strenge Sicherheits- und Performanz-Anforderungen:

* **Strikte Client-Server-Trennung (Decoupled Architecture):** Das Frontend läuft vollkommen unabhängig vom Backend im Browser und kommuniziert ausschließlich über asynchrone REST-Schnittstellen (JSON).
* **Asynchrone Kommunikation (AJAX):** Dynamische Aktualisierungen der Benutzeroberfläche erfolgen via Modern JavaScript (`fetch`-API mit `async/await`), ohne dass die Webseite neu geladen werden muss.
* **Zustandsloses Session-Management (Stateless Security via JWT):** Die Anwendung verwendet keine serverbasierten HTTP-Sessions (Kryptografische Absicherung). Nach erfolgreichem Login wird ein JSON Web Token (JWT) ausgestellt, im `localStorage` des Browsers verwaltet und bei geschützten Anfragen im HTTP-Header (`Authorization: Bearer <token>`) übermittelt.
* **Externe API-Integration:** Anreicherung von Systemdaten durch die Integration von Drittanbieter-REST-Schnittstellen (z. B. Postleitzahl-Validierung und geografische Zuordnung über *Zippopotam* im Backend sowie Wetterdaten über *OpenWeatherMap* im Frontend).

---

## 📁 Projektstruktur

Das Repository ist in zwei Hauptverzeichnisse unterteilt, um die Unabhängigkeit der beiden Systeme zu wahren:

```text
ServiceRate/
├── backend/                  # Spring Boot (Java) Backend
│   ├── src/main/java/at/hcw/serviceratebackend/
│   │   ├── config/           # Security-Konfiguration, CORS, JWT-Filter
│   │   ├── controller/       # REST-Endpunkte (Präsentationsschicht)
│   │   ├── dto/              # Daten-Transfer-Objekte (Java Records)
│   │   ├── model/
│   │   │   ├── entity/       # JPA/Hibernate-Datenbankmodelle
│   │   │   └── common/enums/ # Enums für Rollen und Buchungsstatus
│   │   ├── repository/       # Spring Data JPA Datenbank-Schnittstellen
│   │   └── service/          # Core-Geschäftslogik (Business Layer)
│   └── pom.xml               # Maven-Abhängigkeiten (Spring Boot Starter, JWT, etc.)
│
└── frontend/                 # Client-Anwendung (HTML5, CSS3, Vanilla JS)
    ├── index.html            # Einstiegsseite / Login & Registrierung
    ├── customer-app.html     # Dashboard & Buchungsmaske für Kunden
    ├── customer-app.js       # Logik für Suchen, Buchen, Bewerten & Wetter
    ├── provider-dashboard.html # Dashboard für Dienstleister
    ├── provider-dashboard.js   # Logik für Service-CRUD & Buchungsverwaltung
    ├── api.js                # Zentraler AJAX-Wrapper mit automatischer JWT-Injektion
    ├── utils.js              # UI-Hilfsfunktionen und Toast-Benachrichtigungen
    └── customer-style.css    # Zentrales, responsives Design-System
