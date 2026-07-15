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

### Statusaktualisierung SR-F004 / FINDING-004 – 2026-07-14 / 2026-07-15

**Status: PARTIALLY COMPLETED (aktualisiert 2026-07-15).** Der allgemeine Profilpfad `PUT /api/users/{id}` lehnt `paypalMerchantId` und `paypalEmail` vor jeder Entitätsänderung explizit ab. Zusätzlich wurden der clientvertrauende `POST /api/providers/me/paypal/onboarding-return` und der nicht vom aktiven Onboarding erzeugte Identity-Fallback `POST /api/providers/me/paypal/identity-return` einschließlich Controller-, Service-, DTO- und ungebundenem OAuth-Codeaustausch entfernt; `SecurityConfig` sperrt beide früheren Routen unabhängig davon explizit. Das Provider-Frontend bietet keine manuelle Bestätigung mehr an und sendet keine Merchant-/Permission-/E-Mail-Flags an das Backend.

Der aktive Partner-Return verwendet nun einen serverseitig erzeugten 32-Byte-State. Nur sein SHA-256-Hash und eine 15-minütige Ablaufzeit werden am Provider gespeichert; ein neuer Start ersetzt den vorherigen State. Die PayPal-Return-URL enthält keine Provider-ID mehr. `POST /api/providers/me/paypal/onboarding-complete` akzeptiert ausschließlich den State des authentifizierten Providers, konsumiert ihn in einer kurzen pessimistisch gesperrten Transaktion genau einmal und liest erst danach den Sellerstatus serverseitig bei PayPal. Fehlende, zu lange, ungültige, abgelaufene, fremde und wiederholte States lösen keinen Sellerstatusabruf und keine Empfängeränderung aus. Service-, HTTP-, Persistenz-, Zehnfach-Parallelitäts- und Desktop-/Mobile-Playwright-Tests belegen diese Guards.

Der Checkout-Payee ist nun ebenfalls serverseitig abgesichert. Eine zentrale Regel gibt PayPal nur für Provider mit Status `CONNECTED`, positivem Permission- und E-Mail-Bestätigungsflag sowie nichtleerer Merchant-ID frei; Angebotsantwort, Booking-Vorprüfung und PayPal-Adapter verwenden dieselbe Regel. Der Adapter erzwingt sie auch bei direktem Aufruf vor jedem PayPal-Request, schreibt ausschließlich die getrimmte Merchant-ID als `payee.merchant_id` und besitzt keinen E-Mail-Fallback mehr. Request-Inhaltstests und eine Negativmatrix belegen, dass `ACTION_REQUIRED`, fehlende/negative Flags, fehlende Merchant-ID, ein bloßer E-Mail-Empfänger und fehlende Zuordnungen keinen PayPal-Aufruf auslösen.

Der bestätigte State-/Replay- und Checkout-Payee-Teilpfad ist damit ohne bekannte Umgehung geschlossen. Das Gesamtfinding bleibt Critical und Launch-Blocker, weil die vollständige fachliche Verifikation aller Seller-/Berechtigungswerte, ein manipulationsgeschütztes Änderungs-Audit sowie PayPal-Sandbox-/Produktionsabnahme weiterhin fehlen. Insbesondere wird ein zwischen zwei Statusaktualisierungen bei PayPal entzogener Zugriff noch nicht synchron bei jedem Checkout erkannt. Die neuen Spalten laufen außerdem noch über das unter SR-F018 separat offene Startup-DDL statt über eine versionierte PostgreSQL-Migration.

### Statusaktualisierung SR-F005 / FINDING-005 – 2026-07-14

**Status: PARTIALLY COMPLETED.** Die frei adressierbaren Endpunkte `GET /api/bookings/customer/{customerId}` und `GET /api/bookings/provider/{providerId}` wurden aus dem Controller entfernt. Nur die principalbasierten `/customer/me`- und `/provider/me`-Routen bleiben verfügbar; die früheren ID-Pfade sind zusätzlich in `SecurityConfig` explizit gesperrt. HTTP-Regressionstests weisen die Sperre für Customer, Provider, Admin und anonyme Aufrufer mit eigener, fremder und fehlender UUID nach. Positive Mehrnutzer-Tests bestätigen, dass jede `/me`-Liste ausschließlich Buchungen der authentifizierten Identität enthält.

Der konkrete UUID-IDOR und damit der unmittelbare Abfluss fremder Buchungslisten ist im aktiven Anwendungspfad behoben. Das Finding bleibt bis zur rollenabhängigen Minimierung der umfangreichen `BookingResponse`-Felder und zur Umsetzung eines getrennten, auditierbaren Admin-Zugriffs offen; Pagination und ein vollständiges Endpunktinventar verbleiben ebenfalls in MR-AUTH-002 beziehungsweise MR-PERF-001.

### Statusaktualisierung SR-F006 / FINDING-006 – 2026-07-14

**Status: COMPLETED.** `POST /api/reviews` ist auf die Customer-Rolle begrenzt. Der Controller leitet ausschließlich den authentifizierten Principal an den Service weiter; der Service vergleicht ihn vor Statusprüfung, Persistenz und Mailversand mit dem tatsächlichen Booking-Customer. Fremde Customers erhalten 403, während Provider, Admin und anonyme Aufrufer durch die Security-Konfiguration abgewiesen werden. Für die Exactly-once-Garantie lädt der Service die Buchung nun mit pessimistischem Schreib-Lock, prüft eine vorhandene Bewertung innerhalb derselben Transaktion und persistiert per `saveAndFlush` vor dem Mailversand. Ein benannter eindeutiger Index auf `reviews.booking_id` erzwingt die Invariante zusätzlich direkt in der Datenbank; sequentielle Replays liefern HTTP 409.

Service-, HTTP-, Parallelitäts- und direkter Constraint-Test bestätigen Owner-Normalfall, Rollen-/Fremdzugriffsmatrix, fehlende und nicht abgeschlossene Buchungen, ungültige Ratings, Replay sowie zehn gleichzeitige Requests mit genau einer Review und einer Benachrichtigung. Ursache und bekannte Race-Umgehung sind damit im aktiven Anwendungspfad behoben. SR-F006 ist geschlossen. Das übergreifende MR-AUTH-002 und SR-F005 bleiben wegen DTO-Minimierung, getrenntem Admin-Audit, Endpunktinventar und produktionsnaher PostgreSQL-Verifikation teilweise offen; die allgemeine Ablösung des Startup-DDL bleibt separat SR-F018.

### Statusaktualisierung SR-F007 / FINDING-007 – 2026-07-14

**Status: PARTIALLY COMPLETED.** `ServiceOffering.category` wird bei Create und Update nun zentral gegen eine feste serverseitige Allowlist validiert und normalisiert. Null-, Leer-, unbekannte und HTML-/Scriptwerte werden vor externem PLZ-Aufruf beziehungsweise vor Entity-Mutation abgewiesen. Alle bekannten Kategorieausgaben in Customer-App, Service-Detail, Provider-Profil und Provider-Dashboard escapen zusätzlich den Fallbackwert, sodass auch bereits persistierte oder anderweitig eingespielte Altwerte nur als Text erscheinen. Service-, HTTP- und Playwright-Regressionen bestätigen Ablehnung, persistente Unverändertheit sowie sichere Listen- und Detaildarstellung auf Desktop und Mobile.

Der bestätigte Kategorie-XSS-Pfad ist damit ohne bekannte Umgehung geschlossen. Das Finding bleibt High und Launch-Blocker, weil noch keine vollständige Inventarisierung aller DOM-/Attribut-/URL-Sinks vorliegt, JWTs weiterhin in `localStorage` und beim SSE in der URL verwendet werden und eine durchgesetzte CSP beziehungsweise sichere Cookie-/BFF-Sessionarchitektur fehlt.

### Statusaktualisierung SR-F002 / FINDING-002 – 2026-07-14

**Status: PARTIALLY COMPLETED.** `PUT /api/services/{id}` und `DELETE /api/services/{id}` sind nun explizit auf die Provider-Rolle begrenzt. Der Controller leitet ausschließlich die Identität aus dem authentifizierten Principal weiter; der Service lädt den aktiven Provider und prüft vor Update oder Delete die Provider-ID des Angebots. HTTP- und Service-Regressionstests decken Owner, fremden Provider, Customer, anonymen Zugriff, gesperrten Provider und fehlende Ressource ab.

Das Finding wird noch nicht als geschlossen markiert: Die unmittelbare fehlende Objektprüfung ist behoben, aber FINDING-001 erlaubt weiterhin die Fälschung einer Provideridentität über den bekannten JWT-Schlüssel. Erst nach Behebung von FINDING-001 und erneuter kombinierter Security-Verifikation besteht keine bekannte Umgehungsmöglichkeit mehr.

### Statusaktualisierung SR-F008 / FINDING-008 – 2026-07-15

**Status: PARTIALLY COMPLETED.** Der aktive `POST /api/bookings/{id}/checkout`-Pfad prüft nach Customer-Rolle und Booking-Ownership, aber vor Request-Auswertung, Betragsberechnung und jeder Payment-Mutation nun den fachlichen Ausgangszustand. Ausschließlich exakt `ACCEPTED` darf fortfahren; `PENDING`, `REJECTED`, `COMPLETED`, `CANCELLED`, fehlende, leere und unbekannte Werte liefern über die zentrale `ConflictException` HTTP 409. Checkout und `PUT /api/bookings/{id}/status` laden die Buchung über dieselbe pessimistisch sperrende Zustandsabfrage. Der Provider-Pfad erzwingt zusätzlich die Transitionstabelle `PENDING → ACCEPTED|REJECTED` und `ACCEPTED → COMPLETED`; alle übrigen gültigen Statuspaare, einschließlich Replay, liefern ohne Mutation 409, ungültige Zielwerte bleiben 400. Service- und HTTP-Regressionen decken die vollständige 5×5-Matrix, Rollen, Ownership und Eingaben ab. Zehn gleichzeitig freigegebene Annahme-/Ablehnungsrequests ergeben genau eine Statusänderung und eine Statusmail. Ein gesteuerter Checkout-vs.-Completion-Race weist nach, dass der zweite Pfad bis zum Checkout-Commit wartet und anschließend sowohl `COMPLETED` als auch die Checkout-/PayPal-Daten erhalten bleiben.

Stripe-Checkout-Replays werden nun unter demselben Buchungs-Lock erkannt. Eine committed Session aus `CHECKOUT_CREATED` oder `FAILED` wird ohne erneuten Adapteraufruf zurückgegeben; andere aktive Zahlungsarten und unvollständige/stale Stripe-Daten liefern 409. Zehn parallele Requests warten am Lock und erhalten dieselbe Session/URL bei genau einem Adapteraufruf. Der Adapter sendet zusätzlich den stabilen Key `servicerate-checkout-{bookingId}` an Stripe, sodass ein Retry nach unklarem Providerausgang keine zweite Session erzeugt. Der konkrete Stripe-Checkout-Replay-/Parallelitätsfehler ist damit geschlossen.

PayPal-Order-Replays werden analog unter dem Buchungs-Lock erkannt. Eine vollständig persistierte Order aus `CHECKOUT_CREATED` wird ohne erneuten Adapteraufruf zurückgegeben; aktive andere Zahlungen sowie unvollständige oder stale PayPal-Daten liefern 409. Zehn parallele Requests erhalten dieselbe Order/URL bei genau einem Adapteraufruf. Der Adapter verlangt vor jedem Provideraufruf eine persistierte Booking-ID und sendet `PayPal-Request-Id: servicerate-order-{bookingId}`. Der konkrete PayPal-Order-Replay-/Parallelitätsfehler ist damit geschlossen.

Die synchronen API-Teilpfade „bereits offene oder abgelehnte Buchung startet Checkout“, „PayPal-/Stripe-Doppelklick erzeugt mehrere Provider-Checkouts“, „Provider überspringt oder wiederholt einen fachlichen Statuswechsel“ und der Lost-Update-Race zwischen Checkout und Provider-Statuswechsel sind damit geschlossen. Das Gesamtfinding bleibt High und Launch-Blocker: Offline-Zahlungsverbuchung, Work und Delivery besitzen keine vollständige Transition Policy beziehungsweise gemeinsame Deduplizierung. Abgelaufene PayPal-Orders und Stripe-Sessions haben keinen Erneuerungsworkflow. Der gemeinsame Lock wird während des externen Checkout-Aufrufs gehalten; Akteur-/Grund-/Zeithistorie, Outbox-/Recovery-Architektur, DB-Constraints, PostgreSQL-Verifikation und eine übergreifende Parallelitäts-/Recovery-Suite fehlen.

### Statusaktualisierung SR-F009 / FINDING-009 – 2026-07-15

**Status: PARTIALLY COMPLETED.** `POST /api/bookings/{id}/paypal/capture` lädt die Buchung nun über die gemeinsame pessimistische Zustandsabfrage. Nach Customer-Rolle und Booking-Ownership muss der Provider exakt `PAYPAL`, die Request-Order exakt die gespeicherte Order und der Ausgangszustand exakt `CHECKOUT_CREATED` sein. Ein bereits committed `PAID` wird erst nach diesen Zuordnungsprüfungen ohne weiteren PSP-, Persistenz- oder Mail-Effekt zurückgegeben. Nur eine PayPal-Antwort mit Status `COMPLETED` und nichtleerer Capture-ID darf `PAID`, Capture-ID, Settlementstatus und `paidAt` setzen; die Persistenz wird vor der Mail geflusht.

Service- und HTTP-Regressionen decken Happy Path, sequentiellen Replay, Rollen, Ownership, fehlende Buchung, fehlende/falsche Order, falschen Provider, ungültige Ausgangszustände sowie leere, nicht abgeschlossene oder Capture-ID-lose Providerantworten ohne Mutation ab. Ein Zehnfach-Paralleltest hält den ersten simulierten PSP-Aufruf offen und weist nach, dass alle konkurrierenden Requests am Lock serialisiert werden; insgesamt entstehen genau ein PayPal-Aufruf, eine Benachrichtigung und ein persistierter bezahlter Zustand. Der Adapter verlangt zusätzlich eine persistierte Booking-ID und eine nichtleere Order-ID vor jedem Token-/Provideraufruf, sendet `PayPal-Request-Id: servicerate-capture-{bookingId}` sowie `Prefer: return=representation` und extrahiert die Provider-Order-ID, `purchase_units[0].reference_id` und `custom_id`. Der Booking-Service verlangt deren exakte Übereinstimmung mit gespeicherter Order und gelockter Buchung, bevor Status oder Capture-ID ausgewertet werden. Ein echter HTTP-Retrytest und eine neun Fälle umfassende Missing-/Blank-/Mismatch-Matrix belegen Header, Parsing und fail-closed Verhalten.

Der PayPal-Checkout speichert nun zusätzlich den erwarteten Capture-Betrag als `NUMERIC(19,2)`, die normalisierte ISO-Währung und die verifizierte Payee-Merchant-ID direkt an der Buchung, bevor die Order erzeugt wird. Der Adapter verwendet ausschließlich diese Sollwerte und lehnt fehlende oder vom weiterhin verifizierten Provider abweichende Snapshots vor dem PayPal-Aufruf ab. Die vollständige Capture-Antwort wird um Betrag, Währung und Purchase-Unit-Payee ausgewertet; jeder fehlende, leere, ungültige oder abweichende Wert stoppt vor Statusmutation, Persistenz und Mail. Adapter-, Service-, HTTP- und Zehnfach-Paralleltests belegen die Persistenz des einmaligen Sollwertsatzes, den Request daraus, den vollständigen Abgleich, committed Replay und fail-closed Altcheckouts.

Stripe-Webhooks beanspruchen jetzt nach erfolgreicher Signaturprüfung die nichtleere, längenbegrenzte Provider-Event-ID in einer persistenten Inbox mit eindeutigem Datenbank-Constraint. Inbox-Insert und bestehende fachliche Verarbeitung laufen in einer gemeinsamen Transaktion. Ein committed Replay wird mit HTTP 2xx quittiert und führt weder Booking- noch Mailwirkung erneut aus; schlägt die fachliche Verarbeitung fehl, wird auch die Inbox-Zeile zurückgerollt und dieselbe Event-ID bleibt retryfähig. Echt signierte HTTP-Tests belegen Normalfall, Replay, Signatur-/Event-ID-Negativfälle und Retry nach fehlender Buchung. Ein Zehnfach-Paralleltest belegt genau einen committed Effekt und einen Inbox-Eintrag.

`checkout.session.completed` und `payment_intent.payment_failed` laden die zugeordnete Buchung nun über denselben pessimistischen Lock. Beide verlangen den Paymentprovider `CARD`; Completed bindet die gespeicherte Checkout-Session und eine vorhandene Payment-Intent-ID, Failed zwingend die gespeicherte Payment-Intent-ID. Die monotone Policy erlaubt Failed nur aus `CHECKOUT_CREATED`, Completed aus `CHECKOUT_CREATED` oder `FAILED`, und behandelt `PAID` als terminal. Echt signierte HTTP-Negativtests belegen leere/fremde IDs, Provider- und Statusmismatch ohne Mutation. Failed→Completed, Completed→Failed und ein paralleler Race enden jeweils deterministisch in `PAID`; genau eine Zahlungsbestätigungsmail entsteht.

Der Stripe-Checkout speichert nun zusätzlich den erwarteten Betrag in Minor Units und die normalisierte ISO-Währung; die bereits bookinggebundene Connected-Account-ID wird vor dem Provideraufruf als unveränderlicher Destination-Sollwert gesetzt. Der Adapter erstellt Betrag, Währung und `transfer_data.destination` ausschließlich aus diesem Snapshot und lehnt unvollständige, ungültige oder vom weiterhin verifizierten Provider abweichende Werte vor Stripe ab. `checkout.session.completed` verlangt eine `paid`-Session und einen `succeeded`-PaymentIntent. Vor `PAID` werden Session-`amount_total`/Währung sowie PaymentIntent-`amount`, `amount_received`, Währung und `transfer_data.destination` exakt abgeglichen. Ein nicht expandierter PaymentIntent muss erfolgreich serverseitig geladen werden; der frühere fail-open Pfad bei Retrieval-Fehler ist entfernt. Signierte HTTP-Tests mit 19 Finanz-/Status-/Zielnegativfällen belegen, dass Inbox-Claim, Booking und Mail bei jeder Abweichung wirkungslos bleiben.

Stripe-Checkout-Erzeugung ist nun ebenfalls replay- und parallelitätssicher. Committete Replays verwenden die persistierte Session ohne Provideraufruf; ein echter lokaler Stripe-HTTP-Test weist den stabilen bookinggebundenen Idempotency-Header nach. Ein Zehnfach-Race belegt genau einen Adapteraufruf und eine persistierte Session. Inkonsistente Stripe-Daten oder eine bereits aktive andere Zahlungsart werden vor Betragsermittlung und Provideraufruf mit 409 abgewiesen.

PayPal-Order-Erzeugung ist nun ebenfalls replay- und parallelitätssicher. Committete Replays verwenden die persistierte Order/URL ohne Provideraufruf; ein echter lokaler HTTP-Requesttest weist `PayPal-Request-Id: servicerate-order-{bookingId}` nach. Ein Zehnfach-Race belegt genau einen Adapteraufruf und eine persistierte Order. Fehlende Booking-ID, inkonsistente PayPal-Daten oder eine bereits aktive andere Zahlungsart werden vor Provideraufruf abgewiesen.

Die synchronen Replay-/Parallelitätsfehler des PayPal-Order-/Capture-Pfads einschließlich Capture-Retry-Fenster, Order-/Booking-Zuordnung und Betrag-/Währungs-/Payee-Abgleich sowie der Stripe-Checkout-Erzeugung, Stripe-Webhook-Event-ID, Completed-Finanz-/Destination-Bindung und konkret behandelten Completed-/Failed-Reihenfolge sind damit geschlossen. SR-F009 bleibt High und Launch-Blocker: Die providergebundenen PayPal-/Stripe-Sollwerte ersetzen keinen vollständigen Angebots-/Steuer-/Gebühren-/Rechnungssnapshot; Stripe Application Fee und weitere Provider-Eventtypen sowie Refund-/Dispute-Lebenszyklen besitzen keine vollständige Prüf- und Reihenfolgepolicy. Nichttransaktionaler Mailversand, abgelaufene Checkout-Erneuerung, allgemeine Gleitkomma-Geldwerte und vollständige Multi-Währungsregeln, Outbox/Recovery, Ledger/Reconciliation, versionierte Migration, PostgreSQL-Verifikation und Sandboxnachweis fehlen weiterhin.

### Statusaktualisierung SR-F015 / FINDING-015 – 2026-07-14

**Status: PARTIALLY COMPLETED.** Der Runtime-Stack wurde von Spring Boot 4.0.5 auf 4.0.7 aktualisiert. Damit werden Spring Security 7.0.6 sowie Jackson 3.1.4 aufgelöst; ergänzende BOM-Overrides heben Tomcat auf 11.0.24, Logback auf 1.5.37 und den noch transitiv benötigten Jackson-2-Zweig auf 2.21.5. Ein an `check` gebundener Gradle-Test bricht den Build ab, sobald eine dieser tatsächlich aufgelösten Versionen abweicht. Zusätzlich erzeugt CycloneDX 3.2.4 eine kanonisch sortierte CycloneDX-1.6-JSON-SBOM ausschließlich aus dem produktiven Runtime-Classpath. Zufällige Serialnummer, Zeitstempel, Build-Umgebung und lokale Pfade werden ausgeschlossen; ein zweiter `check`-Test prüft Metadaten, PURLs, Kernversionen und die Abwesenheit von Testkomponenten beziehungsweise Secret-Markern. Zwei vollständige Neuerzeugungen waren SHA-256-bytegleich.

Der erneute OSV-Batchscan über 104 ausgewählte Maven-Komponenten liefert keine anwendbare Critical-/High-Meldung. OSV gibt noch GHSA-5jmj-h7xm-6q6v für Jackson 2.21.5 zurück; die veröffentlichte Advisory-Grenze weist jedoch ausschließlich Versionen `< 2.21.5` als betroffen aus. Die im ursprünglichen Audit konkret genannten verwundbaren Frameworkstände sind damit ersetzt und das lokale SBOM-Akzeptanzkriterium erfüllt. SR-F015 bleibt High und Launch-Blocker, bis kontinuierliches SCA als nicht umgehbares CI-Gate, Scan und unveränderliche Archivierung der SBOM des finalen Releaseartefakts sowie nachvollziehbar freigegebene Advisory-Bewertungen vorliegen.

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
