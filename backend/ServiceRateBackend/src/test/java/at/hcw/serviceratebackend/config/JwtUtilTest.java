package at.hcw.serviceratebackend.config;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Base64;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilTest {

    private static final String ISSUER = "servicerate";
    private static final String AUDIENCE = "servicerate-api";
    private static final String KEY_ID = "key-2026-07";
    private static final long EXPIRATION_MS = 60_000;

    @Test
    void generatedTokenContainsExpectedIdentityAndIsValid() {
        JwtUtil jwtUtil = jwtUtil(secret(1, 32), ISSUER, AUDIENCE, KEY_ID);

        String token = jwtUtil.generateToken("customer@example.com", "CUSTOMER");

        assertThat(jwtUtil.isTokenValid(token)).isTrue();
        assertThat(jwtUtil.extractSubject(token)).isEqualTo("customer@example.com");
        assertThat(jwtUtil.extractAccountType(token)).isEqualTo("CUSTOMER");
    }

    @Test
    void tokenSignedWithRotatedOutKeyIsRejected() {
        JwtUtil oldKey = jwtUtil(secret(1, 32), ISSUER, AUDIENCE, "key-old");
        JwtUtil currentKey = jwtUtil(secret(2, 32), ISSUER, AUDIENCE, KEY_ID);

        String oldToken = oldKey.generateToken("customer@example.com", "CUSTOMER");

        assertThat(currentKey.isTokenValid(oldToken)).isFalse();
    }

    @Test
    void tokenWithWrongIssuerIsRejected() {
        JwtUtil current = jwtUtil(secret(1, 32), ISSUER, AUDIENCE, KEY_ID);
        JwtUtil wrongIssuer = jwtUtil(secret(1, 32), "other-issuer", AUDIENCE, KEY_ID);

        assertThat(current.isTokenValid(wrongIssuer.generateToken("customer@example.com", "CUSTOMER"))).isFalse();
    }

    @Test
    void tokenWithWrongAudienceIsRejected() {
        JwtUtil current = jwtUtil(secret(1, 32), ISSUER, AUDIENCE, KEY_ID);
        JwtUtil wrongAudience = jwtUtil(secret(1, 32), ISSUER, "other-api", KEY_ID);

        assertThat(current.isTokenValid(wrongAudience.generateToken("customer@example.com", "CUSTOMER"))).isFalse();
    }

    @Test
    void tokenWithUnknownKeyIdIsRejected() {
        JwtUtil current = jwtUtil(secret(1, 32), ISSUER, AUDIENCE, KEY_ID);
        JwtUtil wrongKeyId = jwtUtil(secret(1, 32), ISSUER, AUDIENCE, "unknown-key");

        assertThat(current.isTokenValid(wrongKeyId.generateToken("customer@example.com", "CUSTOMER"))).isFalse();
    }

    @Test
    void tokenWithDifferentHmacAlgorithmIsRejected() {
        byte[] sharedSecret = secret(3, 64);
        JwtUtil current = jwtUtil(sharedSecret, ISSUER, AUDIENCE, KEY_ID);
        Date issuedAt = new Date();
        String hs512Token = Jwts.builder()
                .subject("customer@example.com")
                .claim("accountType", "CUSTOMER")
                .issuer(ISSUER)
                .audience().add(AUDIENCE).and()
                .issuedAt(issuedAt)
                .expiration(new Date(issuedAt.getTime() + EXPIRATION_MS))
                .header().keyId(KEY_ID).and()
                .signWith(Keys.hmacShaKeyFor(sharedSecret), Jwts.SIG.HS512)
                .compact();

        assertThat(current.isTokenValid(hs512Token)).isFalse();
    }

    @Test
    void constructorRejectsMalformedOrShortSecrets() {
        assertThatThrownBy(() -> new JwtUtil("not-base64!", ISSUER, AUDIENCE, KEY_ID, EXPIRATION_MS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("JWT-Secret muss gültiges Base64 sein.");

        assertThatThrownBy(() -> new JwtUtil(
                Base64.getEncoder().encodeToString(secret(1, 31)),
                ISSUER,
                AUDIENCE,
                KEY_ID,
                EXPIRATION_MS
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("JWT-Secret muss mindestens 32 zufällige Bytes enthalten.");
    }

    @Test
    void constructorRejectsMissingMetadataAndInvalidExpiration() {
        String validSecret = Base64.getEncoder().encodeToString(secret(1, 32));

        assertThatThrownBy(() -> new JwtUtil(validSecret, " ", AUDIENCE, KEY_ID, EXPIRATION_MS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("JWT-Issuer darf nicht leer sein.");
        assertThatThrownBy(() -> new JwtUtil(validSecret, ISSUER, " ", KEY_ID, EXPIRATION_MS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("JWT-Audience darf nicht leer sein.");
        assertThatThrownBy(() -> new JwtUtil(validSecret, ISSUER, AUDIENCE, " ", EXPIRATION_MS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("JWT-Key-ID darf nicht leer sein.");
        assertThatThrownBy(() -> new JwtUtil(validSecret, ISSUER, AUDIENCE, KEY_ID, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("JWT-Ablaufzeit muss positiv sein.");
    }

    private JwtUtil jwtUtil(byte[] secret, String issuer, String audience, String keyId) {
        return new JwtUtil(
                Base64.getEncoder().encodeToString(secret),
                issuer,
                audience,
                keyId,
                EXPIRATION_MS
        );
    }

    private static byte[] secret(int value, int length) {
        byte[] secret = new byte[length];
        Arrays.fill(secret, (byte) value);
        return secret;
    }
}
