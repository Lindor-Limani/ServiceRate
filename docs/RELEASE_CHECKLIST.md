# Release Checklist

Stand: 2026-07-14  
Aktueller Entscheid: **NO-GO für öffentlichen Produktivbetrieb**  
Zugehörige Dokumente: [Audit](MARKET_READINESS_AUDIT.md) · [Backlog](MARKET_READINESS_TODO.md) · [Security](SECURITY_FINDINGS.md) · [Testlücken](TEST_GAP_ANALYSIS.md)

Diese Liste ist ein Freigabe-Gate, keine Sammlung optionaler Empfehlungen. Jeder Punkt benötigt einen benannten Owner, Datum und verlinkten Nachweis. Nicht anwendbare Punkte benötigen eine schriftliche Begründung und Freigabe der zuständigen Funktion.

## 0. Harte Stop-Kriterien

Ein einziges zutreffendes Kriterium erzwingt **NO-GO**:

- [ ] Offener bestätigter Critical- oder High-Security-Fund.
- [ ] JWT-/Session-Schlüssel im Code, Artefakt, Log oder ungeschützter Konfiguration.
- [ ] Client kann Rolle, Ownership, Zahlungsstatus, Ledger oder Zahlungsempfänger beeinflussen.
- [ ] Payment ist nicht idempotent, nicht abstimmbar oder ohne unveränderliche Ereignisspur.
- [ ] Überbuchung oder unzulässiger Buchungszustand ist unter Parallelität möglich.
- [ ] Produktionsschema wird über `ddl-auto=update` oder fehlertolerantes ad-hoc DDL verändert.
- [ ] Pflichtsecrets besitzen Defaults oder Produktion kann mit Sandbox-/Entwicklungswerten starten.
- [ ] Releaseartefakt hat die P0-Test-, SCA-, Secret-, Migration-, Restore- oder PSP-Sandbox-Gates nicht bestanden.
- [ ] Datenschutz-/Marketplace-/Payment-Betrieb besitzt keine benannten Legal-, Privacy- und Finance-Freigaben.
- [ ] Kein erprobter Rollback/Forward-, Incident-, Backup-/Restore- oder Payment-Recovery-Prozess.

**Aktuell zutreffend:** alle wesentlichen Stop-Kriterien. Ein öffentlicher Launch ist daher nicht verantwortbar.

## 1. Nachweisbare Schließung der P0-Funde

- [ ] **SR-F001:** alter JWT-Key rotiert, sämtliche Tokens invalidiert, Key extern gespeichert; Rollen serverseitig; Forge-/Rotationstests grün. *(Code und Regressionstests am 2026-07-14 abgeschlossen; Gate bleibt bis zur nachgewiesenen Secret-Store-Rotation und Invalidierung in der Zielumgebung offen.)*
- [ ] **SR-F002:** Service-Update/-Delete nur durch Owner-Provider bzw. explizit auditierte Admin-Policy; Rollenmatrix grün. *(Ownership und Rollenmatrix am 2026-07-14 umgesetzt und getestet; Gate bleibt bis zur Behebung des JWT-Identitätsbypasses SR-F001 offen.)*
- [ ] **SR-F003:** `mark-paid` für Clients entfernt; bezahlter Zustand ausschließlich aus verifiziertem PSP-Ereignis.
- [ ] **SR-F004:** PayPal-Merchant-Zuordnung ausschließlich serverseitig verifiziert; State/Nonce und Änderungs-Audit grün.
- [ ] **SR-F007:** alle Stored-/DOM-XSS-Sinks behoben; keine Tokens in Web Storage/URL; CSP ohne unsichere notwendige Ausnahme.
- [ ] **SR-F008/SR-F011:** Buchungszustandsmaschine und Slotreservierung sind atomar; Race-Suite bestanden.
- [ ] **SR-F009/SR-F010/SR-F017:** Ledger, Idempotenz, Währung, Preis-Snapshot und Reconciliation fachlich/technisch abgenommen.
- [ ] **SR-F015:** finale SCA ohne anwendbare Critical/High-Befunde; SBOM archiviert.
- [ ] **SR-F018:** versionierte Migrationen und sichere Lösch-/Archivierungssemantik im Staging bewiesen.
- [ ] **SR-F019:** produktionssichere Konfiguration und nicht umgehbare CI/CD-Gates aktiv.

## 2. Produkt- und Fachfreigabe

- [ ] Unterstützte Kernjourneys und explizit nicht unterstützte Fälle sind dokumentiert.
- [ ] Registrierung, Verifikation, Login, Recovery und Account-Sperrung funktionieren end-to-end.
- [ ] Provider-Onboarding/KYC, Trader-/Privatstatus und Freigabestatus sind verbindlich definiert.
- [ ] Angebotserstellung, Suche, Detail, Buchung, Annahme, Zahlung, Leistung, Review und Support sind end-to-end abgenommen.
- [ ] Statusübergänge besitzen Actor, Guard, erlaubte Quelle/Ziel, Nebenwirkungen und Audit Event.
- [ ] Storno, Umbuchung, No-show, Provider-Ausfall, Ablauf und Konfliktfälle besitzen transparente Regeln.
- [ ] Preise, Gebühren, Steuern, Währung, Rundung und Rechnung werden vor Buchung verständlich dargestellt.
- [ ] Suche/Ranking, gesponserte Inhalte und wesentliche Rankingparameter sind transparent.
- [ ] Verbotene Kategorien, Meldegründe, Sperre, Beweise, Einspruch und Eskalation sind operativ getestet.
- [ ] Support kann Nutzerfälle lösen, ohne Datenbankwerte unkontrolliert manuell zu verändern.

**Sign-off:** Product Owner ___ / Trust & Safety ___ / Support Lead ___ / Datum ___

## 3. Security und Identity

- [ ] Threat Model und Datenflussdiagramm decken Browser, API, DB, Mail, PSP, Storage und Adminzugriff ab.
- [ ] Alle Endpunkte stehen in einer geprüften AuthN/AuthZ-/Ownership-Matrix; Default ist Deny.
- [ ] Admin und Provider verwenden MFA; privilegierte Aktionen verlangen Re-Auth nach definierter Policy.
- [ ] Session-Cookies sind HttpOnly, Secure und angemessen SameSite; CSRF ist für Cookie-Auth verhindert.
- [ ] Session-Laufzeit, Rotation, Replay-Schutz, Widerruf und Logout sind getestet.
- [ ] Passwort-, Brute-Force-, Enumeration-, Recovery- und Account-Lockout-Controls sind aktiv und alarmiert.
- [ ] CORS ist auf konkrete produktive Origins begrenzt; CSP, HSTS, Frame-, MIME- und Referrer-Policy sind gesetzt.
- [ ] Swagger, H2-Konsole, Debug-Actuator und SQL-Logging sind in Produktion deaktiviert bzw. intern authentisiert.
- [ ] Uploads besitzen Byte-/Pixel-/Nutzerquote, Magic-Byte-Prüfung, Re-Encode, Malwareprüfung, sichere ACL und Lifecycle.
- [ ] Secret-Inventar, Rotation, Trennung der Umgebungen und Break-glass-Zugriff sind dokumentiert.
- [ ] SAST, Secret Scan, SCA, Container- und IaC-Scan des finalen Artefakts sind grün.
- [ ] Unabhängiger Penetrationstest ist abgeschlossen; keine offenen Critical/High-Befunde.

**Messbare Schwellen:** 0 Critical/High offen; 100 % Endpunkte klassifiziert; 100 % privilegierte Rollen mit MFA; 0 Secret-/Token-Treffer in Artefakt und Logs.

**Sign-off:** Security Owner ___ / Engineering Lead ___ / Datum ___

## 4. Payments und Finance

- [ ] Zahlungsstatus entsteht ausschließlich aus verifiziertem Provider-Capture/Webhook.
- [ ] Webhook-Signatur, Event-ID, Booking-ID, Betrag, Währung, Payee und erwarteter Ausgangszustand werden geprüft.
- [ ] Provider-Events und API-Aufrufe sind idempotent; Replay und out-of-order Suite ist grün.
- [ ] Append-only Ledger und fachlicher Buchungsstatus sind getrennt, atomar und revisionsfähig.
- [ ] Geldwerte verwenden Minor Units oder feste Dezimalskala; keine Gleitkommazahl für Geld.
- [ ] Unveränderlicher Preis-/Gebühren-/Steuer-/Währungssnapshot wird bei Buchung gespeichert.
- [ ] PayPal-/Stripe-Live-Credentials, Webhook-URLs, Konten und Berechtigungen wurden im Vier-Augen-Prinzip geprüft.
- [ ] Full/Partial Refund, Chargeback, Dispute, Payout, Storno und Fehler-Recovery sind in der Sandbox bestanden.
- [ ] Tägliche PSP-zu-Ledger-zu-Bank-Reconciliation und Abweichungsalarm sind aktiv.
- [ ] Admin-/Support-Zugriffe auf IBAN, PSP- und Settlementdaten sind minimal, maskiert und auditierbar.
- [ ] Rechnungen/Gutschriften besitzen gesetzlich erforderliche Angaben, Nummernkreise und unveränderliche Archivierung.
- [ ] Finance hat Gebühren-, Steuer-, Rundungs-, Auszahlungs- und Reserve-/Haftungsmodell freigegeben.

**Messbare Schwellen:** 0 Reconciliation-Differenz; 100 % Events dedupliziert; 100 parallele Replays = genau ein Effekt; keine manuelle Statusänderung ohne Audit und Vier-Augen-Freigabe.

**Sign-off:** Finance Owner ___ / Payment Engineer ___ / Legal/Tax ___ / Datum ___

## 5. Datenschutz und Compliance

- [ ] Dateninventar, Verarbeitungszweck, Rechtsgrundlage, Empfänger, Speicherort und Aufbewahrung sind vollständig.
- [ ] Datenschutzerklärung, AGB/Marketplace-Bedingungen, Widerruf/Storno und Anbieterpflichtangaben sind juristisch freigegeben.
- [ ] Einwilligungen und Textversionen sind beweisbar gespeichert; Widerruf ist ebenso einfach wie Zustimmung.
- [ ] Datenminimierung wurde pro DTO, Adminansicht, Log, Export und Drittanbieter geprüft.
- [ ] Auskunft/Export, Berichtigung, Löschung/Anonymisierung, Einschränkung und Widerspruch erfüllen definierte SLAs.
- [ ] Finanz-/Audit-Retention ist von löschbaren Profildaten getrennt; Legal Hold ist kontrolliert.
- [ ] Backup-, Cache-, Suchindex-, Objekt-Storage- und Log-Löschung sind berücksichtigt.
- [ ] Auftragsverarbeitung, internationale Transfers, Subprozessoren und PSP-/Mail-Verträge sind freigegeben.
- [ ] Datenschutzvorfall-Prozess, Meldewege, Forensik und Betroffenenkommunikation wurden als Tabletop geübt.
- [ ] Admin-/Supportzugriffe und Exporte sind manipulationsgeschützt auditierbar.

**Sign-off:** Privacy/DPO ___ / Legal ___ / Data Owner ___ / Datum ___

## 6. Datenbank und Migration

- [ ] Flyway/Liquibase oder gleichwertige immutable Migrationen sind alleinige Schemaquelle.
- [ ] `ddl-auto` validiert nur; Startup-DDL schluckt keine Fehler.
- [ ] Leere DB und jede unterstützte Ausgangsversion migrieren deterministisch auf den Release-Stand.
- [ ] Migrationen besitzen Prüfsummen, Owner, Vorbedingungen, Laufzeit-/Lockanalyse und Roll-forward-Plan.
- [ ] FK-, Unique-, Check-, Not-null-, Geld- und Status-Constraints sind geprüft.
- [ ] Optimistic Locking/atomare Updates verhindern Lost Updates und Doppelreservierungen.
- [ ] Kritische Queries wurden mit produktionsnahen Daten und `EXPLAIN ANALYZE` geprüft.
- [ ] Backup unmittelbar vor Migration ist verifiziert; Restore und Roll-forward wurden geprobt.
- [ ] Datenlöschung/-archivierung mit allen abhängigen Entitäten ist getestet.

**Messbare Schwellen:** 100 % Migrationstests grün; 0 ignorierte DDL-Fehler; Restore innerhalb RTO ___; Datenverlust maximal RPO ___.

**Sign-off:** Database Owner ___ / Engineering Lead ___ / Operations ___ / Datum ___

## 7. CI/CD und Supply Chain

- [ ] Main-Branch ist geschützt; Reviews und alle Pflichtchecks sind nicht umgehbar.
- [ ] Build ist aus sauberem Checkout reproduzierbar und nutzt gepinnte Tool-/Dependency-Versionen.
- [ ] Abhängigkeiten sind gelockt; `node_modules`, Secrets und generierte Reports sind nicht versioniert.
- [ ] Unit-, PostgreSQL-, Migration-, Security-, integrierte E2E- und Accessibility-Tests laufen automatisiert.
- [ ] SAST, SCA, Secret, License, SBOM, Container und IaC Scans laufen auf demselben finalen Artefakt.
- [ ] OCI-Image läuft non-root, ist minimal, unveränderlich, signiert und anhand Digest deployt.
- [ ] Staging und Produktion werden ausschließlich über versionierte IaC/Deploymentdefinitionen verändert.
- [ ] Deployments sind genehmigt, auditierbar, schrittweise und besitzen automatisches Health-Gate.
- [ ] Datenbankmigration und App-Rollout sind kompatibel mit Blue-Green/Canary bzw. Expand-Contract.
- [ ] Artefakte, SBOM, Scan-/Testreports und Freigaben sind unveränderlich archiviert.

**Sign-off:** DevOps/Platform ___ / Release Manager ___ / Security ___ / Datum ___

## 8. Infrastruktur und Konfiguration

- [ ] Produktion startet bei fehlender Pflichtkonfiguration fail-closed; keine unsicheren Defaults.
- [ ] Entwicklungs-, Test-, Staging- und Produktionssecrets/-konten sind strikt getrennt.
- [ ] TLS 1.2+ und HSTS sind aktiv; Zertifikatsablauf und Rotation werden überwacht.
- [ ] Reverse Proxy/WAF, Request-/Body-Limits, vertrauenswürdige Proxyheader und reale Client-IP sind korrekt.
- [ ] CORS-Origin, öffentliche Basis-URL, Cookie-Domain, PSP-/Mail-Endpunkte und Währung wurden geprüft.
- [ ] Netzwerkzugriff folgt Least Privilege; DB und interne Endpunkte sind nicht öffentlich.
- [ ] Verschlüsselung at rest, Key Ownership, Rotation und Backupverschlüsselung sind dokumentiert.
- [ ] Readiness/Liveness/Startup-Probes und Graceful Shutdown sind getestet.
- [ ] Ressourcenrequests/-limits, Autoscalinggrenzen, Connection Pools und Quoten basieren auf Lasttests.
- [ ] DNS-, Zertifikats-, Secret- und Provider-Konfigurationsänderungen besitzen Vier-Augen-Review.

**Sign-off:** Platform Owner ___ / Security ___ / Datum ___

## 9. Observability und Incident Response

- [ ] Strukturierte Logs enthalten Request-/Trace-ID, aber keine Tokens, Secrets, unnötige PII oder vollständige Finanzdaten.
- [ ] Metriken für HTTP, JVM, DB, Pools, Queue/Outbox, Mail, PSP, Webhooks, Buchungen, Ledger und Reconciliation sind sichtbar.
- [ ] Traces verbinden Checkout, Provider-Aufruf, Webhook, Ledger und Notification.
- [ ] SLOs und Error Budgets sind definiert; Dashboards zeigen aktuelle und historische Lage.
- [ ] Alarme für Paymentabweichung, erhöhte Fehler/Latency, Auth-Angriffe, Admin-/Merchant-Änderung, Queue-Stau, DB-Sättigung und Backupfehler wurden synthetisch ausgelöst.
- [ ] On-call, Eskalation, Severity, Kommunikationskanäle und Statusseite sind benannt.
- [ ] Security-, Privacy-, Payment-, Verfügbarkeits- und Datenintegritäts-Runbooks wurden geübt.
- [ ] Forensik-/Auditdaten sind manipulationsgeschützt, zeitlich synchronisiert und gemäß Retention auffindbar.
- [ ] Break-glass-Aktionen sind minimal, zeitlich begrenzt und nachträglich geprüft.

**Messbare Schwellen:** Alarmzustellung < ___ Min.; Acknowledgement Sev-1 < ___ Min.; Paymentabweichung erkannt < ___ Min.; Logs 0 Secret-Treffer.

**Sign-off:** SRE/Operations ___ / Security ___ / Support ___ / Datum ___

## 10. Performance und Capacity

- [ ] Erwartete Nenn-, Peak- und Wachstumsraten pro Journey sind festgelegt.
- [ ] Last-, Spike- und 8h-Soak-Lauf wurden auf produktionsnaher Topologie und Datenmenge bestanden.
- [ ] p95/p99, Error Rate, DB/CPU/Memory/Pool-Auslastung und Queryzahl liegen innerhalb der Ziele.
- [ ] Öffentliche und Admin-Listen sind paginiert und besitzen harte Maximalgrößen.
- [ ] N+1-Abfragen sind für Kernlisten ausgeschlossen; Indizes sind anhand Query-Plänen verifiziert.
- [ ] Letzter Slot, Payment-Replay und Statusupdate wurden unter hoher Parallelität getestet.
- [ ] Rate Limits und Backpressure degradieren kontrolliert, ohne Datenverlust oder unfairen Lockout.
- [ ] Kapazitätsgrenzen und Skalierungsschritte sind im Runbook dokumentiert.

**Initiale Mindestziele:** p95 Read < 500 ms; p99 Read < 1 s; HTTP 5xx < 0,1 %; kein stetiges Ressourcenwachstum; 0 Datenintegritätsfehler. Abweichende Ziele müssen vor dem Test beschlossen werden.

**Sign-off:** Performance Owner ___ / Platform ___ / Product ___ / Datum ___

## 11. Accessibility und UX

- [ ] Kernjourneys erfüllen WCAG 2.2 AA; keine kritischen/hohen offenen Befunde.
- [ ] Login, Suche, Buchung, Checkout, Chat, Provider und Admin sind vollständig per Tastatur bedienbar.
- [ ] Modals besitzen Semantik, Fokusfalle, Fokusreturn und Escape; Tabs/Karten verwenden korrekte native/ARIA-Patterns.
- [ ] Screenreader kündigt Namen, Rollen, Werte, Fehler, Status und Live-Updates verständlich an.
- [ ] Sichtbarer Fokus, Kontrast, Touch-Ziele, 200-%-Zoom und 400-%-Reflow sind manuell geprüft.
- [ ] Loading-, Empty-, Error-, Timeout- und Retry-Zustände verhindern Datenverlust und Doppelaktionen.
- [ ] Texte, Preise, Gebühren, Storno- und Datenschutzinformationen sind verständlich und konsistent.
- [ ] Automatisierter axe/Pa11y-Report und manueller NVDA/VoiceOver-Bericht sind archiviert.

**Sign-off:** Accessibility/UX ___ / Product ___ / Datum ___

## 12. Backup, Restore und Disaster Recovery

- [ ] Verschlüsselte automatische DB-/Objekt-Storage-Backups sind aktiv, überwacht und geografisch angemessen getrennt.
- [ ] RPO ___ und RTO ___ wurden von Business/Operations genehmigt.
- [ ] Vollrestore in isolierter Umgebung ist mit Integritäts- und Anwendungssmoke bestanden.
- [ ] Point-in-time Recovery und Wiederanlauf von Outbox/Payment-Reconciliation sind getestet.
- [ ] Restore berücksichtigt gelöschte/gesperrte Nutzer und verhindert unkontrollierte Wiederveröffentlichung.
- [ ] Backupzugriff, Schlüssel, Rotation, Retention und Löschung folgen Least Privilege.
- [ ] Verantwortliche und Ersatzpersonen können das Runbook ohne Autor der Funktion ausführen.

**Sign-off:** Operations ___ / Database Owner ___ / Privacy ___ / Datum ___

## 13. Pre-Production-Abnahme (T-7 bis T-1)

- [ ] Release Candidate ist per Commit, Image-Digest, SBOM und Migrationsstand eingefroren.
- [ ] Vollständige Test-/Scan-Suite ist auf exakt diesem Artefakt grün.
- [ ] Staging verwendet produktionsnahe Topologie, TLS, Proxy, Secrets, Limits und Observability.
- [ ] PSP-Live-Konfiguration wurde im Vier-Augen-Prinzip validiert; zulässige minimale Produktionstransaktion ist geplant.
- [ ] Migration, Backup, Restore und Roll-forward/rollback wurden mit diesem Release geprobt.
- [ ] Offene Findings besitzen Severity, Owner, Frist und formale Akzeptanz; kein Critical/High bleibt offen.
- [ ] Support, Moderation, Finance, On-call und Incident Commander kennen Releasefenster und Runbooks.
- [ ] Statusseite, Nutzerkommunikation, Wartungsfenster und Rollback-Kommunikation sind vorbereitet.
- [ ] Monitoring-Dashboards und Alarme sind während des Launchfensters besetzt.
- [ ] Go/No-Go-Meeting mit allen Sign-offs wurde protokolliert.

## 14. Launch-Day-Runbook

- [ ] Change Freeze aktiv; Incident Commander, Release Manager und Facheigentümer anwesend.
- [ ] Backup/PITR-Stand und Restorebereitschaft unmittelbar vor Deployment bestätigt.
- [ ] Artefakt-Digest, Konfiguration, Secret-Versionen und Migrationsplan nochmals abgeglichen.
- [ ] Deployment schrittweise ausrollen; Health-, Error-, DB-, Queue- und Paymentmetriken nach jeder Stufe prüfen.
- [ ] Synthetischer Smoke: Registrierung, Login, Suche, Buchung, Zahlung, Webhook, Delivery, Review und Admin-Audit.
- [ ] Kleine echte bzw. zugelassene Produktionstransaktion einschließlich Reconciliation und Rückabwicklung prüfen.
- [ ] Für mindestens ___ Stunden verstärkte Überwachung mit definierten Checkpoints durchführen.
- [ ] Bei Stop-Schwelle sofort Traffic stoppen/Release zurückrollen und Incidentprozess starten.
- [ ] Abschlussstatus, Abweichungen, Entscheidungen und Evidence protokollieren.

### Sofortige Rollback-/Stop-Schwellen

- Jede unautorisierte Daten-/Rollen-/Paymentänderung.
- Jede Ledger-/PSP-/Bank-Abweichung oder doppelte Belastung.
- Datenverlust, fehlerhafte Migration oder nicht erklärbare Referenzverletzung.
- 5xx > 1 % für 5 Minuten oder p99 > 3× Ziel für 10 Minuten, sofern nicht kontrollierte externe Störung.
- Kritischer Secret-/PII-Leak, Account-Takeover oder aktiver Angriff.
- Queue-/Webhook-Rückstau außerhalb definierter Recovery-Kapazität.

## 15. Post-Launch (T+1 bis T+14)

- [ ] Tägliche Ledger-/PSP-/Bank-Reconciliation ohne ungeklärte Abweichung.
- [ ] Security-, Error-, Performance-, Funnel-, Support- und Moderationsmetriken täglich reviewen.
- [ ] Backups und mindestens ein Restore-Sample verifizieren.
- [ ] Fehlgeschlagene Events/DLQ, Stornos, Refunds, Disputes und KYC-Ausnahmen bearbeiten.
- [ ] Nutzerfeedback und Accessibility-Probleme triagieren; Critical/High sofort eskalieren.
- [ ] Release-Retrospektive mit SLO, Incidents, Findings und konkreten Owners durchführen.
- [ ] Temporäre Launchrechte/-secrets entfernen und Break-glass-Zugriffe prüfen.

## 16. Formale Go/No-Go-Entscheidung

| Domäne | Verantwortliche Person | Entscheidung | Datum | Evidence-Link |
|---|---|---|---|---|
| Product |  | Go / No-Go |  |  |
| Engineering |  | Go / No-Go |  |  |
| Application Security |  | Go / No-Go |  |  |
| Platform/SRE |  | Go / No-Go |  |  |
| Database |  | Go / No-Go |  |  |
| Finance/Payments |  | Go / No-Go |  |  |
| Privacy/DPO |  | Go / No-Go |  |  |
| Legal/Compliance |  | Go / No-Go |  |  |
| Trust & Safety |  | Go / No-Go |  |  |
| Support/Operations |  | Go / No-Go |  |  |
| Accessibility/UX |  | Go / No-Go |  |  |

**Endentscheidung:** Go / No-Go  
**Release Manager:** ___  
**Zeitpunkt:** ___  
**Release-Digest/Commit:** ___  
**Begründung und Restrestrisiken:** ___

Ein „Go“ ist nur gültig, wenn alle harten Stop-Kriterien falsch, alle P0/P1-Gates erfüllt und sämtliche Domänenfreigaben dokumentiert sind. Zeitdruck oder bereits entstandene Kosten ändern diese Regel nicht.
