# ServiceRate Market-Readiness-Audit

Stand: 14. Juli 2026  
Audit-Typ: statisches Full-Repository-Audit plus lokale Build-/Test- und Dependency-Prüfungen  
Geprüfter Branch/Commit: `feature/laptop-config` / `ff6e1d3`  
Rechtlicher Hinweis: Dieses Dokument bewertet technische Compliance-Voraussetzungen und ersetzt keine Rechtsberatung.

## Prüfgrundlage und Grenzen

Geprüft wurden der reale Java-/Spring-Code, DTOs, JPA-Entities, Repositories, Konfigurationen, statische Frontends, Docker Compose, Tests, Performance-Skripte, Dokumentation und der aufgelöste Dependency-Tree. Maßgeblich waren [OWASP ASVS 5.0.0](https://github.com/OWASP/ASVS/tree/v5.0.0_release), [OWASP Top 10:2025](https://owasp.org/Top10/), [OWASP API Security Top 10:2023](https://owasp.org/API-Security/editions/2023/en/0x11-t10/), und [WCAG 2.2](https://www.w3.org/TR/WCAG22/) auf Konformitätsniveau AA. OWASP MASVS wurde nicht angewandt, weil kein nativer mobiler Client vorhanden ist.

Ausgeführt wurden:

- `gradlew.bat test --rerun-tasks --no-daemon`: 58 Tests, 0 Fehler, 0 übersprungen, Build erfolgreich.
- `npm run test:e2e`: 8 Playwright-Tests auf Desktop Chrome und Pixel-5-Profil, alle erfolgreich.
- `npm audit --json`: 0 bekannte npm-Findings im Lockfile.
- OSV Query API gegen 115 aufgelöste Maven-Komponenten: 34 Treffer; Details in FINDING-015. Das ist eine Momentaufnahme, kein Ersatz für einen kontinuierlichen SCA-Prozess.
- Quellcode-Suchen nach Endpunkten, Autorisierungsregeln, Transaktionen, Locks, Secrets, DOM-Sinks, Tokenhaltung, Uploads, Resilienz- und Observability-Kontrollen.

Nicht prüfbar waren eine deployte Produktionsumgebung, TLS/DNS/WAF, produktive Secret Stores, reale Stripe-/PayPal-/SMTP-Konten, echte PostgreSQL-Produktionsdaten und Query-Pläne, Backup/Restore, Infrastructure-as-Code außerhalb des Repositories, Datenschutzverträge, rechtliche Texte, ein Live-Penetrationstest, DAST, Container-Scanning und reale Lasttests. Die k6-Dateien wurden geprüft, aber nicht gegen eine laufende Umgebung ausgeführt, weil sie Daten erzeugen können.

## A. Executive Summary

**Gesamtbewertung: 26/100**

**Launch-Empfehlung: NO-GO**

Die Anwendung ist ein funktionsreicher Prototyp, aber derzeit nicht für einen öffentlichen Marktstart oder einen Pilotbetrieb mit echten Zahlungen und personenbezogenen Daten verantwortbar. Vier Critical Findings ermöglichen Rollenfälschung, fremde Service-Manipulation, Zahlung ohne Zahlungsnachweis und Umleitung von PayPal-Zahlungen. Buchungs- und Zahlungszustände sind nicht als zulässige Zustandsmaschine modelliert; Parallelität, Idempotenz, Refunds, Chargebacks, Reconciliation und belastbare Auditierung fehlen.

### Wichtigste fünf Risiken

1. Der im Repository bekannte JWT-HMAC-Schlüssel und die aus dem Token übernommene Rolle erlauben ein selbst signiertes Admin-Token für jede aktive eigene E-Mail (FINDING-001).
2. Jeder authentifizierte Nutzer kann fremde Services ändern oder löschen; Preis und Zahlungsempfänger hängen daran (FINDING-002).
3. Ein Kunde kann eine eigene Buchung ohne externen Zahlungsbeleg direkt als bezahlt markieren (FINDING-003).
4. Provider können PayPal-Empfänger und den vermeintlichen Onboarding-Erfolg selbst behaupten (FINDING-004).
5. Fehlende Status-, Sperr- und Idempotenzregeln ermöglichen unzulässige Übergänge, Doppelverarbeitung und inkonsistente Zahlungsstände (FINDING-008/FINDING-009).

### Wichtigste fünf Stärken

1. Passwörter werden mit `BCryptPasswordEncoder` gehasht (`SecurityConfig.java:25-28`, `UserService.java:37-38`).
2. Schreibende Booking-Aktionen des Providers prüfen überwiegend den tatsächlichen Booking-Eigentümer (`BookingService.java:108-128`, `559-563`).
3. Stripe-Webhooks werden kryptographisch mit `Webhook.constructEvent` geprüft (`StripeConnectService.java:177-185`).
4. APIs verwenden überwiegend DTOs statt Entities; Passwort-Hashes und Kartennummern/CVV werden nicht ausgegeben oder gespeichert.
5. Suche ist paginiert und begrenzt die Seitengröße auf 48; JPQL-Parameter verhindern die sichtbare SQL-Injection-Klasse (`ServiceOfferingService.java:97-126`, `ServiceOfferingRepository.java:21-115`).

### Restrisiko

Aktuell **sehr hoch**: 4 Critical, 15 High, 7 Medium und 1 Low. Nach Schließen aller P0/P1-Tickets bleibt vor einer Beta mindestens ein mittleres Restrisiko, das durch externen Penetrationstest, Payment-Provider-Abnahme, Datenschutz-/Marktplatz-Rechtsprüfung, Restore-Test, WCAG-Audit und Lasttest neu zu bewerten ist.

## B. Bewertungsmatrix

Die Gesamtzahl ist die normalisierte Summe: `22 / 85 * 100 = 26`.

| Bereich | Punkte | Begründung |
| --- | ---: | --- |
| Funktionsumfang | 2 | Kern-UI, Services, Buchungen, Chat und Teilzahlungen vorhanden; viele geforderte Prozesse fehlen. |
| Geschäftslogik | 1 | Serverseitige Betragsberechnung vorhanden, aber keine Zustandsmaschine, Preis-Snapshots oder Konkurrenzkontrolle. |
| Authentifizierung | 1 | BCrypt, Verify/Reset vorhanden; fester JWT-Key, schwache Lifecycle-/Abuse-Kontrollen. |
| Autorisierung | 1 | Einige Ownership-Checks gut; mehrere bestätigte BOLA/IDOR-Pfade. |
| Anwendungssicherheit | 1 | DTOs und parametrisierte JPQL positiv; XSS, Secret, Upload-/DoS- und Hardening-Lücken. |
| Zahlungssicherheit | 1 | Stripe-Signatur und serverseitiger Checkout-Betrag vorhanden; kritische Bypass- und Lifecycle-Lücken. |
| Datenschutz | 0 | Kein Export-, Retention-, Consent-, Backup-Lösch- oder Privacy-Audit-Konzept. |
| Marktplatz-Compliance | 1 | Report-/Admin-Grundgerüst; Anbieterklassifizierung, Rechtstexte, Appeals und Audit-Trail fehlen. |
| Datenbank | 1 | UUID/FKs/Auditzeiten teilweise vorhanden; `Double`, DDL-Auto, kaum Constraints/Indizes/Migrationen. |
| Performance | 1 | Pagination und k6-Skripte vorhanden; N+1, unbeschränkte Admin-Listen und keine Messergebnisse. |
| Stabilität | 1 | Transaktionen teilweise vorhanden; externe Calls darin, keine Timeouts/Retry/Outbox/Recovery. |
| Tests | 3 | 58 Backend- und 8 UI-Tests bestehen; kritische Negative-, Concurrent-, Payment- und E2E-Tests fehlen. |
| DevOps | 0 | Keine CI/CD, App-Containerisierung, SBOM, Deploy-/Rollback-Automation oder Umgebungsprofile. |
| Monitoring | 1 | Actuator-Abhängigkeit vorhanden; keine produktive Exposition, Alerts, Tracing oder Business-Metriken. |
| Usability | 3 | Responsive Kernoberflächen, Lade-/Leerzustände und Dialoge vorhanden; Abbruch-/Recovery-Flows lückenhaft. |
| Barrierefreiheit | 1 | Sprache, Labels und semantische Basiselemente teilweise gut; Dialog-/Fokus-/Keyboard-Konformität fehlt. |
| Dokumentation | 3 | Umfangreiches README und Teststrategie; Betriebs-, Security-, Daten- und Incident-Dokumentation fehlt. |

## Architektur- und Implementierungsinventar

| Komponente | Tatsächlicher Stand | Bewertung |
| --- | --- | --- |
| Backend | Java 21, Spring Boot **4.0.5**, Spring Framework 7, Gradle 9.4.1 | Abweichend vom vermuteten Spring Boot 3; Build erfolgreich. |
| Datenbank | PostgreSQL 16 lokal; H2 in Tests; JPA `ddl-auto=update` plus Startzeit-DDL | Nicht reproduzierbar/releasefähig. |
| Frontend | Statisches HTML/CSS/JavaScript für Customer, Provider und Admin | Kein Framework/Build, BASE_URL auf localhost fest verdrahtet. |
| Auth | JWT/HMAC, 24 Stunden, Rollenstrings; BCrypt | Kritisch kompromittierbarer Schlüssel/Lifecycle. |
| Payments | Stripe Connect/Checkout/Webhook, PayPal Order/Capture/Onboarding, Offline-Zahlung | Teilweise; nicht finanzproduktionsreif. |
| Dateien | Base64-Data-URLs in PostgreSQL, externe Bild-/Delivery-URLs | Kein Storage-/Malware-/Quota-Konzept. |
| Benachrichtigung | synchrones SMTP/Console-Mail; In-Memory-SSE-Chat | Nicht resilient oder horizontal skalierbar. |
| Betrieb | Docker Compose nur für PostgreSQL | Keine App-Images, CI/CD, Reverse Proxy oder Produktionsprofile. |

## 1. Funktions- und Prozessprüfung

Legende: **vollständig**, **teilweise**, **fehlerhaft**, **nur Frontend**, **nur Backend**, **nicht vorhanden**, **nicht prüfbar**.

| Prozess | Status | Nachweis und fehlende Schritte |
| --- | --- | --- |
| Registrierung | teilweise | `/api/auth/register`; DTO-Validierung und Rollen-Allowlist, aber keine Passwortregeln, Rate Limits, E-Mail-Normalisierung oder atomare Duplicate-Behandlung. |
| E-Mail-Verifizierung | teilweise | Ablaufender, einmaliger UUID-Token; Login bleibt ohne Verifikation möglich, Default-Mailmodus loggt Links. |
| Login | fehlerhaft | BCrypt/JWT vorhanden; fester Signierschlüssel, Enumeration und kein Brute-Force-Schutz. |
| Logout | nur Frontend | JWT wird nur aus `localStorage` entfernt; kein Widerruf/Blacklist/Rotation. |
| Passwort vergessen | teilweise | Generische Antwort verhindert Enumeration; kein Rate Limit/Hashing des Reset-Tokens. |
| Passwort ändern/resetten | fehlerhaft | Reset ist einmalig/ablaufend; Profil-Update erlaubt Passwortwechsel ohne aktuelles Passwort und ohne Stärkeprüfung. |
| Benutzerprofil | teilweise | Self-ID-Prüfung; zu breites Update-DTO inklusive Rolle, Status und Payment-Empfänger. |
| Anbieterprofil | teilweise | Öffentliche Summary/Avatar; keine Identitäts-/Gewerbe-/Kontaktfelder oder Statusfilterung. |
| Unternehmensprofil/Organisation | nicht vorhanden | `ORGANIZATION_USER` existiert nur in einem ungenutzten Enum; keine Organisation, Mitgliedschaft oder Mandantentrennung. |
| Rollen/Berechtigungen | fehlerhaft | Customer/Provider/Admin-Strings; mehrere BOLA-Pfade, kein Organization User. |
| Service erstellen | teilweise | Provider und verifizierte E-Mail werden geprüft; kaum Feld-/Preis-/Kategorie-/Uploadvalidierung. |
| Service bearbeiten | fehlerhaft | Beliebiger authentifizierter Nutzer kann jede UUID ändern. |
| Service deaktivieren/löschen | fehlerhaft | Admin kann Status setzen; Provider hat kein sicheres Deaktivieren; Delete ohne Ownership und mit FK-Risiko. |
| Kategorien | teilweise | Frontend-Liste; Backend akzeptiert beliebigen String, keine verbotenen Kategorien/Taxonomie. |
| Suche | teilweise | Textsuche über Titel/Beschreibung/Providernamen; kein Suchindex, korrelierte Unterabfragen. |
| Filter/Sortierung/Pagination | teilweise | Kategorie/Ort/Preis/Rating und 3 Sortierungen; Größe max. 48; unbekannte Sortierung fällt still zurück. |
| Provider-Suche | teilweise | Namen über allgemeine Servicesuche und Profil per UUID; keine dedizierte Providerliste/Filter. |
| Service-Detail | vollständig im vorhandenen Scope | Öffentliche Details/Reviews/Galerie; XSS über Kategorie bleibt. |
| Bilder/Uploads | fehlerhaft | Base64 im DB-Textfeld, max. 10 Einträge; keine serverseitige Byte-/Pixel-/Inhalts-/Malware-/Quota-Prüfung. |
| Buchung erstellen | teilweise | Customer aus JWT statt Request-ID; Datum geprüft; Service-/Providerstatus und Verfügbarkeit fehlen. |
| Buchung annehmen/ablehnen | fehlerhaft | Owner geprüft, aber Übergang aus jedem Zustand möglich. |
| Buchungsdatum ändern | nicht vorhanden | Kein Endpunkt/Workflow. |
| Buchung stornieren | nicht vorhanden | Enum/UI-Label existiert, aber kein zulässiger Endpunkt. |
| Stornierungsgebühren | nicht vorhanden | Keine Regel/Abrechnung. |
| Zahlung | fehlerhaft | Stripe/PayPal/Offline vorhanden, aber Critical Bypass und fehlende Zustandsbindung. |
| Provision | teilweise | Prozent/Fixbetrag serverseitig; `Double`, keine Währung/Version/Auditbuchung. |
| Auszahlung Provider | nur Backend/administrativ | Nur manuell gesetzter Settlement-Status, keine Auszahlung oder Provider-Reconciliation. |
| Rückerstattung/Teilrückerstattung | nicht vorhanden | Nur Kommentar `REFUNDED`; keine API/Provider-Aktion. |
| Fehlgeschlagene Zahlung | teilweise | Stripe setzt `FAILED`; Reihenfolge/Recovery/Retry fehlen. |
| Abgebrochene Zahlung | nur Frontend | Toast nach Redirect; Backend-Checkout bleibt ohne Ablaufbereinigung. |
| Chargeback | nicht vorhanden | Kein Dispute-Webhook, Reserve-/Settlement-Lock oder Fallmanagement. |
| Bewertung | fehlerhaft | Nur nach `COMPLETED`, One-to-One beabsichtigt; eingeloggter Nutzer wird nicht als Customer geprüft. |
| Bewertung bearbeiten/melden | teilweise | Bearbeiten/Löschen fehlt; generischer Report-Typ `REVIEW` vorhanden. |
| Benachrichtigungen | teilweise | E-Mail und Chat-SSE; keine persistente Inbox, Zustellstatus, Retry oder Preferences. |
| Support | nicht vorhanden | Nur Text „Support kontaktieren“, kein Ticketprozess. |
| Beschwerden/Konflikte | teilweise | Reports für Service/Review/Provider; kein Booking-Dispute, SLA, Beweise oder Appeal. |
| Kontosperrung | teilweise | Admin Active/Inactive; keine Gründe, Dauer, Session-Widerruf oder Auditierung. |
| Datenexport | nicht vorhanden | Druckbare Rechnung/Zeitliste ist kein Betroffenenexport. |
| Kontolöschung | fehlerhaft | Hard-Delete mit abhängigen FK-Problemen; keine Aufbewahrungs-/Anonymisierungslogik. |
| Administration/Moderation | teilweise | Listen und Statusaktionen; unpaginiert, kein Audit, keine Rollenstaffelung/Appeals. |

## 2. Geschäftslogik und Datenintegrität

- Preise werden bei Stripe/PayPal grundsätzlich serverseitig aus Servicepreis und gemeldeten Stunden berechnet (`BookingService.java:514-536`), aber es gibt keinen unveränderlichen Preis-Snapshot. Eine spätere Serviceänderung ändert den Rechnungsbetrag.
- Clientfelder `customerId` und `providerId` werden im geschützten Create-Pfad ignoriert bzw. durch den JWT-Nutzer ersetzt. Positiv: `createBooking` lädt den Customer per E-Mail (`BookingService.java:63-84`), `createForProviderEmail` den Provider per E-Mail (`ServiceOfferingService.java:55-59`).
- Clientmanipulation bleibt bei Paymentstatus (`mark-paid`), PayPal-Empfänger, Arbeitsstunden, Servicepreis und Statusübergängen möglich.
- Geld wird als `Double` gespeichert (`Booking.java:88-95`, `ServiceOffering.java:28-32`); Booking hat keine Währung. Stripe-Währung kommt separat aus Konfiguration, PayPal aus dem aktuellen Service.
- Es existieren weder `@Version`, pessimistische Locks, Unique-/Exclusion-Constraints für Termine noch Idempotency Keys. Transaktionen allein verhindern Lost Updates nicht.
- Payment-/Booking-Status sind freie Strings, ohne Check Constraints und ohne gekoppelte Invarianten. Abgelehnte oder Pending-Buchungen können bezahlt werden.
- `@Transactional` umschließt externe Stripe-/PayPal-/Mail-Aufrufe, wodurch lange DB-Transaktionen und unklare Teilfehler entstehen (`BookingService.java:41`, `198-271`).
- Zeitstempel sind überwiegend `OffsetDateTime` und PostgreSQL `TIMESTAMP WITH TIME ZONE` (positiv); `LocalDate` wird sinnvoll für reine Arbeitstage genutzt.

## 3. Authentifizierung und Autorisierung

### Endpunkt-Berechtigungsmatrix

`Öffentlich` gilt für alle Rollen und anonyme Nutzer. `Eigen` bedeutet, dass ein Objektbesitz-Check im Service/Controller vorhanden ist. Organization User ist nicht implementiert.

| Endpunkt | Customer | Provider | Organization User | Admin | Objektbesitz geprüft | Ergebnis |
| --- | --- | --- | --- | --- | --- | --- |
| `POST /api/auth/register` | öffentlich | öffentlich | nicht vorhanden | öffentlich | n/a | Teilweise; Abuse-Kontrollen fehlen. |
| `GET /api/auth/verify-email` | öffentlich | öffentlich | nicht vorhanden | öffentlich | Token | Teilweise. |
| `POST /api/auth/forgot-password` | öffentlich | öffentlich | nicht vorhanden | öffentlich | Tokenziel serverseitig | Positiv generische Antwort; kein Limit. |
| `POST /api/auth/resend-verification` | öffentlich | öffentlich | nicht vorhanden | öffentlich | Tokenziel serverseitig | Kein Limit. |
| `POST /api/auth/reset-password` | öffentlich | öffentlich | nicht vorhanden | öffentlich | Reset-Token | Keine Passwortregeln/Revocation. |
| `POST /api/auth/login` | öffentlich | öffentlich | nicht vorhanden | öffentlich | n/a | Kritisch wegen JWT-Key/Enumeration. |
| `POST /api/users` | ja | ja | nicht vorhanden | ja | nein | Unerwarteter zweiter Create-Pfad. |
| `GET/PUT/DELETE /api/users/{id}` | eigen | eigen | nicht vorhanden | nur eigen | ja | Self-Check gut; Update-DTO zu breit/Delete fehlerhaft. |
| `GET /api/services` | öffentlich | öffentlich | nicht vorhanden | öffentlich | n/a | Korrekt öffentlich, paginiert. |
| `GET /api/services/{id}` | öffentlich | öffentlich | nicht vorhanden | öffentlich | n/a | Korrekt öffentlich. |
| `GET /api/services/{id}/image` | öffentlich | öffentlich | nicht vorhanden | öffentlich | n/a | Öffentlich beabsichtigt. |
| `POST /api/services` | nein | ja | nicht vorhanden | nein | Principal als Provider | Gut geroutet; Validierung lückenhaft. |
| `GET /api/services/my` | Controller verlangt Principal | ja | nicht vorhanden | ja/leer | Principal | Customer-Rolle nicht explizit gesperrt. |
| `PUT /api/services/{id}` | **ja** | **ja** | nicht vorhanden | ja | **nein** | **Critical BOLA**. |
| `DELETE /api/services/{id}` | **ja** | **ja** | nicht vorhanden | ja | **nein** | **Critical BOLA**. |
| `GET /api/providers/{id}` | öffentlich | öffentlich | nicht vorhanden | öffentlich | n/a | Erwartet öffentlich; Typ/Status ungeprüft. |
| `GET /api/providers/{id}/avatar` | öffentlich | öffentlich | nicht vorhanden | öffentlich | n/a | Erwartet öffentlich. |
| `POST /api/providers/me/paypal/*` | nein | ja | nicht vorhanden | nein | Principal | Rolle gut; Rückkehrdaten nicht vertrauenswürdig. |
| `POST /api/providers/me/stripe/*` | nein | ja | nicht vorhanden | nein | Principal | Rolle/Eigentümer gut. |
| `POST /api/bookings` | ja | nein | nicht vorhanden | nein | Customer aus Principal | Positiv; Availability fehlt. |
| `GET /api/bookings/customer/me` | ja | nein | nicht vorhanden | nein | Principal | Gut. |
| `GET /api/bookings/provider/me` | nein | ja | nicht vorhanden | nein | Principal | Gut. |
| `GET /api/bookings/customer/{id}` | **ja** | **ja** | nicht vorhanden | ja | **nein** | **High IDOR**. |
| `GET /api/bookings/provider/{id}` | **ja** | **ja** | nicht vorhanden | ja | **nein** | **High IDOR**. |
| `PUT /api/bookings/{id}/status` | nein | Provider | nicht vorhanden | nein | ja | Owner gut, Zustandsübergang fehlerhaft. |
| `PUT /api/bookings/{id}/work` | nein | Provider | nicht vorhanden | nein | ja | Owner gut, Status/Maximalwerte fehlen. |
| `POST /api/bookings/{id}/time-entries` | nein | Provider | nicht vorhanden | nein | ja | Owner gut, keine Obergrenzen/Idempotenz. |
| `POST /api/bookings/{id}/delivery` | nein | Provider | nicht vorhanden | nein | ja | Owner gut, URL/Status ungeprüft. |
| `GET /api/bookings/{id}/delivery/open|url` | eigen | eigen | nicht vorhanden | nein | ja | Zugriff gut; Provider-gesteuerter externer Redirect. |
| `POST /api/bookings/{id}/checkout` | eigen | nein | nicht vorhanden | nein | ja | Ownership gut; Bookingstatus fehlt. |
| `POST /api/bookings/{id}/paypal/capture` | eigen | nein | nicht vorhanden | nein | ja | Ownership/Order-ID; kein Provider-Antwortabgleich/Idempotenz. |
| `POST /api/bookings/{id}/mark-paid` | eigen | nein | nicht vorhanden | nein | ja | **Critical Business-Flow-Bypass**. |
| `POST /api/bookings/{id}/record-payment` | nein | Provider | nicht vorhanden | nein | ja | Owner gut; beliebige Statuslage. |
| `POST /api/reviews` | ja | ja | nicht vorhanden | ja | **nein** | Authentifizierter Fremder kann Customer imitieren. |
| `GET /api/reviews/booking/{id}` | öffentlich | öffentlich | nicht vorhanden | öffentlich | n/a | Öffentlich beabsichtigt. |
| `GET /api/reviews/service/{id}` | öffentlich | öffentlich | nicht vorhanden | öffentlich | n/a | Öffentlich beabsichtigt. |
| `POST /api/reports` | ja | ja | nicht vorhanden | ja | Reporter aus Principal | Ziel existiert nicht zwingend. |
| `GET/POST /api/messages/booking/{id}` | eigen | eigen | nicht vorhanden | nein | ja | Ownership gut. |
| `GET /api/messages/booking/{id}/stream?token=` | Tokeninhaber/eigen | Tokeninhaber/eigen | nicht vorhanden | ggf. nein | ja | JWT in URL ist unsicher. |
| `GET /api/weather/current|forecast` | öffentlich | öffentlich | nicht vorhanden | öffentlich | n/a | Externe Kosten/DoS ohne Limit. |
| `POST /api/stripe/webhook` | Stripe-signiert | Stripe-signiert | n/a | Stripe-signiert | Signatur, Booking-Metadatum | Signatur positiv; Replay/Ordering fehlen. |
| `GET/PATCH /api/admin/**` | nein | nein | nicht vorhanden | ja | Adminfunktion | Rollen-Gate gut; Audit/Pagination fehlen. |

### Authentifizierungsbewertung

- Passwort-Hashing: umgesetzt und positiv.
- Passwortanforderungen: nicht vorhanden (`CreateUserRequest.java:8`, `ResetPasswordRequest` in `AuthController.java:35`).
- Enumeration: Login unterscheidet „User existiert nicht“ und „Falsches Passwort“ (`AuthController.java:87-96`); Forgot/Resend sind dagegen generisch.
- Brute Force/Rate Limiting: nicht vorhanden.
- Refresh Token/Widerruf/Rotation/JTI: nicht vorhanden; Access Token 24 Stunden (`JwtUtil.java:16-25`).
- Gesperrte Nutzer: pro Request gegen DB-Status geprüft (positiv), Rolle dagegen aus dem Token statt der DB.
- CORS: `*` für alle Origins/Headers (`SecurityConfig.java:34-39`); für Produktion zu weit.
- CSRF: bei reinem Bearer-Header vertretbar deaktiviert; Query-JWT und künftige Cookie-Auth erfordern Neubewertung.

## 4. Anwendungssicherheit und OWASP-Mapping

| Standardbereich | Ergebnis |
| --- | --- |
| OWASP Top 10 A01 / API1, API3, API5 | Bestätigte BOLA/Function-Level-Fehler bei Services, Bookings und Reviews. |
| A02 Security Misconfiguration | CORS `*`, Swagger/H2-Allowlist, `show-sql=true`, lokale Defaults, kein Produktionsprofil. |
| A03 Supply Chain | Aktuelle OSV-Treffer, keine CI-SCA/SBOM/Signierung; npm-Momentaufnahme sauber. |
| A04 Cryptographic Failures | Fester JWT-Key; BCrypt ist positiv. Keine dokumentierte Verschlüsselung sensibler DB-Felder. |
| A05 Injection | JPQL ist parametriert; gespeicherte DOM-Injection/XSS über Kategorie bestätigt. Keine OS-Command-Sinks gefunden. |
| A06 Insecure Design | Fehlende Statusmaschinen, Idempotenz, Refund-/Dispute- und Availability-Modelle. |
| A07 Authentication Failures | Kein Rate Limit/Lockout/MFA/Token-Revocation; Enumeration. |
| A08 Integrity Failures | Webhook-/Checkout-Replay und clientvertrauensbasiertes PayPal-Onboarding. |
| A09 Logging/Alerting | Keine Security-/Business-Auditierung oder Alerts. |
| A10 Exceptional Conditions | Externe Fehlerdetails werden teils als 400 ausgegeben; kein Recovery/Outbox. |
| API4 Resource Consumption | Wetterproxy, Base64-Bilder, Admin-Listen und Bilddecoding ohne harte Ressourcenlimits. |
| API6 Sensitive Business Flows | Registrierung, Login, Booking, Report, Checkout ohne Rate-/Bot-/Idempotenzschutz. |
| API10 Unsafe Consumption | Externe APIs ohne Timeouts/Resilience; Provider-Rückkehrdaten werden vertraut. |

Nicht gefunden wurden dynamische SQL-Konkatenation, `ProcessBuilder`, `Runtime.exec`, serverseitiges Abrufen nutzerdefinierter URLs oder Java-native Deserialisierung. Diese Negativbefunde sind keine Sicherheitsgarantie und ersetzen keinen DAST/Pentest.

## 5. Datei- und Bilduploads

Die Anwendung hat keinen echten Uploadservice. Bilder werden clientseitig komprimiert und als Data-URL im JSON/DB-Text gespeichert (`utils.js:44-82`; `ServiceOffering.imageUrl/imageUrls`; `User.profileImageUrl`; `ChatMessage.imageDataUrl`).

| Kontrolle | Stand |
| --- | --- |
| Anzahl | Maximal 10 Servicebilder serverseitig gezählt; Avatar/Chat jeweils 1. |
| Dateigröße | Nur Chat-Stringlänge 1,5 Mio.; kein Limit für Service/Avatar und kein HTTP-Body-Limit im Projekt. |
| MIME/Inhalt | Data-URL-Präfix und kleine Allowlist beim Ausliefern; kein Magic-Byte-/vollständiger Decode-Check beim Speichern. |
| SVG/HTML | Decoder liefert SVG/HTML nicht aus; beliebige Remote-URLs und beliebige Strings werden dennoch gespeichert/als Client-URL ausgegeben. |
| Malware/Metadaten/EXIF | Nicht vorhanden; Browser-Canvas entfernt bei lokalem Re-Encoding oft Metadaten, ist aber keine Serverkontrolle. |
| Namen/Traversal | Kein Filesystempfad; damit kein klassischer Path Traversal. Chat-Dateiname wird gespeichert, aber escaped angezeigt. |
| Random Storage/ACL/Löschung/Quota | Nicht vorhanden. |
| Thumbnails | JPEG/PNG on-demand bis 640px; vor dem Decode keine Pixel-/Memory-Grenze, GIF/WebP roh. |

## 6. Zahlungs- und Provisionslogik

Stripe Checkout nutzt Destination Charges mit Application Fee (`StripeConnectService.java:113-170`) und validiert Webhook-Signaturen. PayPal legt serverseitig Orderbetrag, Payee und Plattformfee an (`PayPalService.java:136-171`, `403-429`). Kartennummern oder CVV werden nicht verarbeitet; gespeichert werden nur Stripe-/PayPal-IDs. Das reduziert, beendet aber nicht automatisch den PCI-DSS- und Datenschutz-Prüfumfang.

Launch-blockierend fehlen bzw. sind fehlerhaft:

- Payment darf nur aus einem definierten, akzeptierten Bookingzustand starten.
- `/mark-paid` muss entfernt oder ausschließlich provider-signiertem Testcode außerhalb Produktion vorbehalten werden.
- PayPal-Onboarding muss ausschließlich aus serverseitig verifiziertem Providerstatus stammen.
- Booking muss Betrag, Währung, Preisgrundlage, Gebührenversion, Steuer-/Rechnungsdaten unveränderlich snapshotten. Für den PayPal-Teilpfad werden seit 2026-07-15 erwarteter Capture-Betrag, ISO-Währung und Payee-Merchant-ID beim Checkout gespeichert; der vollständige fachliche Preis-/Gebühren-/Steuer-/Rechnungssnapshot bleibt offen.
- Payment Attempts, eindeutige Provider-Transaktions-IDs, Idempotency Keys, Event-Inbox und atomare Statusübergänge fehlen.
- Stripe `checkout.session.completed` wird nicht gegen die gespeicherte Session, Betrag, Währung, Paymentstatus und Connected Account geprüft; alte/fremde Reihenfolgen können den Zustand überschreiben (`StripeConnectService.java:205-243`).
- PayPal Capture fordert die vollständige Repräsentation an und prüft Order-ID, Booking-Referenzen, erwarteten Betrag, Währung und Payee gegen die beim Checkout gespeicherten PayPal-Sollwerte, bevor `PAID` gesetzt wird. Offen bleiben die vollständige Preisgrundlage, versionierte Migration sowie PostgreSQL-/PayPal-Sandbox-Verifikation.
- Refund, Teilrefund, Chargeback, fehlgeschlagene Auszahlung, Gebührenrücknahme, Reconciliation und Recovery Job fehlen.
- Settlement ist nur ein manuell änderbarer String; kein Ledger/Audit-Log.

## 7. Datenschutz und technische Compliance

| Datenart | Zweck | Speicherort | Zugriff | Aufbewahrung | Löschmechanismus | Risiko |
| --- | --- | --- | --- | --- | --- | --- |
| E-Mail, Name, Rolle, Status | Konto/Kommunikation | `users` | Self, Admin; Name öffentlich bei Provider/Reviews | undefiniert | Hard-Delete, fehleranfällig | Hoch |
| Passwort-Hash, Verify-/Reset-Token | Authentifizierung | `users` | Backend/DB-Admin | Token bis Nutzung/Ablauf, aber kein Cleanup | Nullsetzen bei Nutzung | Hoch; Token im Klartext |
| Avatar/Base64-Bild | Profil | `users.profile_image_url` | Self/Admin, Provideravatar öffentlich | undefiniert | mit User-Hard-Delete | Hoch; unbegrenzt/Metadaten |
| IBAN, PayPal-/Stripe-IDs | Auszahlung/Zahlung | `users` | Self und Admin-Listen | undefiniert | mit User-Hard-Delete | Sehr hoch; keine Feldverschlüsselung |
| Serviceinhalt/Bilder/Ort/Preis | Marktplatz | `service_offerings` | öffentlich/Admin/Provider | undefiniert | Hard-Delete | Mittel bis hoch |
| Bookingdatum, Parteien, Notizen, Status | Vertragserfüllung | `bookings` | Parteien; durch IDOR jeder Login | undefiniert | fehleranfälliger Hard-Delete | Sehr hoch |
| Zahlungs-/Settlementmetadaten und Beträge | Abrechnung | `bookings` | Parteien/Admin; durch IDOR exponiert | undefiniert | mit Booking | Sehr hoch |
| Zeitbuchungen | Leistungsnachweis | `time_entries` | Parteien/Admin indirekt | undefiniert | kein eigener Mechanismus | Hoch |
| Chattext/-bilder | Kommunikation/Beweis | `chat_messages` | Parteien | undefiniert | kein eigener Mechanismus | Hoch |
| Reviews | Vertrauen | `reviews` | öffentlich | undefiniert | kein Endpunkt | Mittel |
| Reports/Beschwerden | Moderation | `reports` | Reporter/Admin | undefiniert | kein Endpunkt | Hoch |
| Logs inkl. Stacktraces/Mails | Betrieb/Fehler | stdout/lokale Laufzeit | Betreiber | undefiniert | nicht definiert | Hoch; PII/Tokens möglich |

Nicht vorhanden sind Consent-/Cookie-Management, Zwecke/Versionen von Rechtstexten, Widerruf, Betroffenenauskunft, maschinenlesbarer Export, Berichtigungsaudit, Retention-/Legal-Hold-Regeln, Pseudonymisierung, Adminzugriffsaudit, Backup-Löschung, Drittanbieterregister und Privacy-Incident-Runbook. Es gibt im statischen Frontend kein erkennbares Tracking; das ist positiv, ersetzt aber kein Consent-Konzept für künftige Dienste.

## 8. Marktplatz- und Verbraucheranforderungen

| Anforderung | Stand |
| --- | --- |
| Private/gewerbliche Anbieter, Identifikation, Kontakt | nicht vorhanden |
| Impressum, AGB, Datenschutz, Rücktritt/Storno | nicht vorhanden |
| Preis-/Gebührenklarheit | Stundenpreis und Paymentmethoden sichtbar; Währung/Steuer/Total vor Vertrag nicht belastbar snapshotbar |
| Plattformprovision transparent | Provider/Admin sehen Beträge; Customer nicht konsistent, keine versionierte Gebührenregel |
| Rankingtransparenz/bezahlte Platzierung | Trust-Score-Formel im Code, aber keine Nutzererklärung; bezahlte Platzierung nicht modelliert |
| Verifizierte Bewertungen | Bookingbindung beabsichtigt; Auth-Lücke erlaubt fremde Abgabe |
| Meldung Angebote/Bewertungen | vorhanden als generischer Report |
| Moderation/Sperrung | Status-Grundgerüst vorhanden; kein Entscheidungsgrund, Audit, SLA oder Appeal |
| Verbotene Kategorien/Sicherheitsprüfung | nicht vorhanden |
| Beschwerdeverfahren/Verantwortungstrennung | nicht vorhanden |

Vor juristischer Abnahme müssen Rechtstextversionen/Einwilligungsnachweise, Anbieterklassifikation und -identität, vollständige Preis-/Steuer-/Stornoanzeige, Rankinginformation, Notice-and-Action, Begründung/Appeal und manipulationssicherer Moderations-Audit-Trail technisch unterstützt werden.

## 9. API-Qualität

Positiv sind JSON-DTOs, UUIDs, Bean Validation an einigen Requests, Pagination der öffentlichen Suche, OpenAPI-Grundkonfiguration und ein zentraler Exception Handler. Negativ sind keine `/v1`-Versionierung, uneinheitliche `200` statt `201`, `400` auch für Not Found/Conflict/interne Laufzeitfehler, teils Plaintext- und teils JSON-Fehler, unvollständige Validation, freie String-Enums, keine Idempotenz-/Rate-/Correlation-Header, keine Deprecation-Strategie und unpaginierte private/Admin-Listen. OpenAPI markiert global Bearer-Auth, obwohl öffentliche Endpunkte existieren, und dokumentiert Ownershipregeln nicht.

## 10. Datenbank und Migrationen

- `spring.jpa.hibernate.ddl-auto=update` und imperatives DDL in `SchemaMigrationConfig.migrate()` sind weder versioniert noch atomar rollbackfähig.
- `tryExecute` verschluckt Schemafehler (`SchemaMigrationConfig.java:101-106`).
- Foreign Keys entstehen teils durch JPA bzw. explizites DDL; `reports.target_id` ist bewusst polymorph und hat keine FK-/Existenzprüfung.
- Status, Geld, Währung, Ratings, Stunden und Gebühren besitzen keine Check Constraints; es gibt kein `@Version`.
- Das Löschverfahren berücksichtigt Reviews, Chat, Time Entries und Reports nicht zuverlässig.
- PostgreSQL-Backup-/Restore, RPO/RTO und Migrationstest fehlen.

Konkret erforderliche Indizes, nach Query-/FK-Nutzung zu validieren:

1. `bookings(customer_id)` und `bookings(service_offering_id)`.
2. partiell/komposit `bookings(service_offering_id, booking_date)` für aktive Zustände; bei exklusiven Slots zusätzlich DB-seitige Exclusion-/Unique-Regel.
3. `service_offerings(provider_id)` sowie `(status, category, price)`; für `%text%` PostgreSQL `pg_trgm`/GIN auf den gewählten Suchfeldern.
4. eindeutig `reviews(booking_id)` und `chat_messages(booking_id, created_at)`.
5. `time_entries(booking_id, work_date DESC, created_at DESC)` und `time_entries(provider_id)`.
6. `reports(status, created_at DESC)`, `reports(reporter_id)`, `reports(target_type, target_id)`.
7. eindeutige partielle Indizes auf nicht-null Stripe Session/PaymentIntent, PayPal Order/Capture und Stripe Connected Account IDs.

Keine nachweisbar überflüssigen expliziten Indizes sind im Repository vorhanden; das Problem ist das Fehlen eines kontrollierten Schemas und belastbarer Query-Pläne.

## 11. Performance und Skalierbarkeit

### Erwartetes Verhalten ohne Messgarantie

| Gleichzeitige Nutzer | Einschätzung |
| ---: | --- |
| 10 | Lokale Kernsuche wahrscheinlich nutzbar; externe API-/Mail-Latenz kann Schreibflows blockieren. |
| 100 | N+1 und unbeschränkte Admin-/Booking-Listen werden sichtbar; Pool-/Timeoutverhalten unbekannt. |
| 1.000 | Ohne Cache, horizontale SSE-/Storage-Lösung, Queryoptimierung und Backpressure nicht verantwortbar. |
| 10.000 | Aktuelle Architektur/DB-/In-Memory-SSE-/Base64-Speicherung nicht geeignet. |

N+1-Nachweise: pro Service werden Rating und Count separat geladen (`ServiceOfferingService.java:189-198`), pro Booking Time Entries (`BookingService.java:371-413`, `602-611`), Admin ruft unbeschränkt `findAll()` und pro Service weitere Detailqueries auf (`AdminService.java:39-99`).

### Zielwerte für einen ersten kontrollierten Marktstart

- Öffentliche Suche: p95 ≤ 500 ms, p99 ≤ 1.000 ms, Fehlerquote < 0,5 % bei 100 RPS gemischter Suche.
- Login/Profile: p95 ≤ 400 ms ohne Mailversand; 99,9 % korrekte Authentscheidung.
- Booking Create/Transition: p95 ≤ 750 ms ohne externen Call; 0 Doppelbuchungen bei 100 parallelen Requests auf denselben Slot.
- Payment-API: interne Antwort p95 ≤ 1.000 ms plus Providerlatenz; 0 doppelte Charges/Payouts bei Replay/Timeout.
- Upload: max. 10 MB Request, max. 5 MB/Bild und definierte Pixelgrenze nach Produktentscheidung; keine ungeprüfte DB-Base64-Ablage.
- Verfügbarkeit: initial 99,5 % monatlich, RPO ≤ 15 Minuten, RTO ≤ 2 Stunden; vor Launch messen und freigeben.

### Konkreter Testplan

| Test | Szenario | Tool/Abnahme |
| --- | --- | --- |
| Smoke | Register→Verify→Login→Service→Booking→Accept→echter Sandbox-Checkout→Webhook→Review | Playwright + Testcontainers/WireMock; 100 % Erfolg |
| Load | 70 % Suche, 15 % Login/Profile, 10 % Booking Reads, 5 % Writes | k6/Gatling; obige p95/Fehlerraten |
| Stress | Stufen bis Sättigung; Pool, DB, CPU, Heap beobachten | k6; dokumentierter Breakpoint, keine Datenkorruption |
| Spike | 10→500 VUs in 10 s, dann Recovery | k6; Recovery < 2 min, keine Queue-/Pool-Leaks |
| Soak | 24 h bei 30–50 % Ziellast | k6; kein Heap-/Connection-/SSE-Leak, stabile p95 |
| Concurrent Booking | 100 Requests, gleicher Service/Slot/Idempotency-Key-Kombinationen | Gatling/JUnit+PostgreSQL; genau zulässige Kapazität |
| Concurrent Payment | Doppelklick, Timeout, gleicher/verschiedener Key, Webhook Replay/Out-of-order | Provider Sandbox + WireMock; genau ein Charge/Ledger-Effekt |

## 12. Zuverlässigkeit und Fehlertoleranz

Es fehlen explizite Connect-/Read-Timeouts (`new RestTemplate()` in `ServiceRateBackendApplication.java:15-19`), Retry mit Backoff, Circuit Breaker, Bulkhead, Outbox, Event Inbox, DLQ, Recovery/Reconciliation Jobs, Readiness-Probe und Graceful-Shutdown-Konfiguration. SMTP, PayPal/Stripe und Mailversand laufen synchron; teilweise innerhalb einer DB-Transaktion. Bei Mailfehlern sind Verhalten und Fachtransaktion inkonsistent (`send` wirft für Verify/Reset, `trySend` verschluckt für andere Mails). Ein Prozessneustart verliert alle SSE-Emitter. DB-, Storage-, Zertifikat-, Disk-full-, Migration- und Deployment-Fehler besitzen keine Runbooks oder getesteten Recoverypfade.

## 13. Logging, Monitoring und Auditierbarkeit

Vorhanden sind Standard-SLF4J-/Spring-Logs und Actuator als Dependency. Es fehlen strukturierte JSON-Logs, Correlation/Trace IDs, zentrale Sammlung, Prometheus-Export, Tracing, Businessmetriken, produktive Health-/Readiness-Freigabe, Alarmregeln sowie ein unveränderlicher Security-/Admin-/Payment-/Moderations-Audit-Log.

Mindestens erforderliche Alerts:

1. Auth-Fehlerrate, Login-Brute-Force pro Account/IP, JWT-Signaturfehler.
2. 5xx-/Timeout-/Pool-Sättigung und p95/p99 je kritischem Endpunkt.
3. Payment Failed/Unknown, Webhook-Signaturfehler, Replay, Backlog und Statusdivergenz.
4. Booking-Konflikte, ungewöhnliche Statusübergänge und Doppelbuchungsversuche.
5. DB-Verbindungen/Deadlocks/Replication/Backupalter/Restore-Testalter/Disk.
6. SMTP-/Provider-/Storage-Fehlerrate und Circuit-Status.
7. Admin- und Moderationsaktionen außerhalb Baseline; massenhafte Datenzugriffe/Exports.
8. Zertifikatsablauf 30/14/7 Tage, Deployment-/Migration-/Smoke-Testfehler.

## 14. Tests und CI/CD

Die bestehenden Tests sind schnell verständlich und decken mehrere positive Security-/Ownership-Fälle ab. Die 58 Backend-Tests und 8 UI-Tests bestehen. Die UI-Tests mocken jedoch die API vollständig; der deklarierte XSS-Test prüft nur eine escaped Description, nicht die ungeescapte Category (`customer-usability.spec.js:3-34`, `134-145`). Stripe-Webhooktests prüfen Controllerdelegation, nicht echte Eventzustände. Es gibt keine PostgreSQL-Testcontainers, Migration-, Refund-, Chargeback-, Replay-, Concurrent-, Restore-, Accessibility-, Contract- oder echte End-to-End-Payment-Tests. k6 deckt primär GET-Suche bis 150 VUs ab und wurde nicht gegen eine laufende Umgebung gemessen.

Es gibt keine CI/CD-Datei. Damit fehlen reproduzierbare Gates für Tests, Secret Scan, SAST, SCA, Container Scan, SBOM, Artefaktsignatur, Migration, Deploy, Approval, Smoke und Rollback. Gradle-/npm-Versionen sind gepinnt; das ist positiv.

## 15. Benutzerfreundlichkeit und Barrierefreiheit

Positiv: Responsive Desktop-/Mobile-Grundlayout, deutsche `lang`-Attribute, viele sichtbare Labels, Loading-/Empty-States, Bestätigungsdialog vor Service-Löschung und verständliche Toasts. Negativ: Overlays/Drawer haben weder `role="dialog"` noch `aria-modal`, keine Fokussetzung/-falle/-rückgabe, keine globale Escape-Behandlung; klickbare `article`-Elemente sind nicht tastaturfokussierbar. Tabs haben keine Tab-Semantik/States. Toasts besitzen keine Live-Region. Status/Fehler sind häufig nur visuell; Kontrast, 200-%-Zoom, Reflow, Screenreader, Target Size, Reduced Motion und vollständige Keyboardbedienung wurden nicht automatisiert oder manuell nachgewiesen. WCAG 2.2 AA ist daher nicht erfüllt/nicht abnahmefähig.

## 16. Betriebs- und Releasebereitschaft

Vorhanden: lokales PostgreSQL Compose, Umgebungsvariablen für externe Secrets, Wrapper/Lockfile und README-Startanleitung. Nicht vorhanden: Produktionsprofil, App-/Frontend-Container, Reverse Proxy/TLS, Secret Manager, Network Policies, CI/CD, getrennte Umgebungen, Backup/Restore, Monitoring/Alerts, Runbooks, Incident Response, Rollback, Wartungsmodus, Feature Flags, Statusseite, Support-Rota, Verantwortlichkeiten, Notfallkontakte und Release-Freigaben. Lokale Defaults (`postgres/postgres`, öffentlich gebundener DB-Port, Mail `console`, PayPal `sandbox`, `show-sql=true`, Swagger/H2-Allowlist) dürfen nicht in Produktion gelangen.

## C. Findings

Die Detailfindings werden zusätzlich in `docs/SECURITY_FINDINGS.md` gespiegelt. Die folgenden Einträge sind die maßgebliche, deduplizierte Liste.

### FINDING-001: Fester JWT-Schlüssel ermöglicht Rollen- und Adminfälschung

* Kategorie: Authentifizierung / Kryptographie / OWASP A04, A07
* Schweregrad: Critical
* Launch-Blocker: Ja
* Sicherheit: Vertraulichkeit, Integrität und vollständige Account-/Admin-Kontrolle
* betroffene Rollen: alle, insbesondere Platform Admin
* betroffene Dateien: `config/JwtUtil.java:12-25`; `config/JwtAuthenticationFilter.java:43-60`
* betroffene Endpunkte: alle authentifizierten Endpunkte, besonders `/api/admin/**`
* Beschreibung: Der HMAC-Schlüssel steht im Repository. Der Filter prüft in der DB nur, ob die Token-E-Mail aktiv ist, übernimmt die Autorität aber aus dem selbst signierten `accountType`-Claim.
* konkreter Nachweis: `SECRET` ist konstant; Zeile 58 baut `ROLE_` direkt aus dem Claim, ohne DB-Rollenabgleich.
* realistisches Angriffs- oder Fehlerszenario: Ein registrierter aktiver Customer signiert mit dem bekannten Key ein Token für seine E-Mail und `accountType=ADMIN` und liest/ändert Admin-Daten.
* Auswirkung: Vollständige Kompromittierung von Nutzern, Services, Reports und Settlementstatus.
* empfohlene Lösung: Starkes rotierbares Secret/Keypair aus Secret Manager; `iss/aud/jti/kid`; Rollen serverseitig aus DB/Authorization Service; kompromittierten Key rotieren und alle Tokens widerrufen.
* empfohlene Tests: selbst signiertes Token; fremder Algorithmus; alter/rotierter Key; Claim-/DB-Rollendifferenz; Admin-E2E.
* Abhängigkeiten: Secret Management, Token-Version/Revocation.
* geschätzter Aufwand: M
* Akzeptanzkriterien: Kein Schlüssel im Repository; gefälschte/alte Tokens werden abgewiesen; Autorität entspricht ausschließlich aktueller serverseitiger Rolle.

### FINDING-002: Service-Update und -Delete ohne Rolle oder Objektbesitz

* Kategorie: Autorisierung / BOLA / OWASP A01, API1, API5
* Schweregrad: Critical
* Launch-Blocker: Ja
* Sicherheit: Integrität, Verfügbarkeit, finanzielle Integrität
* betroffene Rollen: Customer, Provider, Admin
* betroffene Dateien: `config/SecurityConfig.java:74-77`; `controller/ServiceOfferingController.java:73-85`; `service/ServiceOfferingService.java:158-176`
* betroffene Endpunkte: `PUT /api/services/{id}`, `DELETE /api/services/{id}`
* Beschreibung: Beide Endpunkte fallen auf `authenticated()` zurück; Controller/Service prüfen weder Provider-Rolle noch Eigentümer.
* konkreter Nachweis: `updateService` lädt nur die Pfad-ID und überschreibt u. a. Preis/Bilder; `delete` ruft direkt `deleteById` auf.
* realistisches Angriffs- oder Fehlerszenario: Ein Customer setzt einen fremden Preis oder XSS-Category, ersetzt Bilder oder löscht das Angebot.
* Auswirkung: Marktplatzmanipulation, Zahlungspreismanipulation, Datenverlust und Rufschaden.
* empfohlene Lösung: `hasRole(PROVIDER)` plus zentraler Ownership-Check; Adminaktion separat; Update mit erlaubten Feldern, Optimistic Lock und Audit.
* empfohlene Tests: Customer/anderer Provider/Admin/Owner je PUT/DELETE; Race mit Checkout; gelöschter Service mit Bookings.
* Abhängigkeiten: Rollenmodell, Service-Lifecycle, Preis-Snapshot.
* geschätzter Aufwand: S
* Akzeptanzkriterien: Nur Owner darf fachlich zulässige Änderungen/Deaktivierung; Fremdzugriff liefert 403 ohne Existenzleck; bestehende Bookings bleiben konsistent.

### FINDING-003: Kunde kann Zahlung ohne Providerbeleg als bezahlt markieren

* Kategorie: Payments / Sensitive Business Flow
* Schweregrad: Critical
* Launch-Blocker: Ja
* Sicherheit: finanzielle Integrität, Betrugsschutz
* betroffene Rollen: Customer, Provider, Admin
* betroffene Dateien: `config/SecurityConfig.java:71`; `controller/BookingController.java:100-105`; `service/BookingService.java:274-291`
* betroffene Endpunkte: `POST /api/bookings/{id}/mark-paid`
* Beschreibung: Der eigene Customer darf `PAID` setzen; es gibt keine Webhook-/Providertransaktion oder nur-Test-Absicherung.
* konkreter Nachweis: Zeilen 283-288 setzen Status, Beträge, Settlement und `paidAt` unmittelbar.
* realistisches Angriffs- oder Fehlerszenario: Customer ruft den Endpunkt auf und erhält eine digitale Delivery, weil Delivery nur `paymentStatus=PAID` prüft.
* Auswirkung: Unbezahlte Leistung/Downloads, falsche Umsätze und Provider-Auszahlungen.
* empfohlene Lösung: Endpunkt entfernen; `PAID` nur aus verifiziertem Provider-Event oder kontrollierter Admin-Offline-Zahlung mit Vier-Augen-Audit.
* empfohlene Tests: direkter Customeraufruf; Replay; abgelehnte/stornierte Buchung; Delivery vor echtem Payment.
* Abhängigkeiten: Payment State Machine, Webhook Inbox/Ledger.
* geschätzter Aufwand: S
* Akzeptanzkriterien: Kein Customerpfad setzt `PAID`; jeder Paid-Zustand referenziert eine verifizierte eindeutige Transaktion.

### FINDING-004: PayPal-Onboarding und Zahlungsempfänger sind clientmanipulierbar

* Kategorie: Payments / Mass Assignment / Unsafe Consumption
* Schweregrad: Critical
* Launch-Blocker: Ja
* Sicherheit: finanzielle Integrität, Auszahlungsschutz
* betroffene Rollen: Provider, Customer
* betroffene Dateien: `dto/UpdateUserRequest.java:10-15`; `service/UserService.java:128-141`; `service/ProviderPayPalOnboardingService.java:74-109`; `frontend/provider-dashboard.js:218-234,433-445`; `service/PayPalService.java:432-444`
* betroffene Endpunkte: `PUT /api/users/{id}`, `POST /api/providers/me/paypal/onboarding-return`, PayPal Checkout
* Beschreibung: Provider dürfen Merchant-ID/E-Mail direkt speichern und der Return-Endpunkt vertraut behaupteten Permission-/E-Mail-Flags. Diese Werte werden als Payee verwendet.
* konkreter Nachweis: Das Frontend sendet hart `permissionsGranted:true` und `isEmailConfirmed:true`; Backend verifiziert diese Angaben nicht bei PayPal.
* realistisches Angriffs- oder Fehlerszenario: Kontoübernahme oder böswilliger Provider setzt eine fremde Empfänger-ID und markiert sie als verbunden; Customerzahlung fließt dorthin.
* Auswirkung: Fehlgeleitete Gelder, Fraud, Rückabwicklung und Haftungsrisiko.
* empfohlene Lösung: Paymentfelder aus Profil-DTO entfernen; Onboardingstate serverseitig zufällig/bindend speichern; Merchantstatus ausschließlich providerseitig abrufen und Payee unveränderlich auditieren.
* empfohlene Tests: manipulierte Returnflags/state/redirect URI; fremde Merchant-ID; Providerstatusänderung; Checkout-Payee-Abgleich.
* Abhängigkeiten: Provider-Identität, Secret/State Store, Payment Ledger.
* geschätzter Aufwand: M
* Akzeptanzkriterien: Kein Client kann Paymentempfänger oder Connectedstatus behaupten; Payee ist serververifiziert, eindeutig und auditiert.

### FINDING-005: Fremde Customer- und Provider-Bookings sind per IDOR lesbar

* Kategorie: Autorisierung / Datenschutz / API1
* Schweregrad: High
* Launch-Blocker: Ja
* Sicherheit: Vertraulichkeit personenbezogener und Zahlungsdaten
* betroffene Rollen: alle authentifizierten Rollen
* betroffene Dateien: `controller/BookingController.java:37-41,129-132`; `service/BookingService.java:90-98,354-361`; `dto/BookingResponse.java:8-47`
* betroffene Endpunkte: `GET /api/bookings/provider/{providerId}`, `GET /api/bookings/customer/{customerId}`
* Beschreibung: Beliebige authentifizierte Nutzer können UUIDs einsetzen; es gibt keine Rollen-/Self-/Adminprüfung.
* konkreter Nachweis: Services laden direkt `findBy...Id`; Response enthält Namen, Bilder, Notizen, Payment-/Settlement-IDs und Zeitdaten.
* realistisches Angriffs- oder Fehlerszenario: Ein Customer sammelt bekannte Provider-UUIDs aus öffentlichen Services und liest deren Kunden- und Umsatzdaten.
* Auswirkung: Datenschutzverletzung, Geschäftsgeheimnis- und Zahlungsmetadatenabfluss.
* empfohlene Lösung: Öffentliche ID-Endpunkte entfernen; nur `/me`; Adminquery separat und auditiert; minimalisierte rollenabhängige DTOs.
* empfohlene Tests: Matrix mit fremder/eigener ID für jede Rolle; DTO-Feldtests.
* Abhängigkeiten: Admin-API/Pagination.
* geschätzter Aufwand: S
* Akzeptanzkriterien: Fremde IDs liefern 403/404; Parteien sehen nur erforderliche Felder; Adminzugriff wird auditiert.

### FINDING-006: Jeder Login kann im Namen des Booking-Customers bewerten

* Kategorie: Autorisierung / Bewertungsintegrität
* Schweregrad: High
* Launch-Blocker: Ja
* Sicherheit: Integrität, Marktvertrauen
* betroffene Rollen: Customer, Provider, Admin
* betroffene Dateien: `controller/ReviewController.java:21-25`; `service/ReviewService.java:26-46`
* betroffene Endpunkte: `POST /api/reviews`
* Beschreibung: Der Controller übergibt keinen Principal. Der Service setzt den Reviewer stets auf den Customer des angegebenen abgeschlossenen Bookings.
* konkreter Nachweis: `User reviewer = booking.getCustomer()` ohne Vergleich mit Authentifizierung.
* realistisches Angriffs- oder Fehlerszenario: Ein Provider oder fremder Customer kennt eine Completed-Booking-UUID und erzeugt eine Review im Namen des Opfers.
* Auswirkung: Manipulierte Ratings/Rankings, Identitätsmissbrauch und Verbraucherirreführung.
* empfohlene Lösung: Customer-Rolle und `booking.customer.email == principal`; DB-Unique; Reviewfenster; Audit/Moderation.
* empfohlene Tests: Owner/Fremder/Provider/Admin, doppelter paralleler Create, nicht bezahltes/nicht abgeschlossenes Booking.
* Abhängigkeiten: Review-/Booking-State-Regeln.
* geschätzter Aufwand: S
* Akzeptanzkriterien: Nur der authentifizierte Booking-Customer darf exakt einmal im zulässigen Zustand bewerten.

### FINDING-007: Gespeichertes XSS trifft langlebige JWTs in localStorage

* Kategorie: XSS / Token-Diebstahl / OWASP A05
* Schweregrad: High
* Launch-Blocker: Ja
* Sicherheit: Accountübernahme, Vertraulichkeit, Integrität
* betroffene Rollen: Besucher und alle eingeloggten Rollen
* betroffene Dateien: `service/ServiceOfferingService.java:75-78,162-176`; `frontend/customer-app.js:248,281,456`; `frontend/service-detail.js:36`; `frontend/provider-profile.js:93,115`; `frontend/provider-dashboard.js:692,724`; `frontend/api.js:14-16`
* betroffene Endpunkte: Service Create/Update und alle Serviceansichten
* Beschreibung: `category` ist ein freier String und wird mehrfach ungeescaped in `innerHTML` interpoliert. JWTs liegen in `localStorage`; keine CSP ist definiert.
* konkreter Nachweis: Ausdrücke `${CAT_LABELS[s.category] || s.category}` ohne `esc(...)`; Backend hat keine Kategorie-Allowlist.
* realistisches Angriffs- oder Fehlerszenario: Provider speichert HTML mit Eventhandler als Kategorie; Besucheransicht führt Script aus und exfiltriert JWTs/API-Daten.
* Auswirkung: Stored XSS, Account-/Adminübernahme und Lieferketteneffekt über öffentliche Seiten.
* empfohlene Lösung: Serverseitige Enum/Taxonomie; überall `textContent`/kontextspezifisches Encoding; Trusted Types/CSP ohne Inline-Script; Tokens in gehärtetem BFF/HttpOnly-Konzept oder kurzlebig.
* empfohlene Tests: XSS-Payloads in allen Stringfeldern/Attribut-/URL-/CSS-Kontexten; CSP-Report; Browser-E2E mit Category-Payload.
* Abhängigkeiten: Frontend-Refactoring, Security Header, Tokenarchitektur.
* geschätzter Aufwand: M
* Akzeptanzkriterien: Keine Nutzereingabe erzeugt DOM-Knoten/Script; CSP blockiert Inline-/Fremdcode; XSS-Test deckt Category ab.

### FINDING-008: Booking- und Payment-Zustände besitzen keine gültige Zustandsmaschine

* Kategorie: Geschäftslogik / Insecure Design
* Schweregrad: High
* Launch-Blocker: Ja
* Sicherheit: Integrität und Betrugsschutz
* betroffene Rollen: Customer, Provider, Admin
* betroffene Dateien: `model/entity/Booking.java:31-98`; `service/BookingService.java:107-128,198-323`
* betroffene Endpunkte: Booking Status, Work, Delivery, Checkout, Capture, Record Payment
* Beschreibung: Status sind freie Strings; Provider kann ACCEPTED/REJECTED/COMPLETED aus nahezu jedem Zustand setzen, und Checkout/Payment prüfen den Bookingstatus nicht.
* konkreter Nachweis: `updateBookingStatus` validiert nur die Zielmenge; `createCheckout`, `markPaid` und `recordProviderPayment` prüfen keinen Ausgangszustand.
* realistisches Angriffs- oder Fehlerszenario: Rejected Booking wird bezahlt; Completed wird wieder Accepted; Work/Delivery wird vor Annahme publiziert.
* Auswirkung: Vertrags-, Fulfilment-, Refund- und Abrechnungsinkonsistenz.
* empfohlene Lösung: Explizite Transition Policy mit gekoppelten Invarianten, DB-Checks, Domain Events und atomarem Compare-and-Set.
* empfohlene Tests: vollständige Transitionstabelle inklusive Fehler-, Retry- und Admin-Sonderpfade.
* Abhängigkeiten: Payment Ledger, Cancellation/Refund Design.
* geschätzter Aufwand: L
* Akzeptanzkriterien: Jede Transition hat definierte Quelle, Akteur, Guard und Side Effects; alle anderen liefern 409.

### FINDING-009: Keine Idempotenz, Locks oder Webhook-Reihenfolgekontrolle

* Kategorie: Parallelität / Payments / Reliability
* Schweregrad: High
* Launch-Blocker: Ja
* Sicherheit: Double Spending, Lost Update, Doppelbuchung
* betroffene Rollen: Customer, Provider, System
* betroffene Dateien: `repository/BookingRepository.java:7-16`; `service/BookingService.java:41,148-174,198-323`; `service/StripeConnectService.java:177-243`; `service/PayPalService.java:136-183`
* betroffene Endpunkte: Booking Create/Transitions, Checkout/Capture, Stripe Webhook
* Beschreibung: Das Gesamtmodell besitzt weiterhin keine `@Version`, allgemeine Idempotency-Key-Tabelle oder eindeutigen Payment-ID-Constraints; einzelne Checkout-/Webhook-Pfade sind inzwischen gezielt abgesichert.
* konkreter Nachweis: PayPal-Order-Erzeugung, PayPal-Capture und Stripe-Checkout werden bookinggebunden pessimistisch serialisiert und senden stabile, nach API-Aufruftyp getrennte Provider-Idempotency-Keys; vollständig persistierte Zustände werden bei Replay ohne neuen Provideraufruf zurückgegeben. PayPal-Capture fordert die vollständige Providerrepräsentation an und bindet Order-ID, `reference_id` und `custom_id` fail-closed an die gelockte Buchung. Zehnfach-Races sind für diese Pfade grün, und echte HTTP-Adaptertests belegen Capture-Key, Representation-Header und Identitätsextraktion. Offen bleiben insbesondere Ledger/Outbox, allgemeine Payment-ID-Constraints, Betrag-/Währungs-/Payee-Abgleich, weitere Eventtypen und Recovery nach Parameteränderungen.
* realistisches Angriffs- oder Fehlerszenario: Doppelklick erzeugt mehrere Checkouts; verspätetes Failed überschreibt Paid; parallele Zeiteinträge verlieren Summen; Webhook-Replay sendet mehrfach Mail.
* Auswirkung: Doppelcharges, falsche Auszahlungen, verlorene Updates und nicht reproduzierbare Zustände.
* empfohlene Lösung: Idempotency-Key-Tabelle, Unique Provider IDs/Event IDs, optimistic/pessimistic locking, Inbox/Outbox, monotone Eventpolicy, Reconciliation.
* empfohlene Tests: 100 parallele Requests, Replay/Out-of-order/Timeout, Crash zwischen Provider- und DB-Schritt.
* Abhängigkeiten: Migrationen, Statusmaschine, Ledger.
* geschätzter Aufwand: XL
* Akzeptanzkriterien: Wiederholungen bewirken genau einen fachlichen Effekt; ältere Events können terminale Zustände nicht zurücksetzen.

### FINDING-010: Geld, Währung und Preisgrundlage sind nicht revisionssicher

* Kategorie: Payments / Datenintegrität
* Schweregrad: High
* Launch-Blocker: Ja
* Sicherheit: finanzielle Integrität
* betroffene Rollen: Customer, Provider, Admin
* betroffene Dateien: `model/entity/ServiceOffering.java:28-44`; `model/entity/Booking.java:88-95`; `service/BookingService.java:514-536`; `service/PayPalService.java:367-376`; `service/StripeConnectService.java:53-54,149-152`
* betroffene Endpunkte: Service Create/Update, Booking Checkout, Settlement
* Beschreibung: Geld/Stunden sind `Double`; Booking speichert keine Währung, Preis-/Steuer-/Gebührenversion oder Vertragssnapshot. Stripe-Konfigurationswährung kann vom Service abweichen.
* konkreter Nachweis: Checkout liest aktuellen `serviceOffering.price` und `actualHours`; negative/überlange Werte werden serverseitig nicht durchgängig verhindert.
* realistisches Angriffs- oder Fehlerszenario: Servicepreis wird nach Buchung geändert; paralleler Checkout erzeugt unterschiedliche Beträge; Rundung/Infinity korrumpiert Abrechnung.
* Auswirkung: Falsche Charges, Provision, Rechnung und Providerforderung.
* empfohlene Lösung: `BigDecimal`/DECIMAL und ISO-Währung; unveränderlicher Booking-/Quote-Snapshot; Preisart/Einheit/Steuer/Gebührenversion; Wertebereiche/Checks.
* empfohlene Tests: Rundungsgrenzen, Währungsmismatch, Preisänderung, Null/negativ/extrem, Snapshot-Replay.
* Abhängigkeiten: Migration, Produkt-/Steuerentscheidung.
* geschätzter Aufwand: L
* Akzeptanzkriterien: Jeder Paymentbetrag ist aus einem unveränderlichen, währungsgebundenen Snapshot reproduzierbar; keine binären Gleitkommazahlen für Geld.

### FINDING-011: Keine Availability- oder Overbooking-Kontrolle

* Kategorie: Booking / Parallelität
* Schweregrad: High
* Launch-Blocker: Ja
* Sicherheit: Geschäftsverfügbarkeit und Datenintegrität
* betroffene Rollen: Customer, Provider
* betroffene Dateien: `service/BookingService.java:63-88`; `repository/BookingRepository.java:7-16`; `model/entity/Booking.java:24-32`
* betroffene Endpunkte: `POST /api/bookings`
* Beschreibung: Es wird nur geprüft, ob das Datum nicht in der Vergangenheit liegt; Kapazität, Dauer, Providerkalender, Serviceaktivität und konkurrierende Buchungen fehlen.
* konkreter Nachweis: Kein Availability-Query, Lock oder Unique/Exclusion Constraint ist vorhanden.
* realistisches Angriffs- oder Fehlerszenario: Viele Kunden buchen denselben Provider/Tag gleichzeitig; alle Requests werden PENDING gespeichert.
* Auswirkung: Overbooking, manuelle Konflikte, Stornos und Vertrauensverlust.
* empfohlene Lösung: Availability-/Kapazitätsmodell, reservierbare Slots/Holds, DB-seitige Konfliktregel und transaktionaler Lock.
* empfohlene Tests: simultane Buchung gleicher/überlappender Slots, Zeitzonen/DST, Hold-Ablauf.
* Abhängigkeiten: Produktentscheidung zu Slot/Kapazität und Cancellation.
* geschätzter Aufwand: XL
* Akzeptanzkriterien: Kapazität wird auch bei Parallelität niemals überschritten; Konflikt liefert deterministisch 409.

### FINDING-012: Base64-Uploads erlauben Speicher-/Decode-DoS und ungeprüfte Inhalte

* Kategorie: Upload Security / Resource Consumption
* Schweregrad: High
* Launch-Blocker: Ja
* Sicherheit: Verfügbarkeit, Datenschutz, Content Safety
* betroffene Rollen: Customer, Provider
* betroffene Dateien: `dto/CreateServiceRequest.java:13-14`; `dto/UpdateUserRequest.java:10`; `model/entity/ChatMessage.java:22-30`; `service/ServiceOfferingService.java:278-294,358-401`; `service/ProviderProfileService.java:69-83`; `service/ChatMessageService.java:83-97`
* betroffene Endpunkte: Service/User Update, Chat Message, öffentliche Bildendpunkte
* Beschreibung: Service-/Avatar-Data-URLs haben kein Byte-/Request-/Pixel-Limit; Inhalte werden erst beim Abruf decodiert. Es fehlen Magic Bytes, Malware, Quota, Metadatenbereinigung und Storage-ACL.
* konkreter Nachweis: Maximal 10 Strings wird geprüft, nicht deren Größe; `ImageIO.read` läuft ohne Pixelgrenze; GIF/WebP werden roh zurückgegeben.
* realistisches Angriffs- oder Fehlerszenario: Provider speichert sehr große Base64-/Dekompressionsbombe; öffentliche Abrufe binden Heap/CPU und blähen DB/Backups.
* Auswirkung: Out-of-Memory, Datenbank-/Backupwachstum, Tracking-/Datenschutzrisiken.
* empfohlene Lösung: Object Storage, presigned Uploads, harte Byte-/Pixel-/Count-/Quota-Limits, Inhaltsdetektion, Re-Encoding/EXIF-Strip, Malware-Scan, zufällige Keys und Lifecycle.
* empfohlene Tests: falscher MIME/Endung, SVG/HTML, polyglot, decompression bomb, EXIF, Maximalgröße/-anzahl, Quota und Löschung.
* Abhängigkeiten: Storage-/CDN-Architektur.
* geschätzter Aufwand: L
* Akzeptanzkriterien: Ungültige/zu große Inhalte werden vor Persistenz abgelehnt; DB enthält keine unbeschränkten Base64-Dateien; Löschung/ACL/Quota sind getestet.

### FINDING-013: Login und Account-Flows haben keine Abuse- oder Passwortkontrollen

* Kategorie: Authentifizierung / Abuse Prevention
* Schweregrad: High
* Launch-Blocker: Ja
* Sicherheit: Accountübernahme und Verfügbarkeit
* betroffene Rollen: alle
* betroffene Dateien: `dto/CreateUserRequest.java:6-11`; `controller/AuthController.java:33-36,62-107`; `service/UserService.java:67-104`
* betroffene Endpunkte: Register, Login, Forgot, Resend, Reset
* Beschreibung: Keine Passwortlänge/-blocklist, Rate Limits, progressive Delays, IP-/Account-Limits, CAPTCHA-Risikosteuerung oder MFA; Login enumeriert Accounts.
* konkreter Nachweis: Unterschiedliche 401-Texte in `AuthController.java:89-96`; jede nicht-leere Passwortlänge wird akzeptiert.
* realistisches Angriffs- oder Fehlerszenario: Credential Stuffing/Brute Force oder massenhafte Mail-/Account-Erzeugung belastet Nutzer und Infrastruktur.
* Auswirkung: Kontoübernahmen, Mailkosten, DoS und Missbrauch des Marktplatzes.
* empfohlene Lösung: zentrale Rate-/Risk Controls, generische Authfehler, Mindestlänge/Blocklist, sichere Recovery, optional MFA für Admin/Provider-Payoutänderungen.
* empfohlene Tests: verteilte Limits, Account/IP-Kombination, Enumeration-Timing/Text, Passwortgrenzen, Recovery-Abuse.
* Abhängigkeiten: Reverse Proxy/Redis, Monitoring.
* geschätzter Aufwand: M
* Akzeptanzkriterien: Definierte Limits greifen messbar; Loginantworten enumerieren nicht; schwache/geleakte Passwörter werden abgelehnt; Admin-MFA ist aktiv.

### FINDING-014: JWT-, Logout- und Recovery-Lifecycle ist nicht widerrufbar; Chat leakt Token in URL

* Kategorie: Session/Token Security
* Schweregrad: High
* Launch-Blocker: Ja
* Sicherheit: Sessiondiebstahl und Langzeitzugriff
* betroffene Rollen: alle
* betroffene Dateien: `config/JwtUtil.java:16-26`; `frontend/api.js:14-16`; `frontend/customer-app.js:1543-1545`; `frontend/provider-dashboard.js:1807-1809`; `controller/ChatMessageController.java:38-43`
* betroffene Endpunkte: alle Bearer-APIs, Chat SSE, Logout/Reset
* Beschreibung: 24h Access Token ohne Refresh/Rotation/JTI/Revocation; Logout löscht nur lokal; Passwortreset widerruft Tokens nicht; SSE überträgt JWT als Queryparameter.
* konkreter Nachweis: `EventSource(...?token=...)`; Token liegt in `localStorage` und kann in Logs/History/Referer geraten.
* realistisches Angriffs- oder Fehlerszenario: Gestohlener Token bleibt nach Logout/Passwortreset gültig und wird aus Proxy-/Browserlogs wiederverwendet.
* Auswirkung: Persistente Accountübernahme und Chat-/Booking-Datenzugriff.
* empfohlene Lösung: kurze Access Tokens, rotierende Refresh Tokens/Tokenversion, serverseitiger Widerruf; SSE via BFF/kurzlebiges einmaliges Streamticket; `Referrer-Policy: no-referrer`.
* empfohlene Tests: Logout/Reset/Block/Role Change widerruft; Refresh Replay; URL-/Logprüfung; gestohlener Token.
* Abhängigkeiten: Autharchitektur/Secret Store.
* geschätzter Aufwand: L
* Akzeptanzkriterien: Kritische Accountänderungen invalidieren aktive Sessions; kein JWT erscheint in URLs/Logs; Replay wird erkannt.

### FINDING-015: Aufgelöster Java-Stack enthält ungepatchte OSV-Advisories

* Kategorie: Supply Chain / SCA
* Schweregrad: High
* Launch-Blocker: Ja
* Sicherheit: abhängig vom Advisory; RCE/Auth/DoS/Informationsrisiko
* betroffene Rollen: alle
* betroffene Dateien: `build.gradle:1-49`; aufgelöster Runtime-Classpath
* betroffene Endpunkte: potenziell alle Web-/JSON-/Actuatorpfade
* Beschreibung: OSV meldete 34 Treffer für Spring Boot 4.0.5, Spring Security 7.0.4, Spring MVC 7.0.6, Tomcat 11.0.20, Jackson 2.21.2/3.1.0 und Logback 1.5.32.
* konkreter Nachweis: Darunter Critical `GHSA-5m62-pw8w-7w9f`, `GHSA-8v8j-3hxp-93wr`, `GHSA-h6fc-48rj-7qqh`, `GHSA-r29c-68gh-xp6x`; OSV nennt u. a. Fixes Boot 4.0.6, Security 7.0.5, Tomcat 11.0.22, Jackson 2.21.4/3.1.4 und Logback 1.5.35. Mehrere Bedingungen (Digest, XML Rules, X.509, Default Security Chain) sind im aktuellen Code nicht erkennbar, andere müssen deploymentbezogen bewertet werden.
* realistisches Angriffs- oder Fehlerszenario: Eine anwendbare HTTP-/Deserialisierungs-/Tempdir-Lücke wird vor manueller Aktualisierung ausgenutzt.
* Auswirkung: Von DoS/Cache Poisoning bis Auth-/Serverkompromittierung, abhängig von Konfiguration.
* empfohlene Lösung: Auf aktuell gepatchte kompatible Boot-BOM aktualisieren, Advisory-Bedingungen dokumentieren, SCA/SBOM/Dependabot in CI mit SLA.
* empfohlene Tests: vollständige Regression, Security-Header/Matchers, JSON-Grenzen, HTTP/2/Proxy, Container-SCA.
* Abhängigkeiten: CI/CD, Kompatibilitätstests.
* geschätzter Aufwand: M
* Akzeptanzkriterien: Keine offenen anwendbaren Critical/High-SCAs; Nichtanwendbarkeit ist je Advisory belegt; automatisches Gate verhindert Regression.

### FINDING-016: Datenschutzrechte, Retention und administrative Zugriffsauditierung fehlen

* Kategorie: Datenschutz / technische Compliance
* Schweregrad: High
* Launch-Blocker: Ja
* Sicherheit: Personenbezogene Daten, Nachweisfähigkeit
* betroffene Rollen: alle, Admin
* betroffene Dateien: gesamtes Datenmodell; `service/UserService.java:147-160`; `service/AdminService.java:72-99`
* betroffene Endpunkte: User Delete, Admin-Listen; fehlende Export-/Consent-APIs
* Beschreibung: Kein Zweck-/Consent-/Rechtstextversionsmodell, Betroffenenexport, Retention/Löschjob, Legal Hold, Pseudonymisierung, Backup-Löschung oder Admin-Audit.
* konkreter Nachweis: User Delete ist ein synchroner Hard-Delete; Admin liest PII/IBAN/Payment-IDs ohne Audit; keine entsprechenden Entities/Jobs/Configs vorhanden.
* realistisches Angriffs- oder Fehlerszenario: Auskunft/Löschung kann nicht vollständig beantwortet werden; Admin-Massenabgriff bleibt unentdeckt; Backups behalten Daten unbegrenzt.
* Auswirkung: Datenschutzvorfall und fehlende regulatorische Nachweisfähigkeit.
* empfohlene Lösung: Dateninventar/Policy als Code, Exportworkflow, Retention/Anonymisierung/Legal Hold, Consent-/Textversionen, Admin-Audit und Backup-Löschkonzept.
* empfohlene Tests: vollständiger Export, Löschung mit/ohne Aufbewahrung, Backup/Restore nach Löschung, Admin-Audit, Consent-Widerruf.
* Abhängigkeiten: juristische Vorgaben, Backupdesign, Audit Store.
* geschätzter Aufwand: XL
* Akzeptanzkriterien: Jede Datenart hat Zweck, Frist und getesteten Lösch-/Exportpfad; privilegierter Zugriff ist nachvollziehbar.

### FINDING-017: Refund, Chargeback, Payout und Reconciliation sind nicht implementiert

* Kategorie: Payments / Operations
* Schweregrad: High
* Launch-Blocker: Ja
* Sicherheit: finanzielle Integrität und Recovery
* betroffene Rollen: Customer, Provider, Admin
* betroffene Dateien: `model/entity/Booking.java:52-101`; `service/AdminService.java:136-156`; `service/StripeConnectService.java:177-243`; `service/PayPalService.java`
* betroffene Endpunkte: Payment-/Webhook-/Settlementpfade
* Beschreibung: Keine Refund-/Teilrefund-/Dispute-/Chargeback-/Payout-Ausführung, kein Ledger und kein automatischer Abgleich; Admin ändert nur einen String.
* konkreter Nachweis: Keine entsprechenden Controller/Provider-APIs/Jobs; `settlementStatus` und Notiz sind manuell.
* realistisches Angriffs- oder Fehlerszenario: Chargeback trifft nach Providerauszahlung ein; Booking bleibt Paid/Completed und Provision/Payout werden nicht korrigiert.
* Auswirkung: Geldverlust, falsche Buchhaltung, Kunden-/Providerstreit und fehlende Recovery.
* empfohlene Lösung: append-only Ledger, Refund/Dispute/Payout-Workflows, Provider-Webhooks, Reconciliation-/Recoveryjobs, Vier-Augen-Adminaktionen.
* empfohlene Tests: Full/Partial Refund, Chargeback vor/nach Payout, fehlgeschlagener Payout, fehlender/verspäteter Webhook, täglicher Abgleich.
* Abhängigkeiten: Statusmaschine, Idempotenz, Rechnungs-/Steuerkonzept.
* geschätzter Aufwand: XL
* Akzeptanzkriterien: Jeder Providerbetrag ist ledgerbasiert abstimmbar; alle Ausnahmeprozesse sind idempotent und sandbox-getestet.

### FINDING-018: Schemaänderung und Löschung gefährden Datenintegrität

* Kategorie: Datenbank / Migration / Retention
* Schweregrad: High
* Launch-Blocker: Ja
* Sicherheit: Datenverlust, Verfügbarkeit und Integrität
* betroffene Rollen: alle
* betroffene Dateien: `resources/application.properties:17-19`; `config/SchemaMigrationConfig.java:14-106`; `service/UserService.java:147-160`; `service/ServiceOfferingService.java:158-160`; JPA-Entities
* betroffene Endpunkte: User/Service Delete, Anwendungstart/Deployment
* Beschreibung: Hibernate `ddl-auto=update` plus imperatives Start-DDL ohne Versionshistorie/Rollback; Fehler werden verschluckt. Hard-Delete berücksichtigt Review/Chat/TimeEntry/Report-Abhängigkeiten nicht.
* konkreter Nachweis: `tryExecute` fängt jede Exception leer; Service Delete prüft vorhandene Bookings nicht; User Delete löscht Bookings vor ihren Child-FKs.
* realistisches Angriffs- oder Fehlerszenario: Deployment startet mit Teilschema; Kontolöschung scheitert oder löscht geschäftlich aufzubewahrende Belege.
* Auswirkung: Ausfall, inkonsistentes Schema, Datenverlust und Complianceverletzung.
* empfohlene Lösung: Flyway/Liquibase, `ddl-auto=validate`, Forward-/Rollback-/Backupplan; Soft Delete/Anonymisierung und explizite FK-Cascade/Restrict-Policy.
* empfohlene Tests: leere/alte/teilmigrierte PostgreSQL-DB, Rollback, FK-Löschgraph, Restore.
* Abhängigkeiten: Retention/Legal, Datenmodellrevision.
* geschätzter Aufwand: L
* Akzeptanzkriterien: Jede Schemaänderung ist versioniert/CI-getestet; Deployment bricht bei Fehler atomar ab; Löschung erfüllt definierte Policies.

### FINDING-019: Keine sichere Produktions-, CI/CD- oder Secret-Delivery-Basis

* Kategorie: DevOps / Security Misconfiguration
* Schweregrad: High
* Launch-Blocker: Ja
* Sicherheit: Supply Chain, Secrets, Verfügbarkeit
* betroffene Rollen: alle
* betroffene Dateien: `docker-compose.yml:1-15`; `resources/application.properties:1-58`; `frontend/api.js:3`; fehlende CI-/Docker-/Proxydateien
* betroffene Endpunkte: gesamte Anwendung
* Beschreibung: Compose enthält nur DB mit `postgres/postgres` und Hostport; Backend/Frontend sind nicht produktiv paketiert. Keine CI, Umgebungsprofile, TLS/Proxy, Secret Store, Rollback, Backup oder Releaseautomation.
* konkreter Nachweis: `BASE_URL` ist localhost; Mail default console, PayPal sandbox, SQL-Logging aktiv; Swagger/H2 sind permitAll; keine `.github/workflows`/Dockerfiles.
* realistisches Angriffs- oder Fehlerszenario: Lokale Defaults gelangen in Produktion oder ein ungeprüfter Commit wird ohne Scan/Restore-/Rollbackfähigkeit deployt.
* Auswirkung: Datenbank-/Secret-Exposition, unsichere Releases und lange Ausfälle.
* empfohlene Lösung: gehärtete Profile, Secret Manager, App-/Frontend-Images mit non-root/read-only, Reverse Proxy/TLS, CI Gates, SBOM/Signierung, progressive Deployments, Backup/Restore/Runbooks.
* empfohlene Tests: Config-Fail-Closed, Secret Scan, Container Scan, Deployment Smoke, Migration/rollback, Restore und DR-Übung.
* Abhängigkeiten: Zielplattform, Verantwortlichkeiten/Budget.
* geschätzter Aufwand: XL
* Akzeptanzkriterien: Produktion startet nicht mit Devdefaults; alle Artefakte durchlaufen definierte Gates; Rollback und Restore sind erfolgreich protokolliert.

### FINDING-020: N+1-Abfragen und unbeschränkte Listen verhindern planbare Skalierung

* Kategorie: Performance / API Resource Consumption
* Schweregrad: Medium
* Launch-Blocker: Nein
* Sicherheit: Verfügbarkeit
* betroffene Rollen: Besucher, Provider, Customer, Admin
* betroffene Dateien: `service/ServiceOfferingService.java:189-198`; `service/BookingService.java:371-413,602-611`; `service/AdminService.java:39-99`; Repositories
* betroffene Endpunkte: Service Search/Detail, Booking Lists, `/api/admin/**`
* Beschreibung: Rating/Count, Reviews und Time Entries werden pro Element nachgeladen; Admin-/Bookinglisten sind unpaginiert.
* konkreter Nachweis: Zwei Ratingqueries je Service; `findAll()` für Users/Bookings/Reviews/Services; `loadTimeEntries` je Booking.
* realistisches Angriffs- oder Fehlerszenario: Mit wachsendem Datenbestand sättigen harmlose Listen DB-Pool und Heap; Adminaufruf verursacht viele Queries.
* Auswirkung: Latenz, Timeout und DoS-Verstärkung.
* empfohlene Lösung: projektionierte Aggregatqueries/EntityGraphs, Batch Fetch, Pagination/max size, passende Indizes und Query-Budgets.
* empfohlene Tests: Query-Count-Assertions, PostgreSQL `EXPLAIN ANALYZE`, Lasttest mit realistischem Volumen.
* Abhängigkeiten: Migration/Indizes, API-Verträge.
* geschätzter Aufwand: L
* Akzeptanzkriterien: Queryanzahl pro Seite ist O(1)/gebunden; alle Listen paginiert; Ziel-p95 unter Produktionsdatengröße erreicht.

### FINDING-021: Fehlerformat, Statuscodes, Validierung und API-Lifecycle sind inkonsistent

* Kategorie: API Quality / Information Disclosure
* Schweregrad: Medium
* Launch-Blocker: Nein
* Sicherheit: Informationsminimierung und sichere Fehlerbehandlung
* betroffene Rollen: alle
* betroffene Dateien: `model/common/exception/GlobalExceptionHandler.java:18-60`; mehrere Controller/DTOs; `config/OpenApiConfig.java`
* betroffene Endpunkte: gesamte API
* Beschreibung: Runtimefehler werden als 400 mit `ex.getMessage()` ausgegeben; Auth liefert Plaintext; Not Found/Conflict fehlen; Validation ist lückenhaft; keine API-Version/Correlation/Deprecation.
* konkreter Nachweis: Drittanbieter-/interne Exceptiontexte können bis zum Client gelangen; Create Service liefert 200 statt 201.
* realistisches Angriffs- oder Fehlerszenario: Providerfehlermeldung leakt Implementierungs-/Providerdetails und Monitoring klassifiziert echte Serverfehler als Clientfehler.
* Auswirkung: Informationsleck, schlechte Observability/Clients und instabile Verträge.
* empfohlene Lösung: RFC 9457 Problem Details, stabile Fehlercodes/Correlation ID, typisierte Exceptions, vollständige Bean Validation, `/api/v1`/Deprecation Policy.
* empfohlene Tests: Status-/Schema-Contracttests, keine Stack-/Providerdetails, Fuzzing aller DTO-Grenzen.
* Abhängigkeiten: OpenAPI/Clientmigration.
* geschätzter Aufwand: M
* Akzeptanzkriterien: Alle Fehler folgen einem dokumentierten Schema und korrekter Semantik; unerwartete Details bleiben serverseitig.

### FINDING-022: Externe Aufrufe und In-Memory-Komponenten sind nicht resilient

* Kategorie: Reliability / External Dependencies
* Schweregrad: Medium
* Launch-Blocker: Nein
* Sicherheit: Verfügbarkeit und Konsistenz
* betroffene Rollen: alle
* betroffene Dateien: `ServiceRateBackendApplication.java:15-19`; `service/MailService.java:222-325`; `service/BookingService.java:41`; `service/ChatMessageService.java:31,65-121`; Stripe/PayPal/Weather/Location Services
* betroffene Endpunkte: Registrierung, Booking/Payment, Service Create, Weather, Chat
* Beschreibung: Keine expliziten Timeouts/Retry/Circuit/Outbox; SMTP ist rohe synchrone Socketkommunikation; SSE-Emitter leben nur in einer JVM.
* konkreter Nachweis: `new RestTemplate()` ohne Timeouts; externe Calls in `@Transactional`; `ConcurrentHashMap` hält Streams lokal.
* realistisches Angriffs- oder Fehlerszenario: Hängende Drittanbieter erschöpfen Threads/DB-Connections; Neustart verliert Streams; Mailfehler rollt Registrierung anders als Bookingmail.
* Auswirkung: Kaskadenausfall und unklare Teiltransaktionen.
* empfohlene Lösung: Timeouts/Bulkheads/Circuit Breaker, kontrollierte Retries, Outbox/Queue, DLQ, idempotente Worker, externer Pub/Sub für SSE.
* empfohlene Tests: Timeout, 429/5xx, Netzwerkabbruch, Neustart zwischen Commit/Event, Multi-Instance-Chat.
* Abhängigkeiten: Messaging/Observability.
* geschätzter Aufwand: L
* Akzeptanzkriterien: Jede Dependency hat Budget/Fallback; Fachcommit und Side Effects sind recoverbar; Multi-Instance-Verhalten ist getestet.

### FINDING-023: Security-, Payment-, Admin- und Moderationsaktionen sind nicht auditierbar

* Kategorie: Logging / Monitoring / Audit
* Schweregrad: Medium
* Launch-Blocker: Nein
* Sicherheit: Erkennung, Forensik und Nichtabstreitbarkeit
* betroffene Rollen: Admin, Provider, Customer
* betroffene Dateien: `service/AdminService.java`; `service/BookingService.java`; `service/StripeConnectService.java`; fehlende Auditkomponenten
* betroffene Endpunkte: Admin, Payments, Bookingstatus, Payout-/Profiländerungen
* Beschreibung: Keine strukturierten Events, Actor/Before/After/Reason/IP/Correlation oder manipulationsgeschützte Aufbewahrung; keine Alerts/Metriken.
* konkreter Nachweis: Admin- und Settlementmethoden speichern nur den Zielzustand/Notiz; kein Audit-Repository/Telemetry-Exporter.
* realistisches Angriffs- oder Fehlerszenario: Missbräuchliche Sperrung, Payee-Änderung oder Settlement-Manipulation kann nicht sicher rekonstruiert werden.
* Auswirkung: Späte Erkennung, fehlende Beweise, Compliance-/Supportprobleme.
* empfohlene Lösung: append-only Audit Events, restriktiver Zugriff/Retention/Integrität, Correlation/Tracing, Metriken und definierte Alerts.
* empfohlene Tests: jede kritische Aktion erzeugt genau ein redigiertes Audit Event; Manipulations-/Ausfalltest; Alertprobe.
* Abhängigkeiten: Log-/SIEM-Plattform, Datenschutzfristen.
* geschätzter Aufwand: L
* Akzeptanzkriterien: Kritische Aktionen sind vollständig, unveränderlich und datensparsam nachvollziehbar; Alerts wurden ausgelöst und bestätigt.

### FINDING-024: Marktplatz-, Moderations- und Rechnungsprozesse sind rechtlich nicht abnahmebereit

* Kategorie: Marketplace Compliance / Moderation
* Schweregrad: Medium
* Launch-Blocker: Ja
* Sicherheit: technische Compliance und Verbrauchertransparenz
* betroffene Rollen: Besucher, Customer, Provider, Admin
* betroffene Dateien: statische Frontends; `model/entity/Report.java`; `service/ReportService.java`; `service/AdminService.java`; Frontend-Rechnungsdruck
* betroffene Endpunkte: Reports/Admin/Services/Checkout
* Beschreibung: Anbieterklassifikation/-identität, Rechtstexte/Versionen, Rücktritt/Storno, Ranking-/Sponsoringtransparenz, verbotene Kategorien, Notice-and-Action-Gründe, Appeal und Moderationsaudit fehlen. Druckansichten enthalten keine belastbaren Aussteller-/Steuer-/Rechnungsnummern.
* konkreter Nachweis: Report speichert nur Ziel/Grund/Details/Status; Admin setzt Status ohne Reason/Audit/Appeal; keine Legal-Entities/Pages.
* realistisches Angriffs- oder Fehlerszenario: Angebot wird ohne begründete Entscheidung gesperrt oder Rechnung/Preisinfo ist für Verbraucher/Steuer nicht verwendbar.
* Auswirkung: Juristische Abnahme scheitert; Beschwerde- und Transparenzpflichten nicht technisch erfüllbar.
* empfohlene Lösung: mit Rechtsberatung fachliches Compliance-Modell, versionierte Texte/Acceptance, Traderstatus/KYC, Price Breakdown, Moderation Cases/Reasons/Evidence/Appeal und Rechnungsservice.
* empfohlene Tests: Legal-Text-Versionen, vollständige Pre-Contract-Anzeige, Notice→Decision→Appeal, Review-/Rankingprovenienz, Rechnungsfixtures.
* Abhängigkeiten: Rechts-/Steuerberatung und Product Policy.
* geschätzter Aufwand: XL
* Akzeptanzkriterien: Juristische Abnahme bestätigt alle technischen Flows; jede Entscheidung/Anzeige ist versioniert und nachvollziehbar.

### FINDING-025: Zentrale UI-Flows erfüllen WCAG 2.2 AA nicht nachweisbar

* Kategorie: Accessibility / Usability
* Schweregrad: Medium
* Launch-Blocker: Ja
* Sicherheit: gleichberechtigter Zugang und Fehlbedienungsschutz
* betroffene Rollen: alle Nutzer mit assistiven Technologien
* betroffene Dateien: `frontend/customer-app.html`; `provider-dashboard.html`; `customer-app.js`; `provider-dashboard.js`; `customer-style.css`
* betroffene Endpunkte: n/a, alle Frontend-Flows
* Beschreibung: Modals/Drawer ohne Dialogsemantik, Fokusmanagement/-trap/-restore/Escape; klickbare Articles ohne Keyboardrolle; Tabs/Toasts ohne passende ARIA-States/Live-Region.
* konkreter Nachweis: 0 `role="dialog"`/`aria-modal` in allen HTML-Dateien; keine allgemeine Focus-/Escape-Logik gefunden.
* realistisches Angriffs- oder Fehlerszenario: Screenreader-/Tastaturnutzer verliert Fokus hinter Overlay oder kann Service/Booking-Karten nicht aktivieren.
* Auswirkung: Blockierte Kernprozesse, Fehlbedienung und fehlende WCAG-Abnahme.
* empfohlene Lösung: native Elemente/ARIA Patterns, Fokusmanagement, Keyboardnavigation, Live Regions, sichtbarer Fokus, Reduced Motion, Kontrast/Reflow/Target-Size-Audit.
* empfohlene Tests: axe-core plus manuell NVDA/VoiceOver, Tastatur, 200/400 % Zoom, High Contrast, Mobile Touch.
* Abhängigkeiten: Designsystem/Frontend-Refactoring.
* geschätzter Aufwand: L
* Akzeptanzkriterien: Keine kritischen axe-Befunde; alle Kernflows manuell nach WCAG 2.2 AA protokolliert bestanden.

### FINDING-026: Tests decken kritische Negativ-, Parallelitäts- und Live-Integrationspfade nicht ab

* Kategorie: QA / Security Regression
* Schweregrad: Medium
* Launch-Blocker: Ja
* Sicherheit: alle kritischen Schutzziele
* betroffene Rollen: alle
* betroffene Dateien: `src/test/**`; `tests/e2e/customer-usability.spec.js`; `tests/performance/**`; `TEST_STRATEGY.md`
* betroffene Endpunkte: insbesondere Service PUT/DELETE, Booking-IDOR, Reviews, Payment/Refund/Webhooks
* Beschreibung: 58 Backend- und 8 gemockte UI-Tests sind grün, prüfen aber die Critical Findings, PostgreSQL-Migrationen, echte E2E-Payments, Concurrency, Recovery und WCAG nicht.
* konkreter Nachweis: Der XSS-Test nutzt escaped `description` bei fixer Category `PLUMBING`; Webhooktests prüfen Delegation statt Betrag/Reihenfolge; kein Testcontainers/Concurrency/Refund.
* realistisches Angriffs- oder Fehlerszenario: Grüne Pipeline suggeriert Sicherheit, während direkter API-Aufruf kritische Controls umgeht.
* Auswirkung: Wiederkehrende Sicherheits-/Zahlungsfehler und unsichere Releases.
* empfohlene Lösung: risikobasierte Testpyramide, PostgreSQL/Testcontainers, Provider-Sandbox/Contracttests, Security-Matrix, concurrency/fault injection, WCAG und Release-Gates.
* empfohlene Tests: siehe `docs/TEST_GAP_ANALYSIS.md`.
* Abhängigkeiten: CI/CD, korrigierte Fachmodelle.
* geschätzter Aufwand: L
* Akzeptanzkriterien: Alle P0/P1-Akzeptanzkriterien sind automatisiert; kritischer E2E und Concurrent Payment/Booking sind deterministisch grün.

### FINDING-027: Repository enthält 887 verfolgte node_modules-Dateien

* Kategorie: Repository Hygiene / Supply Chain
* Schweregrad: Low
* Launch-Blocker: Nein
* Sicherheit: Reviewbarkeit und Reproduzierbarkeit
* betroffene Rollen: Entwicklung/DevOps
* betroffene Dateien: `node_modules/**`; `.gitignore`
* betroffene Endpunkte: n/a
* Beschreibung: `node_modules` wird nicht ignoriert und 887 Dateien sind im Git-Index; gleichzeitig existiert ein Lockfile.
* konkreter Nachweis: `git ls-files` ergab 1.024 Dateien, davon 887 unter `node_modules`; `.gitignore` hat keinen Eintrag dafür.
* realistisches Angriffs- oder Fehlerszenario: Dependencyänderungen verstecken sich in Vendor-Diffs oder lokale Binär-/Skriptinhalte werden unbeabsichtigt committed.
* Auswirkung: große Reviews, Drift und erschwerte Supply-Chain-Prüfung.
* empfohlene Lösung: `node_modules/` ignorieren und aus dem Index entfernen; ausschließlich `npm ci` aus Lockfile in CI.
* empfohlene Tests: sauberer Clone + `npm ci` + E2E; Git-Check auf Vendorverzeichnisse.
* Abhängigkeiten: CI.
* geschätzter Aufwand: XS
* Akzeptanzkriterien: Keine installierten Dependencies sind versioniert; Clean Build ist reproduzierbar.

## D. Positivbefunde

1. `BCryptPasswordEncoder` statt Klartext/unsicherem Hash.
2. Verify- und Reset-Token haben Ablauf und werden nach erfolgreicher Nutzung gelöscht.
3. Forgot/Resend geben generische Antworten und reduzieren Enumeration in diesen Flows.
4. Der JWT-Filter prüft den aktuellen Accountstatus in der DB und sperrt deaktivierte Nutzer sofort.
5. Booking Create leitet den Customer aus dem Principal ab; das untrusted `customerId`-Feld beeinflusst die Zuordnung nicht.
6. Provideraktionen für Status, Work, Time Entry, Delivery und Payment Record besitzen Booking-Ownership-Checks.
7. Deliveryzugriff prüft Parteien, Payment für Customer und Ablaufdatum.
8. Stripe-Webhooks prüfen die Signatur; Stripe/PayPal erzeugen reale Checkoutbeträge serverseitig.
9. Keine Kartennummern/CVV im Modell oder in Logs/DTOs gefunden; nur tokenisierte Provider-IDs werden gespeichert.
10. DTOs vermeiden direkte Entity-Ausgabe und enthalten keinen Passwort-Hash.
11. Öffentliche Service-Suche ist paginiert, Seitengröße ist auf 48 begrenzt und JPQL ist parametriert.
12. Zeitstempel nutzen überwiegend `OffsetDateTime`/`TIMESTAMP WITH TIME ZONE`.
13. Auslieferbarer Data-URL-Bildtyp schließt SVG/HTML aus; Servicebildanzahl ist auf 10 begrenzt.
14. Responsive Desktop-/Mobile-UI, Loading-/Empty-States und sichtbare Labels sind eine brauchbare UX-Basis.
15. Der erzwungene Backend-Testlauf und alle Playwright-Tests waren erfolgreich; npm audit war zum Auditzeitpunkt sauber.
