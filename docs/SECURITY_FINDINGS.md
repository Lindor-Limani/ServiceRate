# Security Findings

Stand: 2026-07-14  
Bewertungsbasis: OWASP ASVS 5.0.0, OWASP Top 10:2025, OWASP API Security Top 10:2023, CWE, manuelle Codeanalyse sowie lokale SCA- und Testläufe.  
Vollständige Einzelfund-Datensätze mit Szenario, Auswirkung, Lösung, Tests, Aufwand und Akzeptanzkriterien stehen in [MARKET_READINESS_AUDIT.md](MARKET_READINESS_AUDIT.md#c-findings). Die Kurz-IDs `SR-F001` bis `SR-F027` entsprechen dabei exakt `FINDING-001` bis `FINDING-027` im Audit.

## Ergebnis

**Security-Freigabe: abgelehnt (NO-GO).** Vier bestätigte kritische Exploitpfade ermöglichen Admin-Übernahme, Fremdänderung von Angeboten, Freischaltung unbezahlter Leistungen und Manipulation der PayPal-Zahlungsempfänger. Insgesamt wurden 27 Findings erfasst: 4 Critical, 15 High, 7 Medium und 1 Low. Davon sind 19 primär sicherheits-, datenschutz- oder integritätsrelevant.

| Schweregrad | Anzahl | Launch-Blocker |
|---|---:|---:|
| Critical | 4 | 4 |
| High | 15 | 15 |
| Medium | 7 | 0 |
| Low | 1 | 0 |
| Informational | 0 | 0 |

Die Einstufung beschreibt die konkrete Anwendung, nicht nur die theoretische Schwäche. Alle Critical- und High-Funde gelten für einen öffentlichen Marketplace als Launch-Blocker, solange kein belastbarer Gegenbeweis oder eine implementierte und getestete Behebung vorliegt.

## Kritische Exploitketten

### 1. Vollständige Account-/Admin-Übernahme

1. Der Angreifer liest den fest einkompilierten HMAC-Schlüssel aus Quellcode oder Artefakt.
2. Er erstellt ein gültig signiertes JWT mit der UUID eines beliebigen aktiven Kontos und `accountType=ADMIN`.
3. Der Filter prüft zwar, ob der Nutzer aktiv ist, übernimmt die Berechtigung aber aus dem manipulierten Claim.
4. Admin-Endpunkte und besonders sensible personenbezogene/finanzielle Daten werden erreichbar.

**Kontrollen vor Launch:** sofortige Schlüsselrotation, Invalidierung aller Tokens, externer Secret Store, serverseitige Rollenauflösung, Issuer-/Audience-/Algorithmusprüfung, kurze Laufzeit, Session-Widerruf und Regressionstest.

### 2. Fremde Angebote manipulieren oder löschen

1. Ein beliebiger authentifizierter Nutzer ermittelt oder errät eine Service-UUID.
2. `PUT` oder `DELETE /api/services/{id}` passiert die allgemeine `authenticated()`-Regel.
3. Controller und Service prüfen weder Provider-Rolle noch Eigentümerschaft.
4. Preis, Kategorie und Bilder eines fremden Angebots werden geändert oder das Angebot wird entfernt.

**Kontrollen vor Launch:** zentrale objektbezogene Policy, Provider- und Owner-Prüfung, explizit auditierte Admin-Ausnahme, vollständige Rollen-/Ownership-Negativtests.

### 3. Unbezahlte Leistung als bezahlt markieren

1. Der Customer ruft für seine Buchung `POST /api/bookings/{id}/mark-paid` auf.
2. Der Server verlangt keinen verifizierten PSP-Capture oder Webhook.
3. Buchung, Settlement und `paidAt` werden direkt auf bezahlt gesetzt.
4. Die Delivery-Freigabe akzeptiert diesen Zustand und gibt die Leistung frei.

**Kontrollen vor Launch:** Endpunkt entfernen, ausschließlich verifizierte und idempotente PSP-Ereignisse akzeptieren, Ledger und erlaubte Zustandsübergänge atomar prüfen.

### 4. Zahlungsempfänger manipulieren

1. Ein Provider setzt im Profil eine fremde `paypalMerchantId` und positive Bestätigungsflags oder fälscht den Onboarding-Return.
2. Der Backend-Service vertraut diesen Clientwerten.
3. Der Checkout übernimmt die gespeicherte Merchant-ID als Payee.
4. Zahlungen können an ein nicht verifiziertes Konto geroutet werden.

**Kontrollen vor Launch:** Merchant-Zuordnung ausschließlich über serverseitig verifizierten Partner-OAuth/Referral-Flow, State/Nonce, unveränderliches Audit und Vier-Augen-Ausnahmeprozess.

## Findings-Register

| ID | Sev. | Launch | Kurzbefund | Primärnachweis | OWASP/CWE-Zuordnung |
|---|---|---|---|---|---|
| SR-F001 | Critical | Ja | Fest einkompilierter JWT-Key und Rolle aus Token-Claim | `JwtUtil`, `JwtAuthenticationFilter` | ASVS V6; A07:2025; CWE-798/347 |
| SR-F002 | Critical | Ja | Fehlende Ownership bei Service-Update/-Delete | `SecurityConfig`, `ServiceOfferingController/Service` | API1:2023 BOLA; CWE-639/862 |
| SR-F003 | Critical | Ja | Customer kann Buchung ohne PSP-Nachweis auf bezahlt setzen | `BookingController.markPaid`, `BookingService.markPaid` | ASVS V11; CWE-602/841 |
| SR-F004 | Critical | Ja | Client kontrolliert PayPal-Merchant und Verifikationsflags | `UpdateUserRequest`, `UserService`, `ProviderPayPalOnboardingService` | API3/API6; CWE-602/345 |
| SR-F005 | High | Ja | Buchungslisten/-details per fremder UUID abrufbar | Booking-Controller/-Service/-DTO | API1 BOLA; CWE-639 |
| SR-F006 | High | Ja | Review wird ohne Caller-/Customer-Prüfung erstellt | `ReviewController`, `ReviewService` | API1; CWE-862 |
| SR-F007 | High | Ja | Stored XSS plus Tokens in `localStorage` und fehlende CSP | Frontend-`innerHTML`, `api.js` | A05/A07:2025; CWE-79/922 |
| SR-F008 | High | Ja | Buchungs-/Payment-Zustände ohne strikte Übergänge | `BookingService.updateStatus`, Checkout-Flows | ASVS V11; CWE-841 |
| SR-F009 | High | Ja | Webhooks/Payments ohne vollständige Idempotenz und Reihenfolge | Stripe-/PayPal-Service | API4/API6; CWE-362 |
| SR-F010 | High | Ja | Geld als `Double`, Währung/Preis-Snapshot unvollständig | Service-/Booking-Entities und Checkout | CWE-682/704 |
| SR-F011 | High | Ja | Keine atomare Verfügbarkeits-/Überbuchungskontrolle | `BookingService.createBooking`, Schema | API4; CWE-362 |
| SR-F012 | High | Ja | Unbegrenzte/ungenügend validierte Base64-Uploads | Service, User, Chat | A10:2025; CWE-400/434 |
| SR-F013 | High | Ja | Keine Rate Limits/Lockout/MFA, Enumeration und schwache Passwortregeln | Auth-Service/Security | A07:2025; CWE-307/204 |
| SR-F014 | High | Ja | Lang lebende, nicht widerrufbare Tokens; SSE-Token in URL | JWT, Customer-/Provider-Chat | ASVS V6; CWE-598/613 |
| SR-F015 | High | Ja | 34 OSV-Meldungen, einschließlich kritischer Laufzeitkomponenten | Gradle Runtime Classpath | A03:2025 Supply Chain; CWE-1104 |
| SR-F016 | High | Ja | Keine belastbare Retention, Betroffenenrechte und Admin-Zugriffsprüfung | Datenmodell/Admin/Backups | ASVS V8; CWE-359 |
| SR-F017 | High | Ja | Refund/Dispute/Payout/Reconciliation und Ledger fehlen | Payment/Settlement/Admin | ASVS V11; CWE-841 |
| SR-F018 | High | Ja | Nicht deterministische Migrationen und unsichere Löschgraphen | Properties, `SchemaMigrationConfig`, `UserService` | A02/A08:2025; CWE-703 |
| SR-F019 | High | Ja | Unsichere Produktionsdefaults, keine CI/CD-/Secret-Gates | Properties, Compose, Repository | A02/A03/A08:2025 |
| SR-F020 | Medium | Nein | N+1 und unbeschränkte Listen ermöglichen Ressourcenerschöpfung | Service-/Booking-/Admin-Queries | API4; CWE-400 |
| SR-F021 | Medium | Nein | RuntimeException-Text und uneinheitliche Fehler nach außen | `GlobalExceptionHandler` | A10:2025; CWE-209 |
| SR-F022 | Medium | Nein | Externe Calls ohne belastbare Timeouts/Outbox/Recovery | HTTP, SMTP, Payment | API4; CWE-400/755 |
| SR-F023 | Medium | Nein | Logging, Audit, Korrelation, Alarme und Tracing unzureichend | Plattform/Backend | A09:2025; CWE-778 |
| SR-F024 | Medium | Nein | Marketplace-/Moderationskontrollen unvollständig | Provider, Suche, Reports, Admin | Business/Compliance |
| SR-F025 | Medium | Nein | Barrierefreiheitsmängel in Modals, Tabs und Interaktion | HTML/JS/CSS | WCAG 2.2 AA |
| SR-F026 | Medium | Nein | Kritische Security-/Recovery-/Concurrency-Tests fehlen | Backend, Playwright, k6 | ASVS V14 |
| SR-F027 | Low | Nein | Abhängigkeiten und Testartefakte in Git versioniert | Git-Index/`.gitignore` | Supply-chain hygiene |

### Statusaktualisierung SR-F001 / FINDING-001 – 2026-07-14

**Status: REQUIRES MANUAL VERIFICATION.** Der fest einkompilierte HMAC-Schlüssel wurde entfernt. JWT-Secret und Key-ID sind nun Pflichtkonfiguration ohne Default; Schlüssel unter 32 Bytes sowie ungültige Konfiguration verhindern den Start. Tokens binden und validieren Issuer, Audience, Key-ID und HS256. Der Request-Filter ignoriert den Rollen-Claim für die Autorisierung und lädt Status sowie Rolle bei jeder Anfrage aus der Datenbank. Automatisierte Regressionstests bestätigen Schlüsselrotation, Metadaten-/Algorithmusprüfung, Rollen-Claim-Manipulation, serverseitige Rollenänderung und Account-Sperrung.

Das Finding wird erst nach manueller Betriebsverifikation geschlossen: Ein neuer produktiver Schlüssel muss im Secret Store erzeugt und ausgerollt, der bekannte alte Schlüssel entfernt, alle alten Tokens müssen nachweislich ungültig und der Rotationsablauf muss in der Zielumgebung geprüft sein. Bis dahin bleibt der Launch-Blocker aktiv.

### Statusaktualisierung SR-F003 / FINDING-003 – 2026-07-14

**Status: COMPLETED.** `POST /api/bookings/{id}/mark-paid` wurde aus `BookingController` entfernt und die zugehörige `BookingService.markPaid`-Methode gelöscht. `SecurityConfig` sperrt den früheren Pfad zusätzlich mit `denyAll`, sodass auch eine versehentliche spätere Controller-Wiedereinführung nicht ohne bewusste Security-Änderung erreichbar wäre. Ein HTTP-Regressionstest weist für anonym, Customer, Provider und Admin sowie bei Wiederholung nach, dass jeder Request abgewiesen wird und Zahlungs-/Settlementstatus, `paidAt` und Zahlungsnotiz unverändert bleiben.

Die Ursache dieses Findings ist damit im tatsächlichen Anwendungspfad behoben und ohne bekannte Umgehung geschlossen. Andere Payment-Pfade und das fehlende übergreifende Ledger bleiben separat unter SR-F009/SR-F017 offen.

### Statusaktualisierung SR-F004 / FINDING-004 – 2026-07-14

**Status: PARTIALLY COMPLETED.** Der allgemeine Profilpfad `PUT /api/users/{id}` lehnt `paypalMerchantId` und `paypalEmail` nun vor jeder Entitätsänderung explizit ab. Das Provider-Frontend sendet diese Felder beim normalen Profilspeichern nicht mehr. Service- und HTTP-Regressionstests decken einzelne, kombinierte, leere und wiederholte Manipulationsversuche, Fremdzugriff, persistente Unverändertheit sowie normale Profiländerungen ab.

Das Finding bleibt Critical und Launch-Blocker: Der separate Endpunkt `/api/providers/me/paypal/onboarding-return` vertraut weiterhin clientgelieferten Merchant-/Permission-/E-Mail-Bestätigungswerten. Bis auch dieser Pfad ausschließlich serververifizierte PayPal-Daten akzeptiert und Checkout/Audit geprüft sind, besteht weiterhin eine bekannte Umgehungsmöglichkeit.

### Statusaktualisierung SR-F006 / FINDING-006 – 2026-07-14

**Status: PARTIALLY COMPLETED.** `POST /api/reviews` ist nun auf die Customer-Rolle begrenzt. Der Controller leitet ausschließlich den authentifizierten Principal an den Service weiter; der Service vergleicht ihn vor Statusprüfung, Persistenz und Mailversand mit dem tatsächlichen Booking-Customer. Fremde Customers erhalten 403, während Provider, Admin und anonyme Aufrufer bereits durch die Security-Konfiguration abgewiesen werden. Service- und HTTP-Regressionstests bestätigen den Owner-Normalfall, die vollständige Rollen-/Fremdzugriffsmatrix, fehlende und nicht abgeschlossene Buchungen, ungültige Ratings und ausbleibende Schreibeffekte.

Der konkrete Identitätsmissbrauch ist im aktiven Anwendungspfad behoben. Das Finding bleibt bis zur nachgewiesenen Exactly-once-Garantie bei parallelen Review-Requests und zur Verifikation eines eindeutigen Datenbank-Constraints offen; diese Nebenläufigkeits-/Migrationsarbeit war nicht Teil des abgegrenzten Durchlaufs. Die separaten Booking-IDORs aus SR-F005 bleiben vollständig offen.

### Statusaktualisierung SR-F002 / FINDING-002 – 2026-07-14

**Status: PARTIALLY COMPLETED.** `PUT /api/services/{id}` und `DELETE /api/services/{id}` sind nun explizit auf die Provider-Rolle begrenzt. Der Controller leitet ausschließlich die Identität aus dem authentifizierten Principal weiter; der Service lädt den aktiven Provider und prüft vor Update oder Delete die Provider-ID des Angebots. HTTP- und Service-Regressionstests decken Owner, fremden Provider, Customer, anonymen Zugriff, gesperrten Provider und fehlende Ressource ab.

Das Finding wird noch nicht als geschlossen markiert: Die unmittelbare fehlende Objektprüfung ist behoben, aber FINDING-001 erlaubt weiterhin die Fälschung einer Provideridentität über den bekannten JWT-Schlüssel. Erst nach Behebung von FINDING-001 und erneuter kombinierter Security-Verifikation besteht keine bekannte Umgehungsmöglichkeit mehr.

## Betroffene Angriffsflächen

### Authentifizierung und Session

- Login unterscheidet unbekannten Nutzer und falsches Passwort.
- Tokens sind 24 Stunden gültig, besitzen keinen belastbaren Widerruf und werden beim Logout nur clientseitig entfernt.
- Passwortwechsel, Account-Deaktivierung und Schlüsselrotation haben keinen vollständigen Session-Lifecycle.
- JWT/SSE-Tokens werden in Web Storage bzw. Query-Strings transportiert und können über XSS, Logs, Verlauf oder Telemetrie offengelegt werden.
- MFA fehlt insbesondere für Admin und Provider; Login und sensible Recovery-Endpunkte sind nicht rate-limitiert.

### Objekt- und Funktionsautorisierung

- Service-Update und -Delete: authentifiziert, aber keine Rolle/Ownership.
- Buchungslisten nach Provider-/Customer-ID und Buchungsdetail: Identität aus Request statt Principal.
- Review-Erstellung: Buchungskunde wird als Reviewer gesetzt, ohne den Caller abzugleichen.
- Admin-Zugriffe besitzen keinen separaten manipulationsgeschützten Audit Trail.

### Zahlungs- und Finanzdaten

- Positiv: Kartennummer/CVV werden nicht gespeichert und Checkout-Beträge werden grundsätzlich serverseitig berechnet.
- Kritisch: Zahlungsstatus und Payee-Verknüpfung können außerhalb eines verifizierten Provider-Ereignisses beeinflusst werden.
- Hoch: Das Datenmodell besitzt kein unveränderliches Ledger, keine robuste Deduplizierung, keine Buchungswährung und keinen unveränderlichen Preissnapshot.
- Sensible Felder wie IBAN, Merchant-/Payment-IDs und Settlementinformationen sind für Admin-Funktionen breit zugänglich; Zugriff und Maskierung müssen nach Least Privilege erfolgen.
- Secrets dürfen weder Defaultwerte besitzen noch in Properties, Artefakten, Frontend, Logs, URLs, CI-Ausgaben oder Support-Dumps erscheinen.

### Daten und Uploads

- Persistente Texte werden in mehreren Frontends unescaped in HTML eingesetzt.
- Bilder liegen als große Data URLs in der relationalen DB; Byte-/Pixel-/Nutzerquoten, echte Inhaltserkennung, Re-Encode und Malwareprüfung fehlen.
- Löschung kann an Referenzen scheitern oder fachlich erforderliche Finanz-/Auditdaten verlieren.
- Consent-Version, Export, Retention, Legal Hold, Backup-Löschung und Zugriffsaudit sind nicht durchgängig vorhanden.

## Dependency- und Supply-Chain-Befund

Der lokale Laufzeitgraph enthielt 115 Maven-Komponenten. Ein OSV-API-Abgleich am 2026-07-14 lieferte 34 Meldungen. Die Zahl ist **kein CVE-Risikozähler**: mehrere Meldungen können dieselbe Bibliothek oder nur unter bestimmten Konfigurationen relevante Pfade betreffen. Vor Launch ist deshalb pro Meldung die Anwendbarkeit zu dokumentieren und das finale Artefakt erneut zu scannen.

Besonders dringliche Beispiele:

| Komponente/Thema | Gemeldete Schwere | Bekannte Fixschwelle aus Scan | Bewertung |
|---|---|---|---|
| Apache Tomcat Security Constraints | Critical | 11.0.22 | öffentlich exponierter Servlet-Container; aktualisieren und testen |
| Apache Tomcat HTTP/2 Header Validation | Critical | 11.0.22 | relevant, falls HTTP/2 am Container/Proxy terminiert; Architektur prüfen |
| Apache Tomcat Digest Authentication | Critical | 11.0.22 | Digest wird im Code nicht genutzt; dennoch über Plattformkonfiguration bestätigen |
| Spring Boot Default Security Chain/Actuator | Critical | 4.0.6 | Custom Security Chain vorhanden; Endpunkt-Exposition testen und aktualisieren |
| Spring Security | High | gepatchte 7.0.x-Version laut Advisory | konkrete Request-Matcher-/Authentifizierungswege gegen Advisory prüfen |
| Jackson | High | je Advisory 2.21.4 bzw. 3.1.4 | verwendete Databind-/Core-Versionen und erreichbare Parser prüfen |

Zusätzlich erforderlich: Dependency Locking, reproduzierbarer Build, SBOM (CycloneDX/SPDX), Secret Scan, SAST, SCA, signiertes minimales non-root Container-Image, Image-Scan und dokumentierte, befristete Ausnahmegenehmigungen.

## Positive Security Controls

- Passwörter werden mit BCrypt gespeichert.
- E-Mail-Verifikation und Passwort-Reset besitzen Ablauf und Einmalverwendung; Forgot-/Resend-Antworten sind generisch.
- Der JWT-Filter lehnt deaktivierte Datenbanknutzer ab.
- Bei Buchungserstellung wird der Customer aus dem Principal abgeleitet.
- Mehrere Provider-Aktionen prüfen bereits, ob der Provider an der Buchung beteiligt ist.
- Delivery prüft Beteiligung, Zahlungsstatus und Ablauf.
- Stripe-Webhook-Signaturen werden validiert.
- JPQL ist parameterisiert; die öffentliche Suche besitzt eine maximale Seitengröße.
- SVG wird beim Bildabruf nicht erlaubt; Services begrenzen die Anzahl der Bilder auf zehn.

Diese Kontrollen reduzieren Teilrisiken, kompensieren aber keinen der bestätigten Launch-Blocker.

## Verifikationsplan für die Security-Freigabe

1. Für jeden Critical/High-Fund einen reproduzierbaren Negativtest erstellen, der vor dem Fix fehlschlägt.
2. Fix auf einem geschützten Branch implementieren und Peer-/Security-Review durchführen.
3. Backendtests gegen PostgreSQL, integrierte Browsertests und PSP-Sandboxtests ausführen.
4. SAST, SCA, Secret-, SBOM-, Container- und IaC-Scan auf dem finalen Releaseartefakt ausführen.
5. Autorisierungsmatrix sämtlicher Endpunkte sowie manuelle Business-Logic- und Upload-Tests durchführen.
6. Unabhängigen Penetrationstest mit Schwerpunkt BOLA, Payment, Account Takeover, Stored XSS und Race Conditions beauftragen.
7. Nur Findings schließen, wenn Akzeptanzkriterium, Testnachweis, verantwortliche Person und Releaseversion dokumentiert sind.
8. Security, Finance, Privacy und Operations müssen die jeweilige Domäne im Release-Gate signieren.

## Referenzstandards

- [OWASP ASVS 5.0.0](https://github.com/OWASP/ASVS/tree/v5.0.0_release)
- [OWASP Top 10:2025](https://owasp.org/Top10/)
- [OWASP API Security Top 10:2023](https://owasp.org/API-Security/editions/2023/en/0x11-t10/)
- [WCAG 2.2](https://www.w3.org/TR/WCAG22/)

MASVS wurde nicht als Freigabestandard angewandt, da das Repository keine native Mobile-App enthält. Für spätere native Clients wäre eine separate MASVS-Prüfung erforderlich.
