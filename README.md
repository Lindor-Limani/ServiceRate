# ServiceRate

ServiceRate ist eine webbasierte Plattform, die Endkunden mit lokalen Dienstleistern und Handwerkern verbindet. Kunden koennen Services suchen, Anbieterprofile ansehen, Buchungen erstellen, mit Dienstleistern kommunizieren und abgeschlossene Leistungen bewerten. Dienstleister verwalten ihre Angebote, bearbeiten Buchungsanfragen, dokumentieren Arbeitszeiten und koennen Lieferlinks sowie Zahlungsinformationen hinterlegen.

Das Projekt wurde als Fullstack-Webanwendung mit klarer Client-Server-Trennung umgesetzt: ein Spring-Boot-Backend stellt eine REST-API bereit, das Frontend ist eine eigenstaendige HTML/CSS/JavaScript-Anwendung.

---

## Architektur & Kernkonzepte

* **Client-Server-Architektur:** Frontend und Backend sind getrennt. Die Kommunikation erfolgt ueber JSON-basierte REST-Endpunkte.
* **Spring Boot Backend:** Java 21, Spring Web MVC, Spring Security, Spring Data JPA, PostgreSQL und OpenAPI/Swagger.
* **Vanilla-JS Frontend:** Mehrere HTML-Seiten fuer Kunden, Dienstleister und Admins mit zentralem API-Wrapper.
* **JWT-Security:** Login erzeugt ein JSON Web Token. Geschuetzte Requests nutzen `Authorization: Bearer <token>`.
* **Rollenmodell:** Kunden, Dienstleister und Admins erhalten unterschiedliche Zugriffsrechte.
* **Persistenz:** PostgreSQL via Docker Compose, Hibernate aktualisiert das Schema automatisch.
* **Externe APIs:** OpenWeatherMap fuer Wetterdaten und Zippopotam fuer PLZ-/Ortsvalidierung.
* **OpenAPI-Dokumentation:** Swagger UI ist unter `http://localhost:8081/swagger-ui.html` erreichbar.

---

## Projektstruktur

```text
ServiceRate/
├── backend/ServiceRateBackend/       # Spring Boot Backend
│   ├── src/main/java/at/hcw/serviceratebackend/
│   │   ├── config/                   # Security, JWT, OpenAPI, Migrationen
│   │   ├── controller/               # REST-Endpunkte
│   │   ├── dto/                      # Request-/Response-DTOs
│   │   ├── model/                    # JPA-Entities, Enums, Basisklassen
│   │   ├── repository/               # Spring Data JPA Repositories
│   │   └── service/                  # Business-Logik
│   ├── build.gradle                  # Gradle-Konfiguration
│   └── src/main/resources/
│       └── application.properties    # lokale Defaults und Env-Variablen
│
├── frontend/                         # HTML, CSS und Vanilla JavaScript
│   ├── index.html                    # Login, Registrierung, Passwort-Reset
│   ├── customer-app.html             # Kundendashboard
│   ├── provider-dashboard.html       # Dienstleisterdashboard
│   ├── admin-dashboard.html          # Adminbereich
│   ├── provider-profile.html         # oeffentliches Anbieterprofil
│   ├── service-detail.html           # Servicedetailseite
│   ├── api.js                        # zentraler Fetch-Wrapper mit JWT
│   └── customer-style.css            # gemeinsames Styling
│
├── docker-compose.yml                # PostgreSQL fuer lokale Entwicklung
└── test.http                         # Beispiel-Requests
```

---

## Lokal starten

### 1. Datenbank starten

Die PostgreSQL-Datenbank laeuft ueber Docker Compose und wird lokal auf Port `5533` veroeffentlicht, damit sie nicht mit einer lokalen PostgreSQL-Instanz auf Port `5432` kollidiert.

```bash
docker compose up -d
```

Standardwerte:

* Datenbank: `servicerate`
* Benutzer: `postgres`
* Passwort: `postgres`
* JDBC URL: `jdbc:postgresql://localhost:5533/servicerate`

### 2. Backend starten

```powershell
cd backend\ServiceRateBackend
.\gradlew.bat bootRun
```

Das Backend startet standardmaessig auf `http://localhost:8081`. Der Port kann mit `SERVER_PORT` ueberschrieben werden.

### 3. Frontend starten

Das Frontend besteht aus statischen Dateien. Es kann z. B. ueber VS Code Live Server, einen einfachen lokalen Webserver oder direkt im Browser geoeffnet werden. Wenn `frontend/` als Webroot gestartet wird, laeuft das Provider-Dashboard lokal unter `http://localhost:5500/provider-dashboard.html`.

---

## Konfiguration

Die wichtigsten Umgebungsvariablen:

| Variable | Zweck | Default |
| --- | --- | --- |
| `SERVER_PORT` | Backend-Port | `8081` |
| `APP_PLATFORM_FEE_PERCENT` | Prozentuale Plattformprovision je Buchung | `10` |
| `APP_PLATFORM_FEE_FIXED` | Fixe Plattformprovision je Buchung | `0` |
| `SPRING_DATASOURCE_URL` | PostgreSQL-Verbindung | `jdbc:postgresql://localhost:5533/servicerate` |
| `SPRING_DATASOURCE_USERNAME` | Datenbank-User | `postgres` |
| `SPRING_DATASOURCE_PASSWORD` | Datenbank-Passwort | `postgres` |
| `OPENWEATHER_API_KEY` | API-Key fuer Wetterdaten | leer |
| `PAYPAL_MODE` | `sandbox` oder `live` | `sandbox` |
| `PAYPAL_CLIENT_ID` | PayPal REST App Client ID | leer |
| `PAYPAL_CLIENT_SECRET` | PayPal REST App Secret | leer |
| `PAYPAL_RETURN_URL` | Frontend-URL nach erfolgreicher PayPal-Bestaetigung | `APP_FRONTEND_BASE_URL/customer-app.html` |
| `PAYPAL_CANCEL_URL` | Frontend-URL nach PayPal-Abbruch | `APP_FRONTEND_BASE_URL/customer-app.html` |
| `PAYPAL_SELLER_RETURN_URL` | Frontend-URL nach Provider-Onboarding | `APP_FRONTEND_BASE_URL/provider-dashboard.html` |
| `PAYPAL_PARTNER_ATTRIBUTION_ID` | Optionaler PayPal BN-Code fuer Partner Attribution | leer |
| `PAYPAL_PARTNER_MERCHANT_ID` | PayPal Merchant ID des Plattform-/Partnerkontos fuer Seller-Status-Abfragen | leer |
| `STRIPE_SECRET_KEY` | Stripe Secret Key der Plattform | leer |
| `STRIPE_WEBHOOK_SECRET` | Signatur-Secret fuer `/api/stripe/webhook` | leer |
| `STRIPE_CURRENCY` | Checkout-Waehrung | `eur` |
| `STRIPE_CONNECT_COUNTRY` | Land fuer neue Express Connected Accounts | `AT` |
| `STRIPE_REFRESH_URL` | Ruecksprung bei erneutem Stripe-Onboarding | `APP_FRONTEND_BASE_URL/provider-dashboard.html?stripe=refresh` |
| `STRIPE_RETURN_URL` | Ruecksprung nach Stripe-Onboarding | `APP_FRONTEND_BASE_URL/provider-dashboard.html?stripe=return` |
| `STRIPE_CHECKOUT_SUCCESS_URL` | Ruecksprung nach erfolgreichem Stripe Checkout | `APP_FRONTEND_BASE_URL/customer-app.html?stripe=success&session_id={CHECKOUT_SESSION_ID}` |
| `STRIPE_CHECKOUT_CANCEL_URL` | Ruecksprung nach abgebrochenem Stripe Checkout | `APP_FRONTEND_BASE_URL/customer-app.html?stripe=cancel` |
| `APP_FRONTEND_BASE_URL` | Ziel fuer Mail-Redirects | `http://localhost:5500` |
| `APP_BACKEND_BASE_URL` | Backend-Basis-URL fuer Links | `http://localhost:8081` |
| `APP_MAIL_MODE` | `console` oder `smtp` | `console` |

Beispiel fuer Wetterdaten:

```powershell
$env:OPENWEATHER_API_KEY="dein_api_key"
.\gradlew.bat bootRun
```

### PayPal Checkout

PayPal laeuft ueber die aktuelle REST-API im Backend. Das Frontend bekommt keine Secrets. Fuer lokale Sandbox-Tests eine PayPal REST App im Developer Dashboard anlegen und vor dem Backend-Start setzen:

```powershell
$env:PAYPAL_MODE="sandbox"
$env:PAYPAL_CLIENT_ID="deine_sandbox_client_id"
$env:PAYPAL_CLIENT_SECRET="dein_sandbox_secret"
$env:PAYPAL_RETURN_URL="http://localhost:5500/customer-app.html"
$env:PAYPAL_CANCEL_URL="http://localhost:5500/customer-app.html"
.\gradlew.bat bootRun
```

Alternativ kann die lokale Datei `backend/ServiceRateBackend/src/.env` verwendet werden. Sie wird beim Backendstart automatisch geladen und ist durch `.gitignore` nicht fuer Git vorgesehen. Werte in dieser Datei am besten ohne Anfuehrungszeichen schreiben:

```properties
PAYPAL_MODE=sandbox
PAYPAL_CLIENT_ID=deine_sandbox_client_id
PAYPAL_CLIENT_SECRET=dein_sandbox_secret
PAYPAL_RETURN_URL=http://localhost:5500/customer-app.html
PAYPAL_CANCEL_URL=http://localhost:5500/customer-app.html
PAYPAL_SELLER_RETURN_URL=http://localhost:5500/provider-dashboard.html
PAYPAL_PARTNER_MERCHANT_ID=deine_plattform_merchant_id
```

Beim Checkout erstellt `POST /api/bookings/{id}/checkout` mit `provider=PAYPAL` eine PayPal Order und gibt eine `checkoutUrl` zurueck. Nach der PayPal-Bestaetigung landet der Kunde wieder im Frontend; dort wird `POST /api/bookings/{id}/paypal/capture` aufgerufen und die Buchung bei erfolgreichem Capture auf `PAID` gesetzt.

### Stripe Connect Checkout

Kartenzahlungen laufen ueber Stripe Connect Express und Stripe Checkout. Provider verbinden Stripe im Provider-Dashboard ueber **Stripe verbinden**. Das Backend erstellt dabei einen Express Connected Account und leitet den Provider in das Stripe-hosted Onboarding weiter.

Fuer lokale Tests:

```powershell
$env:STRIPE_SECRET_KEY="sk_test_..."
$env:STRIPE_WEBHOOK_SECRET="whsec_..."
$env:STRIPE_RETURN_URL="http://localhost:5500/provider-dashboard.html?stripe=return"
$env:STRIPE_REFRESH_URL="http://localhost:5500/provider-dashboard.html?stripe=refresh"
$env:STRIPE_CHECKOUT_SUCCESS_URL="http://localhost:5500/customer-app.html?stripe=success&session_id={CHECKOUT_SESSION_ID}"
$env:STRIPE_CHECKOUT_CANCEL_URL="http://localhost:5500/customer-app.html?stripe=cancel"
.\gradlew.bat bootRun
```

Der Checkout wird mit `POST /api/bookings/{id}/checkout` und `provider=CARD` gestartet. ServiceRate erstellt eine Stripe Checkout Session mit Destination Charge, `application_fee_amount` und `transfer_data.destination`. Der Kunde gibt Kartendaten nur bei Stripe ein. Optional kann der Kunde beim Checkout zustimmen, dass Stripe die Zahlungsmethode fuer spaetere Buchungen speichert; ServiceRate speichert dafuer nur Stripe-IDs.

Der Webhook `/api/stripe/webhook` verarbeitet mindestens `checkout.session.completed`, `payment_intent.payment_failed` und `account.updated`. Lokal kann die Stripe CLI Events weiterleiten, z. B. an `http://localhost:8081/api/stripe/webhook`.

### Marketplace-Abrechnung

ServiceRate ist als Marketplace-Modell ausgelegt: Der Kunde zahlt fuer eine Buchung, die Plattform berechnet eine Provision und der Provider erhaelt den Restbetrag. Die Provisionshoehe wird ueber `APP_PLATFORM_FEE_PERCENT` und `APP_PLATFORM_FEE_FIXED` konfiguriert.

Bei PayPal wird der Provider als Payee verwendet, wenn im Provider-Profil eine PayPal Merchant ID hinterlegt ist. Die Plattformgebuehr wird im PayPal-Order-Request als `platform_fees` ausgewiesen. Fuer echte Live-Zahlungen braucht das Plattformkonto eine PayPal Commerce-Platform-/Multiparty-Freischaltung.

Bei Stripe wird der Provider als Connected Account verwendet, wenn `stripe_connected_account_id` hinterlegt und der Onboarding-Status `CONNECTED` ist. Die Plattformgebuehr wird beim Checkout als Application Fee angegeben; Kartendaten, CVC und vollstaendige Ablaufdaten werden nicht in ServiceRate gespeichert.

Provider muessen die Merchant ID nicht manuell suchen: Im Provider-Dashboard gibt es den Button **PayPal verbinden**. ServiceRate erzeugt dafuer einen PayPal Partner-Referrals-Link. Der Provider meldet sich bei PayPal an oder erstellt ein Konto, gibt der Plattform die noetigen Berechtigungen und wird danach zurueck zu `PAYPAL_SELLER_RETURN_URL` geleitet. PayPal haengt dabei unter anderem `merchantIdInPayPal`, `permissionsGranted` und `isEmailConfirmed` an die URL; diese Werte werden im Provider-Profil gespeichert.

PayPal unterscheidet dabei zwei Dinge:

* **REST App Credentials:** `PAYPAL_CLIENT_ID` und `PAYPAL_CLIENT_SECRET` gehoeren zur Plattform-App und werden vom Backend genutzt, um Access Tokens und Onboarding-/Checkout-API-Calls zu machen.
* **Sandbox Accounts:** Personal Accounts dienen als Testkaeufer, Business Accounts als Testseller. Ein Provider/Seller kann im Sandbox-Onboarding einen vorhandenen Business Account nutzen oder waehrend des PayPal-Flows einen anlegen. Fuer den produktiven Marketplace sind Business-/Merchant-Accounts die passende Zielannahme.

Wenn der Provider nach PayPal nicht sauber zur App zurueckkommt, muss in der PayPal Partner-Referrals-Anfrage die `partner_config_override.return_url` passen. Lokal ist das bei Webroot `frontend/` exakt `http://localhost:5500/provider-dashboard.html`. Falls PayPal `localhost` nicht akzeptiert, sollte ein HTTPS-Tunnel verwendet und dieselbe HTTPS-URL in `PAYPAL_SELLER_RETURN_URL` gesetzt werden.

Fuer nicht direkt gesplittete Zahlungsarten gilt:

* **Karte ueber Stripe:** Stripe kassiert, behaelt die Plattformgebuehr ein und transferiert den Provider-Anteil an den Connected Account. Settlement-Status wird `STRIPE_DESTINATION_CHARGE_COMPLETED`.
* **SEPA-Demo:** Plattform kassiert, Settlement-Status wird `PLATFORM_COLLECTED_PENDING_PROVIDER_PAYOUT`. Danach muss die Plattform den Provider-Netto-Betrag auszahlen.
* **Barzahlung/Bankueberweisung an Provider:** Provider kassiert direkt, Settlement-Status wird `PLATFORM_FEE_DUE_FROM_PROVIDER`. Danach muss der Provider die Plattformprovision begleichen.
* **Admin-Abschluss:** Admins koennen den Settlement-Status ueber `PATCH /api/admin/bookings/{id}/settlement` auf z. B. `PROVIDER_PAYOUT_SENT`, `PLATFORM_FEE_SETTLED` oder `DISPUTED` setzen.

### E-Mail-Versand

Registrierung, E-Mail-Verifizierung und Passwort-Reset laufen ueber das Backend. Lokal ist standardmaessig der Console-Modus aktiv; Mail-Inhalte werden in der Backend-Konsole ausgegeben. Fuer echten SMTP-Versand:

```powershell
$env:APP_MAIL_MODE="smtp"
$env:APP_MAIL_HOST="smtp.example.com"
$env:APP_MAIL_PORT="587"
$env:APP_MAIL_USERNAME="smtp_user"
$env:APP_MAIL_PASSWORD="smtp_password"
$env:APP_MAIL_FROM="noreply@example.com"
$env:APP_FRONTEND_BASE_URL="http://localhost:5500/frontend"
$env:APP_BACKEND_BASE_URL="http://localhost:8081"
```

---

## API-Endpunkte

Basis-URL lokal: `http://localhost:8081`

Viele Endpunkte sind geschuetzt und erwarten einen JWT im Header:

```http
Authorization: Bearer <token>
```

### Authentifizierung

| Methode | Endpoint | Zugriff | Beschreibung |
| --- | --- | --- | --- |
| `POST` | `/api/auth/register` | oeffentlich | Benutzer registrieren und Verifizierungs-Mail ausloesen |
| `GET` | `/api/auth/verify-email?token=...` | oeffentlich | E-Mail verifizieren und ins Frontend weiterleiten |
| `POST` | `/api/auth/login` | oeffentlich | Login, gibt JWT und Userdaten zurueck |
| `POST` | `/api/auth/forgot-password` | oeffentlich | Passwort-Reset-Link anfordern |
| `POST` | `/api/auth/reset-password` | oeffentlich | Passwort mit Reset-Token setzen |
| `POST` | `/api/auth/resend-verification` | oeffentlich | Verifizierungs-Mail erneut senden |

### Benutzer

| Methode | Endpoint | Zugriff | Beschreibung |
| --- | --- | --- | --- |
| `POST` | `/api/users` | oeffentlich | Benutzer direkt anlegen |
| `GET` | `/api/users/{id}` | eigener Account | Benutzerprofil laden |
| `PUT` | `/api/users/{id}` | eigener Account | Benutzerprofil aktualisieren |
| `DELETE` | `/api/users/{id}` | eigener Account | Benutzer loeschen |

### Services und Anbieter

| Methode | Endpoint | Zugriff | Beschreibung |
| --- | --- | --- | --- |
| `GET` | `/api/services` | oeffentlich | Alle aktiven Services abrufen |
| `GET` | `/api/services/{id}` | oeffentlich | Einzelnen Service abrufen |
| `GET` | `/api/services/my` | Provider | Eigene Services abrufen |
| `POST` | `/api/services` | Provider | Service anlegen |
| `PUT` | `/api/services/{id}` | authentifiziert | Service aktualisieren |
| `DELETE` | `/api/services/{id}` | authentifiziert | Service loeschen |
| `GET` | `/api/providers/{providerId}` | oeffentlich | Anbieterprofil mit Services und Bewertungen abrufen |
| `POST` | `/api/providers/me/paypal/onboarding-link` | Provider | PayPal-Seller-Onboarding-Link erzeugen |
| `POST` | `/api/providers/me/paypal/onboarding-return` | Provider | PayPal-Rueckkehr speichern |

### Buchungen, Arbeit und Lieferung

| Methode | Endpoint | Zugriff | Beschreibung |
| --- | --- | --- | --- |
| `POST` | `/api/bookings` | Customer | Buchung erstellen |
| `GET` | `/api/bookings/customer/me` | Customer | Eigene Kundenbuchungen abrufen |
| `GET` | `/api/bookings/customer/{customerId}` | authentifiziert | Buchungen eines Kunden abrufen |
| `GET` | `/api/bookings/provider/me` | Provider | Eigene Provider-Buchungen abrufen |
| `GET` | `/api/bookings/provider/{providerId}` | authentifiziert | Buchungen eines Providers abrufen |
| `PUT` | `/api/bookings/{id}/status` | Provider | Buchungsstatus aendern |
| `PUT` | `/api/bookings/{id}/work` | Provider | Arbeitsnotizen und Ist-Stunden aktualisieren |
| `POST` | `/api/bookings/{id}/time-entries` | Provider | Zeiteintrag zu einer Buchung erfassen |
| `POST` | `/api/bookings/{id}/delivery` | Provider | Lieferlink oder Ergebnis-Link veroeffentlichen |
| `GET` | `/api/bookings/{id}/delivery/open` | authentifiziert | Lieferlink per Redirect oeffnen |
| `GET` | `/api/bookings/{id}/delivery/url` | authentifiziert | Lieferlink als JSON abrufen |
| `POST` | `/api/bookings/{id}/checkout` | Customer | Checkout fuer eine Buchung erzeugen |
| `POST` | `/api/bookings/{id}/paypal/capture` | Customer | PayPal Order capturen und Buchung als bezahlt markieren |
| `POST` | `/api/bookings/{id}/mark-paid` | Customer | Buchung als bezahlt markieren |
| `POST` | `/api/bookings/{id}/record-payment` | Provider | Zahlung manuell dokumentieren |

### Reviews, Reports und Nachrichten

| Methode | Endpoint | Zugriff | Beschreibung |
| --- | --- | --- | --- |
| `POST` | `/api/reviews` | authentifiziert | Bewertung erstellen |
| `GET` | `/api/reviews/booking/{bookingId}` | oeffentlich | Bewertungen zu einer Buchung abrufen |
| `GET` | `/api/reviews/service/{serviceId}` | oeffentlich | Bewertungen zu einem Service abrufen |
| `POST` | `/api/reports` | authentifiziert | Service, Review oder User melden |
| `GET` | `/api/messages/booking/{bookingId}` | authentifiziert | Chatverlauf zu einer Buchung abrufen |
| `POST` | `/api/messages/booking/{bookingId}` | authentifiziert | Chatnachricht zu einer Buchung senden |

### Zahlungsmethoden

| Methode | Endpoint | Zugriff | Beschreibung |
| --- | --- | --- | --- |

### Wetter

| Methode | Endpoint | Zugriff | Beschreibung |
| --- | --- | --- | --- |
| `GET` | `/api/weather/current?city=Vienna` | oeffentlich | Aktuelles Wetter abrufen |
| `GET` | `/api/weather/forecast?city=Vienna&date=2026-07-06` | oeffentlich | Wetterprognose fuer ein Datum abrufen |

Wenn kein `OPENWEATHER_API_KEY` gesetzt ist, antworten die Wetter-Endpunkte mit `204 No Content`.

### Admin

| Methode | Endpoint | Zugriff | Beschreibung |
| --- | --- | --- | --- |
| `GET` | `/api/admin/stats` | Admin | Plattformstatistiken abrufen |
| `GET` | `/api/admin/users` | Admin | Benutzerliste abrufen |
| `GET` | `/api/admin/services` | Admin | Services fuer Moderation abrufen |
| `GET` | `/api/admin/bookings` | Admin | Buchungen abrufen |
| `GET` | `/api/admin/reviews` | Admin | Bewertungen abrufen |
| `GET` | `/api/admin/reports` | Admin | Meldungen abrufen |
| `PATCH` | `/api/admin/users/{id}/status` | Admin | Benutzer aktivieren/deaktivieren |
| `PATCH` | `/api/admin/services/{id}/status` | Admin | Service-Status setzen |
| `PATCH` | `/api/admin/reports/{id}/status` | Admin | Meldungsstatus aktualisieren |

Zusatzendpunkte fuer Dokumentation und Entwicklung:

* `GET /swagger-ui.html`
* `GET /v3/api-docs`
* `/h2-console/**` ist in der Security-Konfiguration freigegeben, die lokale Standarddatenbank ist jedoch PostgreSQL.

---

## Implementierte Features

* Registrierung, Login, JWT-basierte Authentifizierung und rollenbasierte Autorisierung.
* E-Mail-Verifizierung, erneutes Senden der Verifizierung und Passwort-Reset.
* Kunden-, Dienstleister- und Admin-Oberflaechen im Frontend.
* Service-Marktplatz mit Service-Detailseiten und Anbieterprofilen.
* CRUD-Funktionen fuer Dienstleister-Services inklusive Kategorie, Preis, Standort, Bildern, Lieferart und Status.
* PLZ-/Standortvalidierung ueber externe API im Backend.
* Buchungsworkflow fuer Kunden und Dienstleister mit Statuswechseln wie `PENDING`, `ACCEPTED`, `REJECTED`, `COMPLETED` und `CANCELLED`.
* Arbeitsdokumentation durch Provider mit Notizen, Ist-Stunden und Zeiteintraegen.
* Lieferlinks fuer digitale oder hybride Leistungen.
* Zahlungsstatus, PayPal REST Checkout, manuelle Zahlungsdokumentation und Verwaltung eigener Zahlungsmethoden.
* Bewertungssystem fuer Services und Buchungen.
* Meldesystem fuer problematische Inhalte oder Nutzer.
* Buchungsbezogener Chat zwischen Kunde und Dienstleister.
* Admin-Dashboard mit Statistiken, User-, Service-, Booking-, Review- und Report-Verwaltung.
* Wetterdaten als Backend-Proxy, damit der OpenWeatherMap-Key nicht im Frontend liegt.
* Swagger/OpenAPI-Dokumentation fuer die REST-API.
* Lokale PostgreSQL-Entwicklungsumgebung via Docker Compose.

---

## Moegliche Upcoming Features

* Echtzeit-Chat mit WebSockets statt reinem REST-Polling.
* Volltextsuche, Filter und Sortierung nach Kategorie, Preis, Entfernung, Bewertung und Verfuegbarkeit.
* Kalenderintegration fuer Dienstleister und Kunden.
* Automatische Preisberechnung aus Stunden, Materialkosten, Rabatten und Steuern.
* PayPal Webhooks fuer asynchrone Zahlungsbestaetigungen und Refunds.
* Integration eines echten Stripe-Checkouts inklusive Webhooks.
* Datei-Uploads fuer Angebote, Rechnungen, Vorher-/Nachher-Bilder und digitale Deliverables.
* Benachrichtigungen per E-Mail, Push oder In-App-Notification bei Buchungs- und Zahlungsstatusaenderungen.
* Erweiterte Admin-Moderation mit Audit-Log, Eskalationen und Sperrgruenden.
* Favoriten, gespeicherte Suchen und Wiederholungsbuchungen fuer Kunden.
* Provider-Verifizierung, Zertifikate und Vertrauensabzeichen.
* Mehrsprachigkeit und barrierefreiere UI-Komponenten.
* Automatisierte End-to-End-Tests fuer die wichtigsten Kunden- und Provider-Flows.
* Deployment-Konfiguration fuer produktive Umgebungen inklusive Reverse Proxy, HTTPS und CI/CD.

---

## Tests

Backend-Tests koennen im Backend-Verzeichnis ausgefuehrt werden:

```powershell
cd backend\ServiceRateBackend
.\gradlew.bat test
```
