package at.hcw.serviceratebackend.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtUtil {

    private static final int MINIMUM_KEY_BYTES = 32;

    private final SecretKey key;
    private final String issuer;
    private final String audience;
    private final String keyId;
    private final long expirationTime;

    public JwtUtil(
            @Value("${security.jwt.secret-base64}") String secretBase64,
            @Value("${security.jwt.issuer}") String issuer,
            @Value("${security.jwt.audience}") String audience,
            @Value("${security.jwt.key-id}") String keyId,
            @Value("${security.jwt.expiration-ms}") long expirationTime
    ) {
        byte[] secretBytes;
        try {
            secretBytes = Base64.getDecoder().decode(requireText(secretBase64, "JWT-Secret"));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("JWT-Secret muss gültiges Base64 sein.", ex);
        }
        if (secretBytes.length < MINIMUM_KEY_BYTES) {
            throw new IllegalArgumentException("JWT-Secret muss mindestens 32 zufällige Bytes enthalten.");
        }
        if (expirationTime <= 0) {
            throw new IllegalArgumentException("JWT-Ablaufzeit muss positiv sein.");
        }

        this.key = Keys.hmacShaKeyFor(secretBytes);
        this.issuer = requireText(issuer, "JWT-Issuer");
        this.audience = requireText(audience, "JWT-Audience");
        this.keyId = requireText(keyId, "JWT-Key-ID");
        this.expirationTime = expirationTime;
    }

    public String generateToken(String email, String accountType) {
        Date issuedAt = new Date();
        return Jwts.builder()
                .subject(email)
                .claim("accountType", accountType)
                .issuer(issuer)
                .audience().add(audience).and()
                .id(UUID.randomUUID().toString())
                .issuedAt(issuedAt)
                .expiration(new Date(issuedAt.getTime() + expirationTime))
                .header().keyId(keyId).and()
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    // Liest alle Claims aus dem Token (wirft eine Exception, wenn die Signatur ungültig oder das Token abgelaufen ist)
    private Claims parseClaims(String token) {
        var parsed = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)
                .requireAudience(audience)
                .build()
                .parseSignedClaims(token);
        if (!keyId.equals(parsed.getHeader().getKeyId())) {
            throw new JwtException("Unbekannte JWT-Key-ID.");
        }
        if (!Jwts.SIG.HS256.getId().equals(parsed.getHeader().getAlgorithm())) {
            throw new JwtException("Nicht erlaubter JWT-Algorithmus.");
        }
        return parsed.getPayload();
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

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " darf nicht leer sein.");
        }
        return value.trim();
    }
}
