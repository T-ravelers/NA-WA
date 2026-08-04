package me.nawa.auth.oauth.identity;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoogleIdTokenConfigTest {
    private static final String CLIENT_ID = "google-client-id";
    private static final Instant NOW = Instant.parse(
            "2026-08-03T05:30:00Z"
    );
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final OAuth2TokenValidator<Jwt> validator =
            GoogleIdTokenConfig.createGoogleValidator(CLIENT_ID, CLOCK);

    @Test
    void validate_acceptsHttpsGoogleIssuerAndExpectedAudience() {
        assertFalse(validator.validate(jwt(
                "https://accounts.google.com",
                List.of("another-client", CLIENT_ID),
                NOW.plusSeconds(300)
        )).hasErrors());
    }

    @Test
    void validate_acceptsLegacyGoogleIssuer() {
        assertFalse(validator.validate(jwt(
                "accounts.google.com",
                List.of(CLIENT_ID),
                NOW.plusSeconds(300)
        )).hasErrors());
    }

    @Test
    void validate_rejectsUntrustedIssuer() {
        assertTrue(validator.validate(jwt(
                "https://evil.example",
                List.of(CLIENT_ID),
                NOW.plusSeconds(300)
        )).hasErrors());
    }

    @Test
    void validate_rejectsDifferentAudience() {
        assertTrue(validator.validate(jwt(
                "https://accounts.google.com",
                List.of("another-client"),
                NOW.plusSeconds(300)
        )).hasErrors());
    }

    @Test
    void validate_rejectsExpiredTokenOutsideClockSkew() {
        assertTrue(validator.validate(jwt(
                "https://accounts.google.com",
                List.of(CLIENT_ID),
                NOW.minusSeconds(61)
        )).hasErrors());
    }

    @Test
    void validate_rejectsMissingExpiration() {
        Jwt jwt = Jwt.withTokenValue("id-token")
                .header("alg", "RS256")
                .issuer("https://accounts.google.com")
                .audience(List.of(CLIENT_ID))
                .subject("google-user-id")
                .issuedAt(NOW.minusSeconds(10))
                .build();

        assertTrue(validator.validate(jwt).hasErrors());
    }

    private Jwt jwt(
            String issuer,
            List<String> audience,
            Instant expiresAt) {
        return Jwt.withTokenValue("id-token")
                .header("alg", "RS256")
                .issuer(issuer)
                .audience(audience)
                .subject("google-user-id")
                .issuedAt(NOW.minusSeconds(300))
                .expiresAt(expiresAt)
                .claim("nonce", "expected-nonce")
                .build();
    }
}
