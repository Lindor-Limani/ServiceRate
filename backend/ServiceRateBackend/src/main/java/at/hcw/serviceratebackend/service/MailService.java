package at.hcw.serviceratebackend.service;

import at.hcw.serviceratebackend.model.entity.Booking;
import at.hcw.serviceratebackend.model.entity.Report;
import at.hcw.serviceratebackend.model.entity.Review;
import at.hcw.serviceratebackend.model.entity.ServiceOffering;
import at.hcw.serviceratebackend.model.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import javax.net.ssl.SSLSocketFactory;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MailService {

    @Value("${app.mail.mode:console}")
    private String mode;

    @Value("${app.mail.host:}")
    private String host;

    @Value("${app.mail.port:587}")
    private int port;

    @Value("${app.mail.username:}")
    private String username;

    @Value("${app.mail.password:}")
    private String password;

    @Value("${app.mail.from:noreply@servicerate.local}")
    private String from;

    @Value("${app.mail.starttls:true}")
    private boolean startTls;

    @Value("${app.mail.ssl:false}")
    private boolean ssl;

    @Value("${app.frontend-base-url:http://localhost:5500/frontend}")
    private String frontendBaseUrl;

    @Value("${app.backend-base-url:http://localhost:8081}")
    private String backendBaseUrl;

    @Value("${app.dev.log-tokens:false}")
    private boolean logTokens;

    public void sendVerificationMail(User user) {
        String link = UriComponentsBuilder
                .fromUriString(backendBaseUrl)
                .path("/api/auth/verify-email")
                .queryParam("token", user.getEmailVerificationToken())
                .toUriString();

        if (logTokens) {
            System.out.printf("%n--- ServiceRate Dev Verification Link ---%nUser: %s%nToken: %s%nLink: %s%n--- End Dev Verification Link ---%n",
                    user.getEmail(), user.getEmailVerificationToken(), link);
        }

        String body = "Hallo " + displayName(user) + ",\n\n"
                + "bitte verifiziere deine E-Mail-Adresse fuer ServiceRate:\n\n"
                + link + "\n\n"
                + "Danach kannst du Services buchen oder als Anbieter Services erstellen.\n\n"
                + "Dein ServiceRate Team\n";

        send(user.getEmail(), "ServiceRate E-Mail verifizieren", body);
    }

    public void sendPasswordResetMail(User user, String token) {
        String page = "PROVIDER".equals(user.getAccountType()) ? "provider-dashboard.html" : "customer-app.html";
        String link = UriComponentsBuilder
                .fromUriString(frontendBaseUrl + "/" + page)
                .queryParam("resetToken", token)
                .toUriString();

        String body = "Hallo " + displayName(user) + ",\n\n"
                + "mit diesem Link kannst du dein ServiceRate Passwort neu setzen:\n\n"
                + link + "\n\n"
                + "Der Link ist 30 Minuten gueltig. Falls du das nicht angefordert hast, ignoriere diese Mail.\n\n"
                + "Dein ServiceRate Team\n";

        send(user.getEmail(), "ServiceRate Passwort neu setzen", body);
    }

    public void sendBookingCreatedMail(Booking booking) {
        User customer = booking.getCustomer();
        User provider = providerOf(booking);
        ServiceOffering service = booking.getServiceOffering();
        if (provider == null || provider.getEmail() == null) return;

        String body = "Hallo " + displayName(provider) + ",\n\n"
                + "du hast eine neue Buchungsanfrage erhalten.\n\n"
                + "Service: " + serviceTitle(service) + "\n"
                + "Kunde: " + displayName(customer) + "\n"
                + "Wunschtermin: " + valueOrDash(booking.getBookingDate()) + "\n\n"
                + "Bitte pruefe die Anfrage in deinem ServiceRate Dashboard.\n\n"
                + "Dein ServiceRate Team\n";

        trySend(provider.getEmail(), "Neue Buchungsanfrage bei ServiceRate", body);
    }

    public void sendBookingStatusMail(Booking booking) {
        User customer = booking.getCustomer();
        if (customer == null || customer.getEmail() == null) return;

        String body = "Hallo " + displayName(customer) + ",\n\n"
                + "der Status deiner Buchung wurde aktualisiert.\n\n"
                + "Service: " + serviceTitle(booking.getServiceOffering()) + "\n"
                + "Status: " + valueOrDash(booking.getStatus()) + "\n"
                + "Wunschtermin: " + valueOrDash(booking.getBookingDate()) + "\n\n"
                + "Details findest du in deinen Buchungen.\n\n"
                + "Dein ServiceRate Team\n";

        trySend(customer.getEmail(), "Deine ServiceRate Buchung wurde aktualisiert", body);
    }

    public void sendPaymentRecordedMail(Booking booking) {
        User customer = booking.getCustomer();
        User provider = providerOf(booking);
        String subject = "ServiceRate Zahlung bestaetigt";
        String body = "Hallo,\n\n"
                + "fuer folgende Buchung wurde eine Zahlung bestaetigt:\n\n"
                + "Service: " + serviceTitle(booking.getServiceOffering()) + "\n"
                + "Zahlungsart: " + valueOrDash(booking.getPaymentProvider()) + "\n"
                + "Bezahlt am: " + valueOrDash(booking.getPaidAt()) + "\n\n"
                + "Dein ServiceRate Team\n";

        if (customer != null && customer.getEmail() != null) {
            trySend(customer.getEmail(), subject, body.replace("Hallo,", "Hallo " + displayName(customer) + ","));
        }
        if (provider != null && provider.getEmail() != null) {
            trySend(provider.getEmail(), subject, body.replace("Hallo,", "Hallo " + displayName(provider) + ","));
        }
    }

    public void sendDeliveryPublishedMail(Booking booking) {
        User customer = booking.getCustomer();
        if (customer == null || customer.getEmail() == null) return;

        String body = "Hallo " + displayName(customer) + ",\n\n"
                + "fuer deine Buchung wurde eine digitale Lieferung bereitgestellt.\n\n"
                + "Service: " + serviceTitle(booking.getServiceOffering()) + "\n"
                + "Lieferung: " + valueOrDash(booking.getDeliveryLabel()) + "\n"
                + "Gueltig bis: " + valueOrDash(booking.getDeliveryExpiresAt()) + "\n\n"
                + "Der Download ist in deinen Buchungen verfuegbar, sobald die Zahlung abgeschlossen ist.\n\n"
                + "Dein ServiceRate Team\n";

        trySend(customer.getEmail(), "ServiceRate Lieferung verfuegbar", body);
    }

    public void sendReviewCreatedMail(Review review) {
        Booking booking = review.getBooking();
        User provider = providerOf(booking);
        if (provider == null || provider.getEmail() == null) return;

        String body = "Hallo " + displayName(provider) + ",\n\n"
                + "dein Service wurde bewertet.\n\n"
                + "Service: " + serviceTitle(booking == null ? null : booking.getServiceOffering()) + "\n"
                + "Bewertung: " + review.getRating() + " von 5 Sternen\n"
                + "Kommentar: " + valueOrDash(review.getComment()) + "\n\n"
                + "Dein ServiceRate Team\n";

        trySend(provider.getEmail(), "Neue Bewertung bei ServiceRate", body);
    }

    public void sendReportCreatedMail(Report report, List<User> admins) {
        if (admins == null || admins.isEmpty()) return;
        String body = "Hallo Admin,\n\n"
                + "ein neuer Report wurde erstellt.\n\n"
                + "Typ: " + valueOrDash(report.getTargetType()) + "\n"
                + "Grund: " + valueOrDash(report.getReason()) + "\n"
                + "Reporter: " + (report.getReporter() == null ? "-" : report.getReporter().getEmail()) + "\n"
                + "Details: " + valueOrDash(report.getDetails()) + "\n\n"
                + "Bitte pruefe den Report im Admin Dashboard.\n\n"
                + "Dein ServiceRate Team\n";

        admins.stream()
                .filter(admin -> admin.getEmail() != null && !admin.getEmail().isBlank())
                .forEach(admin -> trySend(admin.getEmail(), "Neuer ServiceRate Report", body.replace("Hallo Admin,", "Hallo " + displayName(admin) + ",")));
    }

    public void sendReportStatusMail(Report report) {
        User reporter = report.getReporter();
        if (reporter == null || reporter.getEmail() == null) return;

        String body = "Hallo " + displayName(reporter) + ",\n\n"
                + "deine Meldung wurde aktualisiert.\n\n"
                + "Typ: " + valueOrDash(report.getTargetType()) + "\n"
                + "Status: " + valueOrDash(report.getStatus()) + "\n"
                + "Grund: " + valueOrDash(report.getReason()) + "\n\n"
                + "Danke, dass du ServiceRate sicherer machst.\n\n"
                + "Dein ServiceRate Team\n";

        trySend(reporter.getEmail(), "ServiceRate Meldung aktualisiert", body);
    }

    public void sendServiceStatusMail(ServiceOffering service) {
        User provider = service == null ? null : service.getProvider();
        if (provider == null || provider.getEmail() == null) return;

        String body = "Hallo " + displayName(provider) + ",\n\n"
                + "der Status deines Service wurde aktualisiert.\n\n"
                + "Service: " + serviceTitle(service) + "\n"
                + "Status: " + valueOrDash(service.getStatus()) + "\n\n"
                + "Dein ServiceRate Team\n";

        trySend(provider.getEmail(), "ServiceRate Service-Status aktualisiert", body);
    }

    private void send(String to, String subject, String body) {
        if (!"smtp".equalsIgnoreCase(mode)) {
            System.out.printf("%n--- ServiceRate Mail (console mode) ---%nTo: %s%nSubject: %s%n%n%s%n--- End Mail ---%n",
                    to, subject, body);
            return;
        }

        if (host == null || host.isBlank()) {
            throw new IllegalStateException("SMTP ist aktiviert, aber app.mail.host ist nicht konfiguriert.");
        }

        try {
            sendSmtp(to, subject, body);
        } catch (Exception e) {
            throw new IllegalStateException("E-Mail konnte nicht versendet werden.", e);
        }
    }

    private void trySend(String to, String subject, String body) {
        try {
            send(to, subject, body);
        } catch (Exception e) {
            System.err.printf("ServiceRate Mail konnte nicht gesendet werden: to=%s subject=%s error=%s%n",
                    to, subject, e.getMessage());
        }
    }

    private void sendSmtp(String to, String subject, String body) throws Exception {
        Socket initialSocket = ssl
                ? SSLSocketFactory.getDefault().createSocket(host, port)
                : new Socket(host, port);

        Socket activeSocket = initialSocket;
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(activeSocket.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(activeSocket.getOutputStream(), StandardCharsets.UTF_8));

            expect(in, 220);
            command(out, in, "EHLO servicerate.local", 250);

            if (startTls && !ssl) {
                command(out, in, "STARTTLS", 220);
                activeSocket = ((SSLSocketFactory) SSLSocketFactory.getDefault()).createSocket(activeSocket, host, port, true);
                in = new BufferedReader(new InputStreamReader(activeSocket.getInputStream(), StandardCharsets.UTF_8));
                out = new BufferedWriter(new OutputStreamWriter(activeSocket.getOutputStream(), StandardCharsets.UTF_8));
                command(out, in, "EHLO servicerate.local", 250);
            }

            if (username != null && !username.isBlank()) {
                command(out, in, "AUTH LOGIN", 334);
                command(out, in, Base64.getEncoder().encodeToString(username.getBytes(StandardCharsets.UTF_8)), 334);
                command(out, in, Base64.getEncoder().encodeToString(password.getBytes(StandardCharsets.UTF_8)), 235);
            }

            command(out, in, "MAIL FROM:<" + from + ">", 250);
            command(out, in, "RCPT TO:<" + to + ">", List.of(250, 251));
            command(out, in, "DATA", 354);

            out.write("From: ServiceRate <" + from + ">\r\n");
            out.write("To: <" + to + ">\r\n");
            out.write("Subject: " + subject + "\r\n");
            out.write("MIME-Version: 1.0\r\n");
            out.write("Content-Type: text/plain; charset=UTF-8\r\n");
            out.write("\r\n");
            out.write(body.replace("\n", "\r\n"));
            out.write("\r\n.\r\n");
            out.flush();
            expect(in, 250);
            command(out, in, "QUIT", 221);
        } finally {
            activeSocket.close();
        }
    }

    private void command(BufferedWriter out, BufferedReader in, String command, int expectedCode) throws Exception {
        command(out, in, command, List.of(expectedCode));
    }

    private void command(BufferedWriter out, BufferedReader in, String command, List<Integer> expectedCodes) throws Exception {
        out.write(command + "\r\n");
        out.flush();
        expect(in, expectedCodes);
    }

    private void expect(BufferedReader in, int expectedCode) throws Exception {
        expect(in, List.of(expectedCode));
    }

    private void expect(BufferedReader in, List<Integer> expectedCodes) throws Exception {
        String line = in.readLine();
        if (line == null || line.length() < 3) {
            throw new IllegalStateException("Ungueltige SMTP-Antwort.");
        }
        String lastLine = line;
        while (line.length() > 3 && line.charAt(3) == '-') {
            line = in.readLine();
            if (line == null) break;
            lastLine = line;
        }
        int code = Integer.parseInt(lastLine.substring(0, 3));
        if (!expectedCodes.contains(code)) {
            throw new IllegalStateException("SMTP-Antwort " + code + ": " + lastLine);
        }
    }

    private String displayName(User user) {
        String name = ((user.getFirstName() == null ? "" : user.getFirstName()) + " " +
                (user.getLastName() == null ? "" : user.getLastName())).trim();
        return name.isBlank() ? user.getEmail() : name;
    }

    private User providerOf(Booking booking) {
        if (booking == null || booking.getServiceOffering() == null) return null;
        return booking.getServiceOffering().getProvider();
    }

    private String serviceTitle(ServiceOffering service) {
        return service == null || service.getTitle() == null ? "-" : service.getTitle();
    }

    private String valueOrDash(Object value) {
        if (value == null) return "-";
        String text = String.valueOf(value);
        return text.isBlank() ? "-" : text;
    }
}
