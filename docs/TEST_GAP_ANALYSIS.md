# Test Gap Analysis

Stand: 2026-07-14  
Ziel: Nachweis, ob ServiceRate sicher, fachlich korrekt und betrieblich beherrschbar veröffentlicht werden kann.

## Executive Summary

Die vorhandenen Tests sind als Entwicklungsbasis brauchbar, aber kein Release-Nachweis. Der erzwungene Backend-Lauf bestand mit **58/58 Tests**, die Playwright-Suite mit **8/8 Tests** auf Chromium Desktop und Mobile. `npm audit` meldete keine npm-Schwachstellen. Dennoch sind alle vier kritischen Exploitpfade und wesentliche Zahlungs-, Nebenläufigkeits-, Recovery-, Datenschutz- und Accessibility-Risiken ungetestet. Die E2E-Suite mockt das Backend und beweist daher keine integrierte Systemfunktion.

**Testfreigabe: NO-GO.** Ein grüner Status darf erst erteilt werden, wenn die P0-Suite gegen PostgreSQL und das gebaute Releaseartefakt besteht und alle Release-Gates in [RELEASE_CHECKLIST.md](RELEASE_CHECKLIST.md) erfüllt sind.

## Ausgeführte Baseline

| Prüfung | Ergebnis am 2026-07-14 | Aussagekraft | Grenze |
|---|---|---|---|
| `gradlew.bat test --rerun-tasks --no-daemon` | 58 bestanden, 0 fehlgeschlagen/übersprungen | Controller-/Service-/Repository-Baseline | überwiegend H2; keine produktionsnahe Infrastruktur |
| `npm run test:e2e` | 8 bestanden | Customer-Suche/Responsive-Smoke mit Chromium | API wird gemockt; kein Auth-/DB-/Payment-Systemtest |
| `npm audit --json` | 0 npm-Vulnerabilities | npm-Manifest/Lockfile | deckt Maven, Container und Laufzeitkonfiguration nicht ab |
| Gradle Runtime + OSV | 115 Komponenten, 34 Meldungen | SCA-Hinweis | Anwendbarkeit und finales Artefakt separat verifizieren |
| k6-Skripte | vorhanden | Ausgangspunkt für Suche/Smoke | kein aktueller signierter Ergebnisreport, kein Soak/Payment-Race |

## Coverage-Matrix der kritischen Geschäftsprozesse

Legende: **Ja** = sinnvoll abgedeckt, **Teil** = Happy Path oder Mock, **Nein** = kein belastbarer Nachweis.

| Prozess/Risiko | Unit | Integration mit PostgreSQL | echtes E2E | Security-Negativ | Concurrency/Recovery | Status |
|---|---|---|---|---|---|---|
| Registrierung, Verifikation, Login | Teil | Nein | Nein | Nein | Nein | P1-Lücke |
| Passwort-Reset und Session-Widerruf | Teil | Nein | Nein | Nein | Nein | P1-Lücke |
| JWT-Signatur/Rolle/Rotation | Teil | Nein | Nein | Nein | Nein | **P0** |
| Service CRUD und Ownership | Teil | Nein | Nein | Nein | Nein | **P0** |
| Suche/Filter/Pagination | Ja/Teil | Nein | Teil/Mock | Nein | k6 ohne Freigabereport | P2-Lücke |
| Buchung erstellen | Teil | Nein | Nein | Teil | Nein | **P0** |
| Buchungsstatusmatrix | Teil | Nein | Nein | Nein | Nein | **P0** |
| Slot/Kapazität/Überbuchung | Nein | Nein | Nein | Nein | Nein | **P0** |
| Stripe Checkout/Webhook | Ja/Teil inkl. Payment-/Application-Fee-Minor-Unit-, Währungs-/Destination-Snapshot und Completed-Abgleich | Nein | Nein | Signatur plus Session-/Intent-/Finanz-/Fee-/Destination-Bindung | Inbox-/Checkout-H2-Races und Reihenfolgetests | **P0** |
| PayPal Checkout/Onboarding | Ja/Teil inkl. Order-Snapshot und Capture-Betrag/Währung/Payee | Nein | Nein | Teil inkl. Capture-Provider- und Finanzbindung | Adapter-Retry plus H2-Zehnfach-Races für Order/Capture | **P0** |
| Ledger/Idempotenz/Event-Reihenfolge | Teil | Nein | Nein | Teil | Provider-Key-Retry plus Teil/H2 | **P0** |
| Refund/Chargeback/Payout/Reconciliation | Nein | Nein | Nein | Nein | Nein | P1-Lücke |
| Delivery nach Zahlung | Teil | Nein | Nein | Nein | Nein | **P0** |
| Chat/SSE und Bildnachrichten | Teil | Nein | Nein | Nein | Nein | P1-Lücke |
| Review erstellen/moderieren | Teil | Nein | Nein | Nein | Nein | **P0** |
| Reports/Admin/Moderation | Teil | Nein | Nein | Nein | Nein | P1-Lücke |
| Upload-Inhalts-/Ressourcenlimits | Teil | Nein | Nein | Nein | Nein | P1-Lücke |
| Datenexport/Löschung/Retention | Nein | Nein | Nein | Nein | Nein | P1-Lücke |
| Migration/Upgrade/Restore | Nein | Nein | Nein | n/a | Nein | **P0** |
| Logging/Alarm/Audit | Nein | Nein | Nein | Nein | Nein | P1-Lücke |
| WCAG 2.2 AA | Nein | n/a | Nein | n/a | n/a | P1-Lücke |

## P0-Testpaket vor einem öffentlichen Launch

### 1. Authentifizierung und Token

- Mit dem alten oder öffentlich bekannten Key signierte Tokens müssen nach Rotation abgewiesen werden.
- Manipulierte Rolle, User-ID, Issuer, Audience, Signaturalgorithmus, Ablauf und `nbf` müssen jeweils fehlschlagen.
- Datenbank-Rollenänderung, Account-Sperrung, Passwortwechsel und Logout müssen bestehende Sessions gemäß Policy widerrufen.
- Gleichförmige Loginantworten und Rate Limits gegen User Enumeration und Credential Stuffing prüfen.
- Admin- und Provider-MFA inklusive Recovery, Replay und Lockout testen.

### 2. Autorisierungsmatrix

Für jeden objektbezogenen Endpunkt sind mindestens Owner, fremder Nutzer gleicher Rolle, andere Rolle, Admin, gesperrter Account, anonym und nicht verifizierter Account zu prüfen.

| Ressource/Aktion | Customer | Owner-Provider | fremder Provider | Admin | Anonym |
|---|---|---|---|---|---|
| Service erstellen | 403 | erlaubt | n/a | nach expliziter Policy | 401 |
| Service ändern/löschen | 403 | erlaubt | 403 | explizit + Audit | 401 |
| Buchungsdetail lesen | nur beteiligt | nur beteiligt | 403 | explizit + Audit | 401 |
| Customer-/Provider-Buchungsliste | nur eigener Principal | nur eigener Principal | 403 | explizit + Audit | 401 |
| Review erstellen | nur Customer der abgeschlossenen Buchung, einmalig | 403 | 403 | nach Policy | 401 |
| Payment-/Settlementdaten | minimal erforderliche Sicht | eigene Buchungen, maskiert | 403 | Least Privilege + Audit | 401 |

Die Tests müssen UUID-Austausch in Pfad, Query und Body kombinieren. Ein bloßer Rollen-Mock auf Controller-Ebene reicht nicht; die Policy ist über HTTP und Service-Grenze zu prüfen.

### 3. Payment und Ledger

- `mark-paid` direkt als Customer, Provider und anonym aufrufen: kein Client darf einen bezahlten Zustand erzeugen.
- Gültige/ungültige Stripe- und PayPal-Signatur sowie falscher Provider, Payee, Betrag, Gebühr, Währung, Booking-ID und Payment-ID. PayPal deckt Order-/Booking-ID, Betrag, Währung und Payee ab. Stripe deckt für `checkout.session.completed` Session-/Intent-ID, `paid`/`succeeded`, Soll-/Istbetrag, `amount_received`, `application_fee_amount`, Währung und Connected Account einschließlich fehlender/leerer/fremder Werte ab. Weitere Stripe-Eventtypen und echte PSP-Sandbox-Abnahmen bleiben offen.
- Dasselbe Ereignis 1, 2 und 100 Mal senden: exakt ein Ledger-Effekt.
- Ereignisse `completed`, `failed`, `refunded`, `disputed` in jeder Reihenfolge senden: deterministischer Endzustand.
- Timeout/Prozessabsturz vor und nach DB-Commit simulieren: keine verlorene oder doppelte Buchung.
- 100 parallele Checkout-/Capture-/Webhook-Requests auf dieselbe Buchung.
- Rundung und Minor Units für 0, 1, Maximalbetrag, Dezimalgrenzen, Gebühren und Steuern.
- Preisänderung nach Buchung: Checkout und Rechnung müssen den unveränderlichen Snapshot verwenden.
- Merchant-Onboarding: gefälschte Flags, fremde Merchant-ID, State-Replay, Callback-CSRF, Berechtigungsentzug.
- Sandbox-End-to-End für Full/Partial Refund, Chargeback, Payout und Reconciliation.

### 4. Buchungslogik und Nebenläufigkeit

- Jede erlaubte und verbotene Kante der Zustandsmaschine je Rolle; verbotene Kanten liefern 409 ohne Seiteneffekt.
- Abgelehnte, stornierte, abgelaufene und noch nicht akzeptierte Buchungen dürfen nicht bezahlt/ausgeliefert werden.
- 100 parallele Reservierungen des letzten Slots müssen genau eine Bestätigung erzeugen.
- Optimistic-Lock-Konflikte, Hold-Ablauf und Wiederholung nach Timeout.
- Zeitzonen und Kalendergrenzen: DST vor/zurück, Mitternacht, Monats-/Jahreswechsel, Schaltjahr.
- Provider-Deaktivierung, Service-Deaktivierung, Preis-/Daueränderung zwischen Suche und Buchung.

### 5. Migration und Datenintegrität

- Leere PostgreSQL-Datenbank über alle Migrationen aufbauen.
- Repräsentative produktionsnahe Baseline auf jede Zielversion migrieren und nach Neustart validieren.
- Migration in der Mitte abbrechen; Anwendung darf nicht mit teilweisem Schema starten.
- Foreign Keys, Checks, Unique Constraints und Geldskalen direkt auf DB-Ebene prüfen.
- Nutzer-/Service-Löschung mit Reviews, Chats, Time Entries, Reports, Payments und Auditdaten.
- Backup vor Migration, Restore und Roll-forward unter dokumentiertem RPO/RTO.

### 6. XSS, Upload und Browserkontrollen

- Stored-XSS-Payloads in Kategorie, Name, Beschreibung, Chat, Review und allen Admin-Ansichten; Ausgabe muss Text bleiben.
- DOM-basierte Payloads über URL, Query, Fragment und API-Fehler.
- Verifizieren, dass Token weder `localStorage`/`sessionStorage` noch URL, Log, Referrer oder Telemetrie erreicht.
- CSP mit automatischem Header-Test und Browser-Report; keine notwendige `unsafe-inline`-/`unsafe-eval`-Ausnahme.
- Uploads: MIME-Spoofing, Polyglot, SVG, Dekompressionsbombe, riesige Dimensionen, EXIF, Malware-Testdatei, Quoten, parallele Uploads und unbefugter Abruf.

## P1-Testpaket

### Datenschutz

- Datenexport gegen ein maschinenlesbares Soll-Inventar vergleichen.
- Löschung/Anonymisierung samt Suchindex, Objekt-Storage, Cache, Logs und Analytics prüfen.
- Gesetzliche Retention und Legal Hold dürfen nicht umgangen werden.
- Restore eines alten Backups darf gelöschte Daten nicht unkontrolliert wieder veröffentlichen.
- Adminzugriffe und Support-Impersonation müssen vollständig auditierbar sein.

### Reliability und Operations

- Stripe, PayPal, SMTP, DB und Objekt-Storage jeweils langsam, nicht erreichbar, drosselnd und inkonsistent simulieren.
- Retry-Budget, Circuit Breaker, DLQ, Outbox und manuelles Replay prüfen.
- Prozesskill an jeder Transaktionsgrenze; Neustart muss einen eindeutigen Zustand herstellen.
- Readiness muss bei nicht betriebsfähiger Abhängigkeit korrekt reagieren, Liveness darf keine Restart-Schleife erzeugen.
- Alarmtests für Paymentabweichung, hohe 401/403-Rate, Admin-/Merchant-Änderung, Queue-Rückstau, DB-Sättigung und SLO-Verletzung.
- Logs auf JWTs, Cookies, Reset-Tokens, PayPal/Stripe-Secrets, IBAN und unnötige PII scannen.

### Accessibility und UX

- Automatisiert axe/Pa11y auf Login, Suche, Detail, Buchung, Checkout, Chat, Provider- und Adminjourneys.
- Manuell Tastatur-only: Reihenfolge, Skip Link, sichtbarer Fokus, Modalfalle/-rückgabe, Escape, Tabs und klickbare Karten.
- NVDA + Firefox/Chrome und VoiceOver + Safari für Namen/Rollen/Werte, Fehlermeldungen und Live Regions.
- 200 % Zoom, 400 % Reflow, High Contrast, reduzierte Bewegung und Touch-Ziele.
- Langsame/fehlerhafte API: verständliche, wiederholbare und nicht datenverlustende UI-Zustände.

## Testumgebungen

| Umgebung | Zweck | Muss enthalten |
|---|---|---|
| Unit | schnelle Domänenregression | keine Netzwerkabhängigkeit, deterministische Uhr/UUIDs |
| Component | Controller/Service/Repository | echte Security Filter, PostgreSQL-Testcontainer, Migrationen |
| Integration | Adapter und Events | lokale/simulierte PSP-/SMTP-/Storage-Dienste, Failure Injection |
| System/E2E | Releasefunktion | gebautes unverändertes Artefakt, alle drei Frontends, PostgreSQL, echte Auth-Flows |
| Staging | Produktionsfreigabe | prod-nahe Topologie, TLS, Secret Store, Sandbox-PSPs, Observability, anonymisierte Lastdaten |
| Production Smoke | minimale Verifikation | synthetische Accounts/Transaktionen, keine echten Kundendaten, sichere Rückabwicklung |

H2 darf für isolierte Unit-Tests bleiben, ist aber kein Ersatz für die PostgreSQL-Komponenten- und Migrationstests.

## Performance-, Capacity- und Soak-Plan

Die konkreten Kapazitätsziele müssen Produkt und Operations anhand erwarteter Nutzerzahlen festlegen. Als anfängliches Release-Gate werden empfohlen:

- p95 für nicht externe Read-Requests < 500 ms, p99 < 1 s unter vereinbarter Nennlast.
- p95 für interne Write-Verarbeitung < 750 ms; externe Checkout-Latenz separat messen.
- HTTP-5xx < 0,1 % unter Nennlast; keine ungeklärten Payment-/Ledger-Abweichungen.
- Keine unbeschränkten Listen; konstante Queryzahl pro Page und keine Connection-Pool-Sättigung.
- 8-Stunden-Soak ohne kontinuierliches Heap-, Thread-, Connection-, SSE-Emitter- oder DB-Wachstum.
- Spike auf 3× Nennlast mit kontrollierter Degradation und Recovery ohne Datenverlust.
- Separate Race-Szenarien für letzten Slot, Statuswechsel, Webhook-Replay und Settlement.

Lastberichte müssen Commit, Konfiguration, Datensatz, Infrastruktur, Durchsatz, Latenzverteilung, Fehler, Ressourcen und Query-Pläne enthalten. Ein Skript ohne aktuellen Ergebnisreport ist kein Nachweis.

## CI/CD-Gates

| Gate | Merge Request | Main | Release Candidate |
|---|---:|---:|---:|
| Compile/Lint/Unit | zwingend | zwingend | zwingend |
| PostgreSQL Component/Migration | zwingend | zwingend | zwingend |
| Authz-/Payment-P0-Regression | zwingend | zwingend | zwingend |
| Integriertes E2E | Kernsmoke | vollständig | vollständig |
| SAST/Secret/SCA/SBOM | zwingend | zwingend | finales Artefakt |
| Container/IaC Scan | bei Änderung | zwingend | zwingend |
| Accessibility Automation | Kernseiten | vollständig | vollständig + manuell |
| Performance | bei Hotpath-Änderung | Baseline | Last/Spike/Soak |
| Restore/Recovery | bei Datenänderung | regelmäßig | aktueller Drillnachweis |
| PSP Sandbox/Reconciliation | bei Payment-Änderung | täglich/regelmäßig | vollständiger Lauf |

Releaseblockierende Schwellen:

- 0 fehlgeschlagene oder übersprungene P0-Tests.
- 0 anwendbare Critical/High-Sicherheitslücken ohne genehmigte Ausnahme; für die bestätigten kritischen Exploitpfade sind keine Ausnahmen zulässig.
- 0 kritische/hohe Accessibility-Befunde auf Kernjourneys.
- 0 ungeklärte Ledger-/PSP-Reconciliation-Differenzen.
- Migration, Restore und Roll-forward innerhalb des festgelegten RPO/RTO bestanden.
- Flaky-Test-Rate < 1 % über 20 Läufe; kein Quarantäne-Test deckt einen Launch-Blocker ab.

## Testdaten und Isolation

- Fixtures müssen rollen-, mandanten- und ownership-spezifische Datensätze enthalten.
- PSP-/E-Mail-/Storage-Testkonten sind getrennt von Produktion; Secrets nur aus dem Test-Secret-Store.
- Tests dürfen keine echten personenbezogenen Daten verwenden; produktionsnahe Daten nur anonymisiert und mit dokumentierter Freigabe.
- Jeder Testlauf besitzt eindeutige IDs, löscht seine Daten sicher und kann parallel ausgeführt werden.
- Zeit, Zufall, externe Antworten und Eventreihenfolgen müssen kontrollierbar sein.

## Manuell zu prüfende Bereiche

Automatisierung ersetzt hier keine Fachprüfung:

1. Unabhängiger Penetrationstest für BOLA, Account Takeover, XSS, Upload, Business Logic und Payment.
2. Finance-Abnahme von Gebühren, Rundung, Steuern, Refund, Chargeback, Payout, Ledger und Reconciliation.
3. Legal-/Privacy-Prüfung von Rollenmodell, Informationspflichten, Consent, Retention, Löschung und Marketplace-Regeln.
4. Moderations-Tabletop mit Beweis, Sperre, Einspruch, Vier-Augen-Prinzip und Missbrauchsfällen.
5. Screenreader-/Keyboard-Test mit Menschen, die assistive Technologien routiniert nutzen.
6. Incident- und Disaster-Recovery-Drills einschließlich PSP-Ausfall, Token-Key-Leak und Datenabfluss.
7. Release-Day-Smoke und gezielte Beobachtung der ersten Transaktionen durch benannte Verantwortliche.

## Exit-Kriterien

Die Testlücke ist erst geschlossen, wenn jede P0/P1-Anforderung einem automatisierten oder ausdrücklich manuellen Test, einem Owner, einem aktuellen Ergebnis und einem Releaseartefakt zugeordnet ist. Testreports müssen unveränderlich archiviert, aus dem finalen Build erzeugt und im Releaseentscheid referenziert werden. „Tests grün“ ohne diese Rückverfolgbarkeit genügt nicht.
