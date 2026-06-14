package at.hcw.serviceratebackend.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;
import javax.crypto.SecretKey;
import java.util.Date;

@Component
public class JwtUtil {
    // Ein geheimer Schlüssel (in einem echten Projekt käme der aus den application.properties)
    private final String SECRET = "MeinSuperGeheimerServiceRateKeyDerMindestens32ZeichenLangIst!";
    private final SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes());

    // Token ist 24 Stunden gültig
    private final long EXPIRATION_TIME = 86400000;

    public String generateToken(String email, String accountType) {
        return Jwts.builder()
                .subject(email)
                .claim("accountType", accountType) // Wir packen die Info ob Kunde oder Handwerker direkt in den Token
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .signWith(key)
                .compact();
    }

    // Liest alle Claims aus dem Token (wirft eine Exception, wenn die Signatur ungültig oder das Token abgelaufen ist)
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // Prüft, ob das Token gültig ist (korrekte Signatur & nicht abgelaufen)
    public boolean isTokenValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // Holt den Subject (= E-Mail) aus dem Token
    public String extractSubject(String token) {
        return parseClaims(token).getSubject();
    }

    // Holt den Claim "accountType" (CUSTOMER oder PROVIDER) aus dem Token
    public String extractAccountType(String token) {
        return parseClaims(token).get("accountType", String.class);
    }
}