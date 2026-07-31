package me.nawa.auth.refresh;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RefreshTokenProviderTest {
    private static final long REFRESH_TOKEN_TTL_SECONDS = 1_209_600;
    private static final Instant ISSUED_AT =
            Instant.parse("2026-07-31T00:00:00Z");

    @Test
    void issueRefreshToken_success_returnsOpaqueToken() {
        RefreshTokenProvider provider = createProvider();

        RefreshToken token = provider.issueRefreshToken();
        String[] tokenParts = token.getValue().split("\\.");

        assertEquals(2, tokenParts.length);
        assertEquals(token.getSessionId(), UUID.fromString(tokenParts[0]));
        assertEquals(
                32,
                Base64.getUrlDecoder().decode(tokenParts[1]).length
        );
        assertEquals(ISSUED_AT, token.getIssuedAt());
        assertEquals(
                ISSUED_AT.plusSeconds(REFRESH_TOKEN_TTL_SECONDS),
                token.getExpiresAt()
        );
    }

    @Test
    void issueRefreshToken_twice_returnsDifferentTokens() {
        RefreshTokenProvider provider = createProvider();

        RefreshToken first = provider.issueRefreshToken();
        RefreshToken second = provider.issueRefreshToken();

        assertNotEquals(first.getSessionId(), second.getSessionId());
        assertNotEquals(first.getValue(), second.getValue());
    }

    @Test
    void matches_originalAndChangedToken_returnsExpectedResult() {
        RefreshTokenProvider provider = createProvider();
        RefreshToken token = provider.issueRefreshToken();
        String tokenHash = provider.hashToken(token.getValue());
        RefreshToken anotherToken = provider.issueRefreshToken();

        assertTrue(provider.matches(token.getValue(), tokenHash));
        assertFalse(provider.matches(anotherToken.getValue(), tokenHash));
    }

    @Test
    void extractSessionId_malformedToken_throwsIllegalArgumentException() {
        RefreshTokenProvider provider = createProvider();

        assertThrows(
                IllegalArgumentException.class,
                () -> provider.extractSessionId("not-a-refresh-token")
        );
    }

    private RefreshTokenProvider createProvider() {
        return new RefreshTokenProvider(
                REFRESH_TOKEN_TTL_SECONDS,
                Clock.fixed(ISSUED_AT, ZoneOffset.UTC),
                new SecureRandom()
        );
    }
}
