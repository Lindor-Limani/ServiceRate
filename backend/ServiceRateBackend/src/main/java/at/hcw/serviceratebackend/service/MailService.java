package at.hcw.serviceratebackend.service;

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

    public void sendVerificationMail(User user) {
        String link = UriComponentsBuilder
                .fromUriString(backendBaseUrl)
                .path("/api/auth/verify-email")
                .queryParam("token", user.getEmailVerificationToken())
                .toUriString();

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
}
