# ServiceRate Teststrategie

## Fehlende Testbereiche

- Security-Regressionen waren bisher kaum automatisiert: Rollen, manipulierte Tokens, Self-Service-Zugriff, Admin-Schutz und Stripe-Webhook-Verifikation.
- Performance-/Lasttests fehlten vollständig.
- Usability-/E2E-Tests für das statische Frontend fehlten vollständig.
- Datei-Uploads sind aktuell clientseitige Base64-Vorschauen, kein echter Storage-Upload. Deshalb gibt es dafür derzeit keine Backend-Upload-Tests.
- Rate Limiting ist im Produktivcode nicht erkennbar. Die Performance-Skripte zeigen Lastverhalten, ersetzen aber kein echtes Abuse-Protection-Feature.

## Strategie

- **Regelmäßig lokal und in CI:** `./gradlew test` für Unit-, Integration-, API- und Security-Tests.
- **Vor Releases:** Playwright-E2E gegen gemockte API und einmal gegen eine lokale integrierte Umgebung.
- **Vor Releases oder bei Performance-relevanten Änderungen:** k6 Baseline und Stress.
- **Bei Bedarf:** k6 Spike und authentifizierte Booking-Smoke-Tests mit dedizierten Testdaten.

## Testfälle

- Security:
  - Kein Token, ungültiges Token, manipuliertes Token.
  - Inaktive User mit ansonsten gültigem Token.
  - Kunde/Provider dürfen keine Admin-Endpunkte aufrufen.
  - Kunde darf Provider-Endpunkte nicht aufrufen, Provider darf Customer-Endpunkte nicht aufrufen.
  - User darf nur eigenes Profil lesen.
  - SQL-Injection-ähnliche Suchparameter erzeugen keinen Serverfehler.
  - Manipulierte Rolle `ADMIN` im Profilupdate wird abgelehnt.
  - Stripe-Webhook benötigt Signaturheader und delegiert Signaturprüfung an den Service.
  - Basis-Security-Header `X-Content-Type-Options` und `X-Frame-Options`.

- Performance:
  - 10 VUs Baseline: 95 Prozent normaler API-Requests unter 500 ms.
  - 50 bis 100 VUs Stress: 95 Prozent unter 1 s, Fehlerrate unter 2 Prozent.
  - Spike bis 150 VUs: 95 Prozent unter 1,5 s, Fehlerrate unter 5 Prozent.
  - Authentifizierte Booking-Smoke-Last nur mit dedizierten Testtokens und Test-Service-ID.

- Usability:
  - Kunde sieht Marktplatz, Suche und Services.
  - XSS-artiger Service-Text wird als Text gerendert, nicht als ausführbares HTML.
  - Registrierung und ungültiger Login zeigen verständliche Meldungen.
  - Buchung ohne Pflichtdatum wird blockiert.
  - Desktop- und Mobile-Viewport zeigen Hauptnavigation und Suche.

## Installation

Backend-Tests nutzen Gradle und die vorhandenen Spring-Testabhängigkeiten:

```powershell
cd backend\ServiceRateBackend
.\gradlew.bat test
```

Playwright installieren:

```powershell
npm install
npx playwright install
npm run test:e2e
```

k6 installieren:

```powershell
winget install k6.k6
```

Lasttests ausführen, während Backend und Datenbank lokal laufen:

```powershell
npm run test:perf:baseline
npm run test:perf:stress
npm run test:perf:spike
```

Authentifizierter Booking-Smoke:

```powershell
k6 run tests/performance/authenticated-booking-smoke.js `
  -e CUSTOMER_TOKEN="<test-customer-jwt>" `
  -e SERVICE_ID="<test-service-uuid>"
```

## Annahmen

- Tests verwenden ausschließlich lokale Testdaten.
- Stripe, PayPal, Mail und externe APIs werden in Unit-/Controller-Tests gemockt oder nicht aufgerufen.
- Die k6-Skripte greifen nur lokale oder explizit gesetzte `BASE_URL`-Umgebungen an.
- Für Playwright werden API-Antworten gemockt, damit keine echte Datenbank, Zahlung oder E-Mail nötig ist.

## Empfehlungen

- Rate Limiting für Login, Buchungen und Suche ergänzen und anschließend dedizierte Abuse-Tests aktivieren.
- CORS nicht dauerhaft mit `*` betreiben; erlaubte Frontend-Origin per Konfiguration begrenzen.
- CSP und Referrer-Policy explizit setzen.
- JWT-Secret aus Konfiguration/Secret Store laden, nicht hart codieren.
- Logout ist clientseitig über Token-Löschung gelöst; serverseitige Session-Invalidierung gibt es nicht.
