# Market-Readiness-TODO

Stand: 2026-07-14  
Quelle: [MARKET_READINESS_AUDIT.md](MARKET_READINESS_AUDIT.md)  
Prioritäten: **P0** = blockiert jeden Marktstart, **P1** = vor öffentlicher Beta, **P2** = für stabilen Produktionsbetrieb, **P3** = Optimierung/Skalierung, **P4** = langfristige Verbesserung.  
Aufwand: XS (< 1 PT), S (1–3 PT), M (4–8 PT), L (9–15 PT), XL (> 15 PT). Schätzungen sind Teamaufwand ohne externe Prüf- oder Wartezeiten.

In jedem Eintrag benennt das Feld „Aufwand/Risiko“ zuerst den geschätzten Aufwand und danach ausdrücklich das **Risiko bei Nichtumsetzung**. Kombinierte Felder für Priorität/Kategorie und Aufwand/Risiko enthalten damit jeweils beide geforderten Angaben.

## Empfohlene Reihenfolge

1. Öffentliche Erreichbarkeit bis zur Behebung der P0-Punkte verhindern.
2. Schlüssel, Autorisierung, Payment-Bypässe und abhängige Bibliotheken absichern.
3. Geld-, Buchungs- und Provider-Onboarding als serverseitige Zustandsmaschinen umsetzen.
4. Datenbankmigrationen, Nebenläufigkeit, Ledger und Löschkonzept etablieren.
5. Kritische Regressionstests und eine produktionsnahe CI/CD-Pipeline als Gates einführen.
6. Datenschutz, Marketplace-Compliance, Monitoring, Runbooks und Barrierefreiheit abschließen.
7. Erst nach einem vollständigen Staging-Release und formaler Freigabe öffentlich starten.

## Epic 1 – Security

### MR-SEC-001 – JWT-Schlüssel kompromittiert behandeln und Rollen serverseitig bestimmen

- **Status:** REQUIRES MANUAL VERIFICATION
- **Abschlussdatum:** 2026-07-14 (Implementierung und automatisierte Tests)
- **Geänderte Komponenten:** `JwtUtil`, `JwtAuthenticationFilter`, JWT-Properties, Gradle-Testkonfiguration und JWT-Rotationsanleitung im `README.md`.
- **Hinzugefügte Tests:** Gültiges Token, ungültige/zu kurze Konfiguration, rotierter Schlüssel, falscher Issuer/Audience/Key-ID/Algorithmus, manipulierter Rollen-Claim und Datenbank-Rollenänderung; bestehender Sperrtest bleibt aktiv.
- **Verbleibende Einschränkungen:** Der Codepfad ist umgesetzt und getestet. Vor Schließung müssen ein neuer produktiver Schlüssel tatsächlich im Secret Store erzeugt, der kompromittierte Schlüssel aus allen Umgebungen entfernt, alle bestehenden Tokens durch Deployment invalidiert und die Rotation in Staging/Produktion nach dem dokumentierten Runbook im Vier-Augen-Prinzip verifiziert werden. Refresh/Revocation jenseits einer globalen Schlüsselrotation bleibt MR-AUTH-003.
- **Priorität/Kategorie:** P0 / Authentifizierung und Autorisierung
- **Beschreibung:** Der fest einkompilierte Schlüssel erlaubt Token-Fälschung; die Rolle wird aus einem selbst signierbaren Claim übernommen.
- **Technische Lösung:** Zufälligen, extern verwalteten Schlüssel mit Rotation einsetzen; bestehende Tokens invalidieren; Rollen bei jeder Anfrage aus der Datenbank laden oder über kurzlebige, versionierte Sessions binden; `kid`, Issuer und Audience validieren.
- **Betroffene Komponenten:** `JwtUtil`, `JwtAuthenticationFilter`, Security-Konfiguration, Secret Store, Deployment.
- **Abhängigkeiten:** Secret-Management und produktionsfähige Konfiguration.
- **Aufwand/Risiko:** M / kritisch: vollständige Account- und Admin-Übernahme.
- **Akzeptanzkriterien:** Kein Schlüssel im Repository oder Artefakt; alte Tokens sind ungültig; ein Token mit manipuliertem Rollen-Claim verleiht keine Rechte; Rotation ist dokumentiert und getestet.
- **Tests:** Token-Fälschung, falscher Issuer/Audience/Algorithmus, Rollenänderung, Sperrung und Schlüsselrotation.
- **Definition of Done:** Code, Secrets, Tests, Rotation-Runbook, Deployment und Security-Review sind abgeschlossen.

### MR-SEC-002 – Stored XSS schließen und Browser-Sicherheitsmodell härten

- **Status:** PARTIALLY COMPLETED
- **Abschlussdatum:** 2026-07-14 (Service-Kategorie)
- **Geänderte Komponenten:** `ServiceOfferingService` sowie Kategorieausgaben in Customer-App, Service-Detail, Provider-Profil und Provider-Dashboard.
- **Hinzugefügte Tests:** Service-Regressionen für erlaubte/normalisierte, leere, unbekannte und XSS-Kategorien bei Create/Update; HTTP-Test mit wiederholtem direktem XSS-Update und Persistenzprüfung; Playwright-Test auf Desktop und Mobile für Listen- und Detailausgabe als reinen Text.
- **Verbleibende Einschränkungen:** Der konkrete Stored-XSS-Vektor über `ServiceOffering.category` ist serverseitig und an allen bekannten Kategorie-Sinks geschlossen. Das Gesamtticket bleibt offen: weitere Freitext-/URL-/Attribut-Sinks müssen vollständig inventarisiert werden, JWTs liegen weiterhin in Web Storage beziehungsweise beim SSE in URLs, und CSP sowie eine HttpOnly-/BFF-Sessionarchitektur fehlen.
- **Priorität/Kategorie:** P0 / Application Security
- **Beschreibung:** Nicht vertrauenswürdige Kategorie- und Profildaten gelangen über `innerHTML` in mehrere Oberflächen; Tokens liegen in `localStorage`.
- **Technische Lösung:** DOM-Ausgabe ausschließlich kontextgerecht escapen bzw. `textContent` nutzen, Kategorien serverseitig erlaubnislisten, starke CSP ohne Inline-Ausnahmen setzen und Authentifizierung auf sichere HttpOnly/Secure/SameSite-Cookies oder gleichwertige BFF-Sessions migrieren.
- **Betroffene Komponenten:** Customer-, Provider- und Admin-JavaScript, Service-DTOs/Validierung, Security Header, Auth-Client.
- **Abhängigkeiten:** MR-SEC-001; CSP-kompatibler Frontend-Build.
- **Aufwand/Risiko:** L / kritisch: Token- und Account-Diebstahl.
- **Akzeptanzkriterien:** Payloads werden überall als Text angezeigt; keine Tokens in Web Storage oder URL; CSP-Report enthält keine erforderlichen Ausnahmen.
- **Tests:** Persistente XSS-Payloads in allen Freitextfeldern, DOM-XSS-Scan, CSP- und Cookie-Tests.
- **Definition of Done:** Alle Sinks inventarisiert, behoben, automatisiert geprüft und durch Penetrationstest bestätigt.

### MR-SEC-003 – Upload-Pipeline mit Quoten und sicherer Verarbeitung

- **Priorität/Kategorie:** P1 / Upload Security
- **Beschreibung:** Base64-Bilder werden ohne belastbare Byte-, Pixel-, Format- und Nutzerquote in der Datenbank gespeichert.
- **Technische Lösung:** Objekt-Storage mit zufälligen Schlüsseln, Magic-Byte-Prüfung, Decode/Re-Encode, Pixel- und Byte-Limits, Metadatenentfernung, Malwareprüfung, Rate Limits, ACL und Lifecycle verwenden.
- **Betroffene Komponenten:** Service-, Avatar- und Chat-Uploads, Datenmodell, Storage, CDN.
- **Abhängigkeiten:** Infrastruktur- und Datenschutzkonzept.
- **Aufwand/Risiko:** L / hoch: DoS, Speicherwachstum und schädliche Inhalte.
- **Akzeptanzkriterien:** Limits gelten vor vollständiger Dekodierung; SVG und unbekannte Formate werden abgewiesen; Objekte sind nicht erratbar; Löschfristen greifen.
- **Tests:** Polyglots, falsche MIME-Typen, Dekompressionsbomben, große Pixelmaße, Quoten, unbefugter Abruf und Löschung.
- **Definition of Done:** Pipeline, Quoten, Alarme, Lifecycle und Missbrauchstests sind produktiv verifiziert.

### MR-SEC-004 – Kritische/hohe Laufzeit-Abhängigkeiten aktualisieren

- **Status:** PARTIALLY COMPLETED
- **Abschlussdatum:** 2026-07-14 (Framework-Runtime, automatisches Versions-Gate und reproduzierbare Runtime-SBOM)
- **Geänderte Komponenten:** Gradle-Build mit Spring Boot 4.0.7, Spring Security 7.0.6, Jackson 3.1.4/2.21.5, Tomcat 11.0.24, Logback 1.5.37 sowie CycloneDX-Gradle-Plugin 3.2.4 mit kanonischer JSON-Ausgabe.
- **Hinzugefügte Tests:** `verifySecurityPatchedRuntime` prüft den tatsächlich aufgelösten Runtime-Classpath auf die festgelegten Patchversionen. `verifyCycloneDxSbom` validiert CycloneDX 1.6, Anwendungsmetadaten, Kernkomponenten/PURLs, den Ausschluss von Testkomponenten, lokalen Pfaden und Secret-Markern sowie das Fehlen instabiler Serialnummern und Zeitstempel. Beide Prüfungen sind an den Gradle-`check`-Lifecycle gebunden; zwei erzwungene Erzeugungen lieferten eine bytegleiche SHA-256-Summe.
- **Verbleibende Einschränkungen:** Die im Audit konkret benannten verwundbaren Frameworkversionen sind aus dem Runtime-Classpath entfernt; die kanonische Produktions-Runtime-SBOM ist lokal reproduzierbar. Ein erneuter OSV-Batchscan über 104 ausgewählte Maven-Komponenten meldet keine anwendbare Critical-/High-Schwachstelle; der einzige zurückgelieferte Datensatz GHSA-5jmj-h7xm-6q6v nennt Jackson 2.21.5, obwohl die veröffentlichte betroffene Versionsspanne bei `< 2.21.5` endet. Das Gesamtticket bleibt offen, bis kontinuierliches SCA im nicht umgehbaren CI-Gate, Scan und Archivierung der SBOM für das finale Releaseartefakt sowie dokumentierte Advisory-Ausnahmen vorhanden sind.
- **Priorität/Kategorie:** P0 / Supply Chain
- **Beschreibung:** Der OSV-Abgleich meldet 34 Datensätze, darunter kritische Treffer für Tomcat und Spring Boot; Anwendbarkeit ist pro CVE zu bestätigen.
- **Technische Lösung:** Spring Boot mindestens auf eine fehlerbereinigte 4.0.x-Version und transitiv Tomcat/Security/Jackson auf gepatchte Versionen aktualisieren; Dependency Locking, SBOM und kontinuierliches Scanning einführen.
- **Betroffene Komponenten:** `build.gradle`, Gradle Lockfiles, CI, Container-Image.
- **Abhängigkeiten:** Vollständige Regressionstests.
- **Aufwand/Risiko:** M / kritisch bis hoch.
- **Akzeptanzkriterien:** Keine anwendbare Critical/High-Schwachstelle ohne befristete, genehmigte Ausnahme; SBOM ist reproduzierbar.
- **Tests:** Backend-, Integrations-, Security- und Payment-Regression; SCA-Scan des finalen Artefakts.
- **Definition of Done:** Gepatchtes Artefakt, SBOM, Scanbericht und dokumentierte Ausnahmen sind freigegeben.

## Epic 2 – Authentifizierung und Autorisierung

### MR-AUTH-001 – Ownership für Service-Änderungen erzwingen

- **Status:** PARTIALLY COMPLETED
- **Abschlussdatum:** 2026-07-14
- **Geänderte Komponenten:** `SecurityConfig`, `ServiceOfferingController`, `ServiceOfferingService`.
- **Hinzugefügte Tests:** Service-Regressionen für Owner, Fremdzugriff, fehlende Ressource und gesperrten Provider; HTTP-Security-Integrationstests für Owner, fremden Provider, Customer, anonymen Zugriff und fehlende Ressource.
- **Verbleibende Einschränkungen:** Der klar abgegrenzte Teil „serverseitige Rollen- und Ownership-Prüfung für PUT/DELETE“ ist vollständig umgesetzt. Das Gesamtticket bleibt teilweise offen: FINDING-001 ermöglicht weiterhin Identitätsübernahme; ein persistentes Audit Event und eine ergänzte API-Dokumentation sind noch nicht vorhanden. Admins erhalten über diese Provider-Endpunkte bewusst keine Ausnahme; der separate Admin-Statuspfad ist nicht Teil dieser Aufgabe.
- **Priorität/Kategorie:** P0 / BOLA
- **Beschreibung:** Jeder authentifizierte Account kann fremde Services ändern oder löschen.
- **Technische Lösung:** Provider-Rolle und Eigentümerschaft zentral in Service/Policy prüfen; Objekt-ID niemals als Autorisierung verwenden; Admin-Ausnahme explizit auditieren.
- **Betroffene Komponenten:** `SecurityConfig`, `ServiceOfferingController`, `ServiceOfferingService`.
- **Abhängigkeiten:** MR-SEC-001.
- **Aufwand/Risiko:** S / kritisch.
- **Akzeptanzkriterien:** Fremde Objekte liefern 403 und bleiben unverändert; deaktivierte Provider sind ausgeschlossen; Admin-Eingriffe werden protokolliert.
- **Tests:** Rollen-/Owner-Matrix für PUT/DELETE inklusive ID-Tausch und deaktiviertem Account.
- **Definition of Done:** Policy, Negativtests, Audit Event und API-Dokumentation sind vorhanden.

### MR-AUTH-002 – Buchungs- und Review-IDOR vollständig schließen

- **Status:** PARTIALLY COMPLETED
- **Abschlussdatum:** 2026-07-14 (Review-Erstellung einschließlich Exactly-once und UUID-basierte Buchungslisten)
- **Geänderte Komponenten:** `SecurityConfig`, `ReviewController`, `ReviewService`, `Review`-/Booking-Repositories, Review-Entity und Schemaergänzung, zentrale Access-Denied-/Conflict-Fehlerbehandlung sowie `BookingController` und `BookingService`.
- **Hinzugefügte Tests:** Review-Service- und HTTP-Regressionen für Owner, Fremdzugriff, Rollen, Eingaben, Status und sequentiellen Replay; Parallelitätstest mit zehn gleichzeitigen Requests und genau einem Persistenz-/Mail-Effekt; direkter Datenbank-Constraint-Test; Booking-HTTP-Matrix für entfernte Customer-/Provider-ID-Pfade mit eigener, fremder und fehlender UUID sowie positive, datenseparierende `/me`-Regressionen für mehrere Customers und Provider.
- **Verbleibende Einschränkungen:** FINDING-006 ist einschließlich Exactly-once-Garantie geschlossen; auch die frei adressierbaren Buchungslisten aus FINDING-005 sind serverseitig geschlossen. Das Gesamtticket bleibt offen, weil rollenabhängige DTO-Minimierung, ein auditierter separater Admin-Zugriff, ein vollständiges Inventar aller objektbezogenen Endpunkte und die produktionsnahe PostgreSQL-Verifikation noch nicht nachgewiesen sind.
- **Priorität/Kategorie:** P0 / BOLA und Datenschutz
- **Beschreibung:** Buchungslisten/-details akzeptieren fremde Nutzer-IDs; Reviews können ohne Prüfung der aufrufenden Person erstellt werden.
- **Technische Lösung:** Principal als einzige Identitätsquelle verwenden; Beteiligung serverseitig prüfen; Admin-Zugriff separat autorisieren und auditieren; DTOs minimieren.
- **Betroffene Komponenten:** Booking- und Review-Controller/Services/DTOs.
- **Abhängigkeiten:** MR-SEC-001.
- **Aufwand/Risiko:** M / kritisch bis hoch.
- **Akzeptanzkriterien:** Nutzer sehen und bewerten ausschließlich zulässige Buchungen; keine Zahlungs- oder Notizdaten fremder Parteien werden offenbart.
- **Tests:** Horizontale/vertikale Zugriffsmatrix, UUID-Raten, abgeschlossene/nicht abgeschlossene Buchung, Admin-Audit.
- **Definition of Done:** Sämtliche objektbezogenen Endpunkte sind inventarisiert, policy-geschützt und negativ getestet.

### MR-AUTH-003 – Sessions, Passwörter und Missbrauchsschutz produktionsreif machen

- **Priorität/Kategorie:** P1 / Account Security
- **Beschreibung:** 24-Stunden-Tokens sind nicht widerrufbar; Login verrät Konten; es fehlen Passwortpolicy, Rate Limits, Lockout und MFA für privilegierte Rollen.
- **Technische Lösung:** Kurzlebige Sessions mit Refresh-Rotation und Revocation, generische Loginfehler, IP-/Account-Limits, adaptive Sperren, Passwortregeln und MFA für Admin/Provider einführen; Reset-Tokens gehasht speichern.
- **Betroffene Komponenten:** Auth-Service, Tokenmodell, UI, Mail, Redis/Session Store.
- **Abhängigkeiten:** MR-SEC-001/002, Monitoring.
- **Aufwand/Risiko:** L / hoch.
- **Akzeptanzkriterien:** Logout, Sperrung und Passwortwechsel widerrufen Sessions; Brute Force alarmiert; Adminzugriff erfordert MFA.
- **Tests:** Credential Stuffing, Enumeration, Refresh-Replay, Logout, Reset-Replay und MFA-Recovery.
- **Definition of Done:** Controls, Alarme, Recovery-Prozess und Tests sind dokumentiert und abgenommen.

## Epic 3 – Payments

### MR-PAY-001 – Clientseitiges `mark-paid` entfernen

- **Status:** COMPLETED
- **Abschlussdatum:** 2026-07-14
- **Geänderte Komponenten:** `BookingController`, `BookingService` und `SecurityConfig`.
- **Hinzugefügte Tests:** HTTP-Regression für anonymen Zugriff sowie Customer, Provider und Admin; wiederholter Request; persistente Prüfung, dass `paymentStatus`, `settlementStatus`, `paidAt` und `paymentNote` unverändert bleiben.
- **Verbleibende Einschränkungen:** Der konkrete Customer-Bypass ist vollständig entfernt. Die legitimen, aber noch nicht gesamthaft als Ledger/State-Machine abgesicherten Stripe-, PayPal- und Provider-Offline-Zahlungspfade bleiben Gegenstand von MR-PAY-003 und MR-PAY-005.
- **Priorität/Kategorie:** P0 / Payment Security
- **Beschreibung:** Ein Customer kann eine Buchung ohne PSP-Nachweis auf `PAID` setzen und Leistungsauslieferung freischalten.
- **Technische Lösung:** Endpunkt entfernen; Zahlung ausschließlich aus verifiziertem, idempotentem Provider-Webhook bzw. serverseitiger Capture-Bestätigung buchen.
- **Betroffene Komponenten:** Booking-Controller/-Service, Frontend, Payment-Webhook.
- **Abhängigkeiten:** MR-PAY-003.
- **Aufwand/Risiko:** S / kritisch: unbezahlte Leistung.
- **Akzeptanzkriterien:** Kein Client kann Zahlungsstatus setzen; nur verifizierte Provider-Ereignisse verändern Ledger und Buchung atomar.
- **Tests:** Direkter Request, Replay, falscher Betrag/Provider/Status, gültiger Webhook.
- **Definition of Done:** Route entfernt/gesperrt, Migration und Regressionstest sind ausgerollt.

### MR-PAY-002 – PayPal-Provideridentität serverseitig verifizieren

- **Status:** PARTIALLY COMPLETED
- **Abschlussdatum:** 2026-07-15 (zusätzlich Checkout-Payee-Abgleich; servergebundener, kurzlebiger und einmaliger State für den aktiven Partner-Return sowie frühere Teilfixes bleiben bestehen)
- **Geänderte Komponenten:** `UserService`, `User`/`UserRepository`/Schemaergänzung, `ProviderProfileController`, `ProviderPayPalOnboardingService`, `PayPalOnboardingStateService`, `PayPalService`, `BookingService`, `ServiceOfferingService`, `SecurityConfig`, PayPal-Onboarding-/Identity-DTOs sowie Provider-Profil-Frontend.
- **Hinzugefügte Tests:** Bestehende Profil-/Return-Regressionen plus State-Erzeugung mit 256 Bit Entropie, ausschließliche Hash-/Ablaufpersistenz, Ersetzung bei Neustart, Return-URL ohne Provider-ID, gültiger Abschluss, fehlender/zu langer/ungültiger/abgelaufener/fremder State, unbekannter und gesperrter Provider, Rollen-/Anonym-Matrix, Replay sowie zehn parallele Konsumenten mit genau einem erfolgreichen Consume; Playwright-Nachweise auf Desktop/Mobile für ausschließlich `{state}` am Abschlussendpunkt und callbackfreien Abbruch ohne State; Checkout-Happy-Path mit exaktem `merchant_id`-Payee sowie Negativmatrix für `ACTION_REQUIRED`, fehlende/negative Permission- und E-Mail-Flags, fehlenden Merchant, bloßen E-Mail-Fallback und fehlende Booking-/Provider-Zuordnung jeweils ohne PayPal-Aufruf; Booking- und Angebots-Regressionen für die zentrale Verfügbarkeitsregel.
- **Verbleibende Einschränkungen:** Der allgemeine `PUT /api/users/{id}`-Mass-Assignment-Pfad, beide clientvertrauenden Legacy-Returns, die fehlende State-/Nonce-Bindung des aktiven Partner-Returns und der unsichere Checkout-Payee-Fallback sind geschlossen. Der aktive State wird 15 Minuten gültig, ausschließlich als SHA-256-Hash gespeichert, pessimistisch genau einmal konsumiert und bindet die Rückkehr an den authentifizierten Provider; erst danach wird der Sellerstatus serverseitig bei PayPal gelesen. Anzeige, Booking-Vorprüfung und PayPal-Order-Erstellung erlauben Checkout nun ausschließlich bei `CONNECTED`, positiv serverseitig gespeicherten Permission-/E-Mail-Bestätigungsflags und nichtleerer Merchant-ID; der Request verwendet nur diese Merchant-ID und nie die PayPal-E-Mail. Das Gesamtticket bleibt offen, bis Seller- und Berechtigungsstatus vollständig fachlich gegen die PayPal-Partner-API abgenommen, Empfängeränderungen auditiert und Sandbox-/Produktionsabnahme dokumentiert sind. Die Eligibility beruht zwischen Statusaktualisierungen auf dem zuletzt serverseitig gespeicherten PayPal-Stand; ein Berechtigungsentzug wird noch nicht bei jedem Checkout live abgefragt. Die Schemaergänzung nutzt bis MR-DB-001 weiterhin das bestehende Startup-DDL und ist noch nicht als Flyway-/PostgreSQL-Migration verifiziert.
- **Priorität/Kategorie:** P0 / Payment Routing
- **Beschreibung:** Provider können Merchant-ID und Bestätigungsflags selbst setzen; Auszahlungen können umgeleitet werden.
- **Technische Lösung:** OAuth/Partner-Referral-Callback mit State/Nonce und serverseitiger PayPal-Abfrage nutzen; Merchant-Zuordnung unveränderbar auditieren; manuelle Änderung nur als Vier-Augen-Adminprozess.
- **Betroffene Komponenten:** User-Profil, PayPal-Onboarding, Provider-UI, Audit Log.
- **Abhängigkeiten:** PayPal-Partnerkonfiguration und rechtliche Freigabe.
- **Aufwand/Risiko:** L / kritisch.
- **Akzeptanzkriterien:** Clientwerte können Payee nicht beeinflussen; Merchantstatus stammt nachweisbar von PayPal; Änderungen werden alarmiert.
- **Tests:** Callback-Fälschung, CSRF/state-Replay, fremde Merchant-ID, Entzug von Berechtigungen.
- **Definition of Done:** Sandbox- und Produktions-Onboarding, Audit und Recovery sind end-to-end abgenommen.

### MR-PAY-003 – Unveränderliches Ledger, Idempotenz und Webhook-State-Machine

- **Status:** PARTIALLY COMPLETED
- **Abschlussdatum:** 2026-07-15 (atomarer synchroner PayPal-Capture-Pfad, Stripe-Event-ID-Deduplizierung sowie monotone Stripe-Completed-/Failed-Policy)
- **Geänderte Komponenten:** `BookingService.capturePayPalPayment`, gemeinsame pessimistische Zustandsabfrage im `BookingRepository`, zentrale 409-Fehlerabbildung, `StripeConnectService.handleWebhook` einschließlich Completed-/Failed-Verarbeitung, persistente `StripeWebhookEvent`-Inbox mit Repository und Transaktionsservice, Startup-Schema sowie Booking-/Stripe-Service-, HTTP-Security- und Parallelitätstests.
- **Hinzugefügte Tests:** Erfolgreicher PayPal-Capture, Replay-/Negativmatrix und zehn parallele Capture-Requests mit genau einem PSP-/Mail-Effekt. Für Stripe: echt HMAC-signierter `checkout.session.completed`-Normalfall mit 2xx-Replay und genau einer Status-/Mailwirkung; ungültige Signatur/Event-ID und Fachfehler-Retry; zehn parallele Beanspruchungen derselben Event-ID; leere/fremde Session- und Payment-Intent-ID, falscher Paymentprovider und unzulässiger Ausgangszustand ohne Mutation; `payment_intent.payment_failed`-Normalfall/Replay und entsprechende Negativmatrix; beide Completed-/Failed-Reihenfolgen sowie ein paralleler Race mit deterministischem Endzustand `PAID`.
- **Verbleibende Einschränkungen:** Der synchrone PayPal-Capture ist für Provider, Order, Ausgangszustand, `COMPLETED` und Capture-ID pessimistisch abgesichert. Signierte Stripe-Events beanspruchen ihre validierte Provider-Event-ID transaktional über einen eindeutigen Inbox-Constraint. Completed und Failed laden die Buchung über denselben pessimistischen Lock, verlangen `CARD` und die gespeicherte Session-/Payment-Intent-Zuordnung; Failed darf nur `CHECKOUT_CREATED → FAILED`, Completed darf `CHECKOUT_CREATED|FAILED → PAID`, und weder Replay noch verspätetes Failed setzt `PAID` zurück. Das Gesamtticket bleibt offen: Betrag, Währung, Booking-ID, Payee/Connected Account und Preisgrundlage werden nicht vollständig gegen Providerdaten geprüft; weitere Eventtypen sowie Refund-/Dispute-Lebenszyklen besitzen keine vollständige Reihenfolgepolicy. Das nichttransaktionale Mail-System kann bei einem Absturz zwischen Mailversand und DB-Commit erneut senden. Checkout-/Provider-Idempotency-Key, Outbox, Ledger, Reconciliation, versionierte DB-Migration, PostgreSQL- und Sandboxverifikation fehlen ebenfalls.
- **Priorität/Kategorie:** P0 / Financial Integrity
- **Beschreibung:** Paymentstatus ist ein veränderbares Feld; Webhook-Deduplizierung, Ereignisreihenfolge und belastbare Abgleiche fehlen.
- **Technische Lösung:** Double-Entry- oder gleichwertiges append-only Ledger, unique Provider-Event-IDs, Idempotency Keys, erlaubte Übergänge, atomare Verarbeitung und Inbox/Outbox implementieren; Betrag, Währung, Booking-ID und Payee prüfen.
- **Betroffene Komponenten:** Stripe/PayPal, Booking, Settlement, Datenbank, Jobs.
- **Abhängigkeiten:** MR-DB-001/002.
- **Aufwand/Risiko:** XL / kritisch.
- **Akzeptanzkriterien:** Replay erzeugt keine Doppelbuchung; out-of-order Events enden deterministisch; jede Statusänderung ist revisionsfähig.
- **Tests:** Replay, Parallelität, Timeout nach Capture, falsche Signatur/Betrag/Währung/Payee, Reconciliation.
- **Definition of Done:** Fachlich abgenommene Zustandsmaschine, Ledger-Migration, Recovery-Job und Auditnachweis existieren.

### MR-PAY-004 – Geldwerte und Preis-Snapshots korrekt modellieren

- **Priorität/Kategorie:** P0 / Financial Correctness
- **Beschreibung:** `Double`, fehlende Buchungswährung und aktueller statt gebuchter Servicepreis erlauben Rundungs- und Preisänderungsfehler.
- **Technische Lösung:** Minor Units als Integer oder `BigDecimal` mit fester Skala, ISO-Währung, unveränderlichen Angebots-/Steuer-/Gebührensnapshot und zentraler Rundungsregel verwenden.
- **Betroffene Komponenten:** Service, Booking, Checkout, DTOs, DB-Migration, Rechnung.
- **Abhängigkeiten:** MR-PAY-003, Steuer-/Gebührenmodell.
- **Aufwand/Risiko:** L / kritisch.
- **Akzeptanzkriterien:** Summe bleibt nach Preisänderung stabil; Rundung ist PSP- und rechnungskonsistent; Mischwährungen sind unmöglich.
- **Tests:** Grenzwerte, Steuern/Gebühren, Rundung, Preisänderung, Migration bestehender Daten.
- **Definition of Done:** Domänenmodell, Migration, Vergleichsbericht und Finance-Abnahme sind abgeschlossen.

### MR-PAY-005 – Refunds, Disputes, Payouts und tägliche Abstimmung

- **Priorität/Kategorie:** P1 / Payment Operations
- **Beschreibung:** Rückerstattung, Chargeback, Teilrefund, echte Auszahlung und Reconciliation fehlen.
- **Technische Lösung:** Lebenszyklen und Ledger-Einträge je Vorgang, Webhook-Handling, Admin-Workflows mit Vier-Augen-Prinzip, tägliche PSP-Abstimmung und Abweichungsalarm implementieren.
- **Betroffene Komponenten:** Payments, Admin, Settlement, Ledger, Monitoring.
- **Abhängigkeiten:** MR-PAY-003/004, Rechts-/Finance-Entscheidungen.
- **Aufwand/Risiko:** XL / hoch.
- **Akzeptanzkriterien:** Voll-/Teilrefund und Dispute sind nachweisbar; Abweichungen werden innerhalb eines Tages erkannt; kein manueller Status ohne Audit.
- **Tests:** Refund/Chargeback/Payout-Sandbox, Doppelrequest, Fehler und Recovery.
- **Definition of Done:** Finance, Support und Security haben Prozesse und Runbooks abgenommen.

## Epic 4 – Booking

### MR-BOOK-001 – Strikte Buchungszustandsmaschine

- **Status:** PARTIALLY COMPLETED
- **Abschlussdatum:** 2026-07-15 (Checkout-Guard, atomare Provider-Statusübergänge und gemeinsame Checkout-/Statussperre)
- **Geänderte Komponenten:** `BookingService.createCheckout`, `BookingService.updateBookingStatus`, gemeinsame pessimistisch sperrende Zustandsabfrage im `BookingRepository`, bestehende zentrale `ConflictException`-API-Abbildung sowie Booking-Service-, HTTP-Security- und Parallelitätstests.
- **Hinzugefügte Tests:** Erfolgreiche Offline- und PayPal-Checkouts aus `ACCEPTED`; Checkout-Negativmatrix für `PENDING`, `REJECTED`, `COMPLETED`, `CANCELLED`, null, leer und unbekannt einschließlich Replay; vollständige 5×5-Service-Matrix der Provider-Statusübergänge mit ausschließlich `PENDING → ACCEPTED|REJECTED` und `ACCEPTED → COMPLETED`; HTTP-Matrix für Rollen, Ownership, fehlende Ressource, ungültige Eingaben, erlaubte Übergänge und 409-Replay; zehn konkurrierende Annahme-/Ablehnungsentscheidungen mit genau einer Änderung und genau einer Statusmail; deterministisch gesteuerter Checkout-vs.-Completion-Race, der das Warten am gemeinsamen Lock und den Erhalt beider committed Änderungen nachweist.
- **Verbleibende Einschränkungen:** `POST /api/bookings/{id}/checkout` ist fail-closed auf exakt `ACCEPTED` begrenzt. Checkout und `PUT /api/bookings/{id}/status` laden die Buchung nun über denselben pessimistischen Zeilen-Lock; parallele Checkout-/Provider-Statusänderungen werden damit serialisiert und überschreiben weder fachlichen Status noch Checkoutfelder. Provider-Updates erlauben ausschließlich `PENDING → ACCEPTED|REJECTED` sowie `ACCEPTED → COMPLETED`; alle anderen gültigen Paare bleiben unverändert und liefern 409. Das Gesamtticket bleibt offen: Checkout-Replay/PSP-Idempotenz, PayPal-Capture, Offline-`record-payment`, Work, Delivery und weitere Übergänge sind nicht vollständig abgesichert. Der Lock bleibt während des externen Checkout-Aufrufs innerhalb der bestehenden Transaktion gehalten; Outbox-/Timeout-/Recovery-Architektur, Akteur-/Grund-/Zeithistorie, DB-Constraints und PostgreSQL-Verifikation fehlen weiterhin.
- **Priorität/Kategorie:** P0 / Business Logic
- **Beschreibung:** Statuswechsel prüfen den Ausgangszustand nicht; abgelehnte oder offene Buchungen können bezahlt werden.
- **Technische Lösung:** Explizite Transitionstabelle mit Rollen, Guards, Zahlungsvoraussetzungen und atomaren Compare-and-Set-Updates; Historie append-only speichern.
- **Betroffene Komponenten:** Booking-Service/-Entity/-Controller, Payment, Delivery.
- **Abhängigkeiten:** MR-PAY-003, MR-DB-002.
- **Aufwand/Risiko:** L / kritisch.
- **Akzeptanzkriterien:** Nur definierte Übergänge sind möglich; ungültige Übergänge liefern 409; jede Änderung hat Akteur, Grund und Zeit.
- **Tests:** Vollständige Transition-/Rollenmatrix, Parallelupdates, Replay und Recovery.
- **Definition of Done:** Fachmodell, Migration, API-Fehler und Tests sind freigegeben.

### MR-BOOK-002 – Verfügbarkeit und Überbuchung transaktionssicher verhindern

- **Priorität/Kategorie:** P0 / Concurrency
- **Beschreibung:** Es gibt kein Kapazitätsmodell, keine Slot-Reservierung und keine DB-Sperre.
- **Technische Lösung:** Provider-Verfügbarkeit, Zeitzone, Dauer/Puffer und temporäre Holds modellieren; Ausschluss-/Unique-Constraints oder serialisierbare Reservierung einsetzen.
- **Betroffene Komponenten:** Booking, Availability, DB, Customer UI.
- **Abhängigkeiten:** Produktentscheidung zu Slots und Kapazität; MR-DB-002.
- **Aufwand/Risiko:** XL / kritisch.
- **Akzeptanzkriterien:** 100 parallele Requests auf den letzten Slot erzeugen genau eine bestätigte Reservierung; Holds laufen sicher ab.
- **Tests:** Race Tests auf PostgreSQL, Sommerzeit, Mitternacht, Storno und Hold-Ablauf.
- **Definition of Done:** Kapazitätsregeln, Datenbankgarantie, Lasttest und Betriebsmetriken sind vorhanden.

### MR-BOOK-003 – Storno, Umbuchung, No-show und Gebührenregeln

- **Priorität/Kategorie:** P1 / Marketplace Operations
- **Beschreibung:** Wesentliche Lebenszyklus- und Konfliktfälle sind nicht definiert.
- **Technische Lösung:** Versionierte Policies, Fristen, Rollen, Gebühren, Refund-Verknüpfung, Benachrichtigungen und Einspruchspfad implementieren.
- **Betroffene Komponenten:** Booking, Payment, Notifications, Admin, Rechtstexte.
- **Abhängigkeiten:** MR-PAY-005 und juristische Entscheidung.
- **Aufwand/Risiko:** L / hoch.
- **Akzeptanzkriterien:** Jede Regel ist transparent vor Buchung, reproduzierbar berechnet und auditierbar.
- **Tests:** Grenzzeitpunkte, beide Rollen, Provider-Ausfall, Teilrefund, Zeitzonen.
- **Definition of Done:** Produkt, Legal, Finance und Support haben Regeln und Tests abgenommen.

## Epic 5 – Datenschutz

### MR-PRIV-001 – Vollständiges Datenschutz- und Betroffenenrechteprogramm

- **Priorität/Kategorie:** P1 / Privacy
- **Beschreibung:** Consent-Versionierung, Export, Aufbewahrung, Löschung/Anonymisierung, Legal Hold und Adminzugriffs-Audit fehlen.
- **Technische Lösung:** Dateninventar und Rechtsgrundlagen erstellen; versionierte Einwilligungen, DSAR-Export, abgestufte Retention, sichere Lösch-/Anonymisierungsjobs, Legal Hold und Zugriffsaudit umsetzen.
- **Betroffene Komponenten:** Alle personenbezogenen Tabellen, Admin, Backups, Storage, Rechtstexte.
- **Abhängigkeiten:** Datenschutzbeauftragte/r und juristische Vorgaben; MR-DB-001.
- **Aufwand/Risiko:** XL / hoch, regulatorisch.
- **Akzeptanzkriterien:** Export und Löschung erfüllen definierte SLA; gesetzlich aufzubewahrende Finanzdaten werden getrennt; Backup-Löschung ist dokumentiert.
- **Tests:** Vollständigkeitsabgleich, Rechteprüfung, Löschung mit abhängigen Daten, Restore alter Backups.
- **Definition of Done:** DPIA/Verzeichnis, technische Controls, Runbooks und Legal-Freigabe liegen vor.

## Epic 6 – Datenbank

### MR-DB-001 – Versionierte Migrationen und sichere Löschsemantik

- **Priorität/Kategorie:** P0 / Data Integrity
- **Beschreibung:** `ddl-auto=update` und fehlertolerantes Startup-DDL sind nicht reproduzierbar; Löschpfade berücksichtigen Abhängigkeiten unvollständig.
- **Technische Lösung:** Flyway/Liquibase mit immutable Migrationen, Validierung und Roll-forward-Strategie; Foreign Keys und fachliche Archive/Anonymisierung statt ad-hoc Hard Deletes.
- **Betroffene Komponenten:** Konfiguration, `SchemaMigrationConfig`, Entities, alle Löschservices, Deployment.
- **Abhängigkeiten:** MR-PRIV-001, Backup/Restore.
- **Aufwand/Risiko:** L / kritisch.
- **Akzeptanzkriterien:** Leere und bestehende DB migrieren deterministisch; Fehler stoppen den Start; Löschung verletzt keine Referenzen und verliert keine Finanzspur.
- **Tests:** Migration ab jeder unterstützten Version, Failure Injection, Roll-forward, Löschgraph, Backup-Restore.
- **Definition of Done:** Migrationen, Baseline, Prüfsummen, Runbook und Staging-Probelauf sind abgeschlossen.

### MR-DB-002 – Constraints, Optimistic Locking und Indexprüfung

- **Priorität/Kategorie:** P1 / Data Integrity und Performance
- **Beschreibung:** Fachliche Status-, Geld- und Nebenläufigkeitsgarantien liegen überwiegend nur im Anwendungscode.
- **Technische Lösung:** Check-/Unique-/FK-Constraints, `NOT NULL`, `@Version`, passende Indizes und atomare Updates anhand realer Query-Pläne ergänzen.
- **Betroffene Komponenten:** Entities, Migrationen, Repositories.
- **Abhängigkeiten:** MR-BOOK-001/002 und MR-PAY-003/004.
- **Aufwand/Risiko:** M / hoch.
- **Akzeptanzkriterien:** Ungültige Zustände sind auch direkt in der DB unmöglich; Konflikte werden als 409 behandelt; kritische Queries nutzen erwartete Indizes.
- **Tests:** Constraint-, Race- und `EXPLAIN ANALYZE`-Tests auf PostgreSQL.
- **Definition of Done:** Schema-Review, Migration, Tests und Produktionsmetriken sind fertig.

## Epic 7 – Performance

### MR-PERF-001 – Pagination, N+1-Beseitigung und belastbare Leistungsziele

- **Priorität/Kategorie:** P2 / Performance
- **Beschreibung:** Admin-/Booking-Listen sind unpaginiert; Service-Mapping erzeugt wiederholte Bewertungs- und Detailabfragen.
- **Technische Lösung:** Cursor-/Page-Pagination, aggregierte Queries/Projections, gezieltes Fetching, begrenzte Caches und Query-Metriken einführen.
- **Betroffene Komponenten:** Service-, Booking- und Admin-Repositories/APIs/UI.
- **Abhängigkeiten:** Produktionsähnlicher Datensatz, Monitoring.
- **Aufwand/Risiko:** M / mittel bis hoch bei Wachstum.
- **Akzeptanzkriterien:** Bei 100k Services/1M Buchungen bleiben p95 Read < 500 ms und DB-Queryzahl pro Listenseite konstant; keine unbeschränkte Liste.
- **Tests:** Query Count, k6 Last/Spike/Soak, Cache-Stale- und Pagination-Konsistenztests.
- **Definition of Done:** Baseline, Zielwerte, Lastreport, Dashboards und Kapazitätsgrenzen sind dokumentiert.

## Epic 8 – Stabilität

### MR-REL-001 – Timeouts, Retry-Grenzen und asynchrone Recovery

- **Priorität/Kategorie:** P1 / Reliability
- **Beschreibung:** HTTP-/SMTP-Clients haben unklare Timeouts; externe Aufrufe laufen in DB-Transaktionen; Outbox, DLQ und Recovery fehlen.
- **Technische Lösung:** Connect/Read/Total-Timeouts, begrenzte Retries mit Jitter nur für idempotente Vorgänge, Circuit Breaker, Transactional Outbox/Inbox, Worker, DLQ und Wiederanlaufjobs implementieren.
- **Betroffene Komponenten:** PayPal, Stripe, Mail, Booking, Webhooks, SSE.
- **Abhängigkeiten:** MR-PAY-003, MR-OBS-001.
- **Aufwand/Risiko:** L / hoch.
- **Akzeptanzkriterien:** Externe Ausfälle blockieren keine DB-Transaktion; kein Ereignis geht verloren oder wird doppelt wirksam; Recovery ist operativ möglich.
- **Tests:** Timeout, Netzwerkabbruch, Prozessabsturz an Commit-Grenzen, Provider-Drosselung, DLQ-Replay.
- **Definition of Done:** Resilience-Tests, Alarmierung und Recovery-Runbook sind in Staging bestanden.

## Epic 9 – Monitoring

### MR-OBS-001 – Observability, Security-Audit und Alarmierung

- **Priorität/Kategorie:** P1 / Operations
- **Beschreibung:** Strukturierte Logs, Korrelations-IDs, zentrale Metriken/Traces, fachliche Alarme und manipulationsgeschützte Audit Events fehlen.
- **Technische Lösung:** JSON-Logs ohne Geheimnisse/PII, Request-/Trace-ID, RED/USE- und Business-Metriken, zentrale Speicherung, SLOs, Alert-Routing und append-only Audit Store einführen.
- **Betroffene Komponenten:** Backend, Proxy, DB, Payment, Admin, Plattform.
- **Abhängigkeiten:** Produktionsplattform und Datenschutzkonzept.
- **Aufwand/Risiko:** L / hoch.
- **Akzeptanzkriterien:** Paymentfehler, Rechteverletzungen, ungewöhnliche Onboarding-Änderungen und SLO-Verletzungen alarmieren innerhalb definierter Zeit; Logs enthalten keine Tokens.
- **Tests:** Synthetische Alarme, Log-Scrubbing, Trace über Checkout/Webhook, Audit-Manipulationsprüfung.
- **Definition of Done:** Dashboards, SLOs, On-call-Routing, Retention und Alarmtests sind abgenommen.

## Epic 10 – Testing

### MR-TEST-001 – Kritische Security-/Payment-Regression als Merge-Gate

- **Priorität/Kategorie:** P0 / Teststrategie
- **Beschreibung:** Grüne Tests decken die bestätigten Launch-Blocker nicht ab.
- **Technische Lösung:** Negativtests für jede Authz-Matrix, XSS, JWT, Payment-Replay/-Manipulation, Buchungsübergänge und Geldrundung ergänzen; bei Fehlern Merge verhindern.
- **Betroffene Komponenten:** Backendtests, Playwright, Security-Test-Harness, CI.
- **Abhängigkeiten:** jeweilige P0-Fixes.
- **Aufwand/Risiko:** L / kritisch.
- **Akzeptanzkriterien:** Jeder Critical/High-Fund hat mindestens einen vor dem Fix fehlschlagenden Regressionstest; Security-Gate ist nicht optional.
- **Tests:** Siehe `TEST_GAP_ANALYSIS.md`.
- **Definition of Done:** Tests laufen deterministisch in CI, erzeugen Reports und blockieren den Merge.

### MR-TEST-002 – PostgreSQL- und echte Systemtests

- **Priorität/Kategorie:** P1 / Integration und E2E
- **Beschreibung:** H2 und gemockte APIs prüfen weder Produktions-SQL noch echte Auth-, Payment- und Browserintegration.
- **Technische Lösung:** Testcontainers/PostgreSQL, Migrationstests, isolierte PSP-Sandboxes und vollständig integrierte Playwright-Flows mit E-Mail-Capture einführen.
- **Betroffene Komponenten:** Testprofile, CI, Datenbank, Frontends, Payment/Mail.
- **Abhängigkeiten:** MR-DB-001, Test-Secrets.
- **Aufwand/Risiko:** L / hoch.
- **Akzeptanzkriterien:** Kernjourneys laufen gegen das gebaute Backend und PostgreSQL; keine API-Mocks im Release-Smoke.
- **Tests:** Registrierung bis Settlement/Refund, Rollen, Migration, Neustart und Recovery.
- **Definition of Done:** Reproduzierbare Testumgebung, Datenbereinigung und Artefakte sind dokumentiert.

### MR-TEST-003 – Last-, Soak-, Chaos- und Accessibility-Gates

- **Priorität/Kategorie:** P2 / Non-functional Testing
- **Beschreibung:** Vorhandene k6-Skripte liefern keine Freigabebaseline; Soak, Paymentlast, Chaos und automatisierte WCAG-Prüfung fehlen.
- **Technische Lösung:** Repräsentative Szenarien, feste SLO-Schwellen, 8h-Soak, konkurrierende Buchung/Payment, Dependency-Failure und axe/Pa11y in CI/Staging ausführen.
- **Betroffene Komponenten:** k6, Playwright, Staging, Observability.
- **Abhängigkeiten:** MR-BOOK-002, MR-OBS-001, MR-UX-001.
- **Aufwand/Risiko:** M / mittel.
- **Akzeptanzkriterien:** Keine SLO-/Error-Budget-Verletzung; keine kritischen WCAG-Automationstreffer; Recovery-Ziele werden erreicht.
- **Tests:** Definierte Suite und manuelle Screenreader-/Keyboard-Runde.
- **Definition of Done:** Signierter Ergebnisreport ist Teil des Release-Evidence-Pakets.

## Epic 11 – DevOps und Release Engineering

### MR-DEVOPS-001 – Sichere Produktionsprofile, CI und Supply-Chain-Gates

- **Priorität/Kategorie:** P0 / DevSecOps
- **Beschreibung:** Unsichere Defaults, `ddl-auto=update`, SQL-Logging und Sandboxwerte können in Produktion aktiv werden; CI fehlt.
- **Technische Lösung:** Fail-closed Prod-Profil ohne Defaults, Secret Store, Umgebungsschema, Branch Protection, Build/Test/SAST/SCA/Secret-Scan/SBOM/Image-Scan und signierte Artefakte einführen.
- **Betroffene Komponenten:** Properties, Gradle, Repository, CI, Secrets.
- **Abhängigkeiten:** MR-SEC-004, MR-DB-001.
- **Aufwand/Risiko:** L / kritisch.
- **Akzeptanzkriterien:** Produktion startet ohne Pflichtsecrets nicht; Swagger/H2/SQL-Debug sind aus; jeder Commit wird reproduzierbar geprüft; Critical/High blockieren.
- **Tests:** Konfigurationsnegativtest, Secret-Leak-Test, Pipeline-Failure-Gates, Artefaktverifikation.
- **Definition of Done:** Geschützte Pipeline und dokumentierte Ausnahme-/Freigaberegel sind aktiv.

### MR-DEVOPS-002 – Reproduzierbares Deployment, TLS, Backups und Rollback

- **Priorität/Kategorie:** P1 / Operations
- **Beschreibung:** Es gibt kein App-Image, IaC, Staging/Prod-Deployment, TLS-/Proxy-Konzept oder erprobten Restore/Rollback.
- **Technische Lösung:** Minimales non-root OCI-Image, IaC, TLS/HSTS, Network Policies, Health/Readiness, Blue-Green/Canary, PITR-Backups und Roll-forward/rollback-fähige Releases etablieren.
- **Betroffene Komponenten:** Container, Plattform, DB, DNS/TLS, Deployment.
- **Abhängigkeiten:** MR-DB-001, MR-OBS-001.
- **Aufwand/Risiko:** XL / hoch.
- **Akzeptanzkriterien:** Wiederaufbau ist reproduzierbar; verschlüsselter Restore erfüllt dokumentierte RPO/RTO; fehlerhaftes Release kann ohne Datenverlust zurückgenommen werden.
- **Tests:** Disaster-Recovery-Drill, Zertifikatsrotation, Readiness, Rollback und Zero-downtime-Migration.
- **Definition of Done:** IaC, Runbooks, Evidence und Verantwortliche sind freigegeben.

### MR-DEVOPS-003 – Repository-Hygiene herstellen

- **Priorität/Kategorie:** P3 / Maintainability
- **Beschreibung:** 887 `node_modules`-Dateien und generierte Testartefakte sind versioniert.
- **Technische Lösung:** Abhängigkeiten aus Git entfernen, Lockfile als Quelle nutzen, `.gitignore` erweitern und Artefakte nur in CI speichern.
- **Betroffene Komponenten:** Git-Index, `.gitignore`, Frontend-Testsetup.
- **Abhängigkeiten:** Keine.
- **Aufwand/Risiko:** XS / niedrig.
- **Akzeptanzkriterien:** Frischer Checkout reproduziert Installationen; keine Abhängigkeits-/Reportdateien sind getrackt.
- **Tests:** Clean checkout, `npm ci`, E2E-Ausführung.
- **Definition of Done:** Repository ist bereinigt und CI erzeugt Reports separat.

## Epic 12 – UX und Accessibility

### MR-UX-001 – WCAG 2.2 AA und resiliente Kernjourneys

- **Priorität/Kategorie:** P1 / Accessibility und UX
- **Beschreibung:** Modals, Tabs und klickbare Karten haben unvollständige Semantik, Fokusführung und Tastaturbedienung; Live-Feedback ist nicht zuverlässig angekündigt.
- **Technische Lösung:** Native Controls/ARIA-Patterns, Fokusfalle/-rückgabe, Escape, sichtbaren Fokus, Live Regions, Kontrast-/Zoom-/Reflow-Regeln und verständliche Fehlerzustände implementieren.
- **Betroffene Komponenten:** Alle drei Frontends, CSS, Komponenten, Content.
- **Abhängigkeiten:** Design-/Content-Review.
- **Aufwand/Risiko:** L / hoch für Inklusion und Compliance.
- **Akzeptanzkriterien:** Kritische Journeys erfüllen WCAG 2.2 AA; vollständig per Tastatur, Screenreader und 200/400-%-Zoom nutzbar.
- **Tests:** axe plus manuell NVDA/VoiceOver, Tastatur, Kontrast, Reflow und Fehlermeldungen.
- **Definition of Done:** Accessibility-Audit ohne kritische/hohe offene Befunde und dokumentierte Erklärung.

## Epic 13 – Admin und Moderation

### MR-MOD-001 – Marketplace-Compliance und belastbare Moderation

- **Priorität/Kategorie:** P1 / Marketplace Governance
- **Beschreibung:** Trader-/Privatstatus, KYC, Rankingtransparenz, verbotene Kategorien, Beweise, Einspruch und Admin-Audit sind nicht ausreichend abgebildet.
- **Technische Lösung:** Onboarding-/KYC-Status, Pflichtinformationen, versionierte Richtlinien, Reason Codes, Evidence Store, SLAs, Appeals und Vier-Augen-Aktionen implementieren; Rankingkriterien offenlegen.
- **Betroffene Komponenten:** Provider-Onboarding, Suche, Reports, Admin, Rechtstexte.
- **Abhängigkeiten:** Legal/Compliance, MR-OBS-001, MR-PRIV-001.
- **Aufwand/Risiko:** XL / hoch, regulatorisch und Reputationsrisiko.
- **Akzeptanzkriterien:** Kein nicht freigegebener Provider kann listen/bezahlt werden; Entscheidungen sind begründet, zeitlich nachvollziehbar und anfechtbar.
- **Tests:** KYC-Ablauf/Entzug, Moderationsrollen, Beweiszugriff, Appeal, Rankingdarstellung.
- **Definition of Done:** Legal, Trust & Safety und Operations haben Prozess, UI und Audit abgenommen.

## Epic 14 – Dokumentation und Betriebsprozesse

### MR-OPS-001 – Runbooks, Incident Response, Support und Release-Governance

- **Priorität/Kategorie:** P1 / Operational Readiness
- **Beschreibung:** Architektur-, Deployment-, Payment-, Datenschutz-, Incident-, Backup- und Support-Runbooks sowie klare Ownership fehlen.
- **Technische Lösung:** Versionierte Runbooks, System-/Datenflussdiagramme, On-call, Eskalationsmatrix, Statuskommunikation, Security-/Privacy-Incident-Prozess, PSP-/Mail-Ausfallverfahren und Release-RACI erstellen und drillen.
- **Betroffene Komponenten:** Engineering, Security, Finance, Support, Legal, Operations.
- **Abhängigkeiten:** Zielarchitektur und organisatorische Benennungen.
- **Aufwand/Risiko:** L / hoch.
- **Akzeptanzkriterien:** Jede kritische Alarmklasse hat Owner, Reaktionszeit und Handlungsanweisung; mindestens ein Tabletop und ein Restore-Drill sind protokolliert.
- **Tests:** Tabletop für Account-Takeover, Paymentabweichung, Datenabfluss, PSP-Ausfall und DB-Verlust.
- **Definition of Done:** Runbooks sind auffindbar, aktuell, geübt und von allen Funktionen signiert.

## P0-Launch-Gates in Kurzform

Der öffentliche Launch bleibt gesperrt, bis mindestens MR-SEC-001/002/004, MR-AUTH-001/002, MR-PAY-001/002/003/004, MR-BOOK-001/002, MR-DB-001, MR-TEST-001 und MR-DEVOPS-001 abgeschlossen und nachweisbar verifiziert sind. Ein P0 darf nur durch Behebung geschlossen werden; Risikoakzeptanz ist für die bestätigten kritischen Exploitpfade kein verantwortbarer Ersatz.
