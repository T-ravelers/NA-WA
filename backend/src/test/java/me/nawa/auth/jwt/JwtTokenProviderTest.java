package me.nawa.auth.jwt;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtTokenProviderTest {
    private static final String ISSUER = "nawa";
    private static final long ACCESS_TOKEN_TTL_SECONDS = 900;
    private static final Instant ISSUED_AT = Instant.parse("2026-07-31T00:00:00Z");
    private static final String SECRET = encodeSecret(
            "test-signing-key-that-is-at-least-32-bytes"
    );
    private static final String OTHER_SECRET = encodeSecret(
            "different-signing-key-that-is-at-least-32-bytes"
    );

    @Test
    void issueAndParseAccessToken_validMember_returnsClaims() {
        JwtTokenProvider provider = createProvider(SECRET, ISSUED_AT);

        AccessToken accessToken = provider.issueAccessToken(42L);
        AccessTokenClaims claims = provider.parseAccessToken(accessToken.getValue());

        assertFalse(accessToken.getValue().isBlank());
        assertEquals(ISSUED_AT.plusSeconds(ACCESS_TOKEN_TTL_SECONDS),
                accessToken.getExpiresAt());
        assertEquals(42L, claims.getMemberId());
        assertFalse(claims.getTokenId().isBlank());
        assertEquals(ISSUED_AT, claims.getIssuedAt());
        assertEquals(accessToken.getExpiresAt(), claims.getExpiresAt());
    }

    @Test
    void parseAccessToken_expiredToken_throwsExpiredJwtException() {
        JwtTokenProvider issuer = createProvider(SECRET, ISSUED_AT);
        AccessToken accessToken = issuer.issueAccessToken(42L);
        JwtTokenProvider verifier = createProvider(
                SECRET,
                ISSUED_AT.plusSeconds(ACCESS_TOKEN_TTL_SECONDS + 1)
        );

        assertThrows(
                ExpiredJwtException.class,
                () -> verifier.parseAccessToken(accessToken.getValue())
        );
    }

    @Test
    void parseAccessToken_differentSigningKey_throwsSignatureException() {
        JwtTokenProvider issuer = createProvider(OTHER_SECRET, ISSUED_AT);
        AccessToken accessToken = issuer.issueAccessToken(42L);
        JwtTokenProvider verifier = createProvider(SECRET, ISSUED_AT);

        assertThrows(
                SignatureException.class,
                () -> verifier.parseAccessToken(accessToken.getValue())
        );
    }

    @Test
    void parseAccessToken_missingExpiration_throwsMalformedJwtException() {
        SecretKey signingKey = Keys.hmacShaKeyFor(
                Decoders.BASE64.decode(SECRET)
        );
        String token = Jwts.builder()
                .issuer(ISSUER)
                .subject("42")
                .issuedAt(Date.from(ISSUED_AT))
                .id(UUID.randomUUID().toString())
                .claim("token_type", "access")
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
        JwtTokenProvider provider = createProvider(SECRET, ISSUED_AT);

        assertThrows(
                MalformedJwtException.class,
                () -> provider.parseAccessToken(token)
        );
    }

    @Test
    void issueAccessToken_nonPositiveMemberId_throwsIllegalArgumentException() {
        JwtTokenProvider provider = createProvider(SECRET, ISSUED_AT);

        assertThrows(
                IllegalArgumentException.class,
                () -> provider.issueAccessToken(0L)
        );
    }

    @Test
    void constructor_weakSecret_throwsIllegalArgumentException() {
        String weakSecret = encodeSecret("too-short");

        assertThrows(
                IllegalArgumentException.class,
                () -> createProvider(weakSecret, ISSUED_AT)
        );
    }

    private JwtTokenProvider createProvider(String secret, Instant currentTime) {
        Clock clock = Clock.fixed(currentTime, ZoneOffset.UTC);
        return new JwtTokenProvider(
                secret,
                ISSUER,
                ACCESS_TOKEN_TTL_SECONDS,
                clock
        );
    }

    private static String encodeSecret(String secret) {
        return Base64.getEncoder().encodeToString(
                secret.getBytes(StandardCharsets.UTF_8)
        );
    }
}
