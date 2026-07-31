package me.nawa.auth.refresh;

import me.nawa.auth.exception.AuthErrorCode;
import me.nawa.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RefreshTokenServiceTest {
    private static final Instant CURRENT_TIME =
            Instant.parse("2026-07-31T00:00:00Z");

    private RefreshTokenProvider refreshTokenProvider;
    private InMemoryRefreshTokenStore refreshTokenStore;
    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        refreshTokenProvider = new RefreshTokenProvider(
                1_209_600,
                Clock.fixed(CURRENT_TIME, ZoneOffset.UTC),
                new SecureRandom()
        );
        refreshTokenStore = new InMemoryRefreshTokenStore();
        refreshTokenService = new RefreshTokenServiceImpl(
                refreshTokenProvider,
                refreshTokenStore
        );
    }

    @Test
    void issueRefreshToken_validMember_storesHashedSession() {
        RefreshToken token = refreshTokenService.issueRefreshToken(42L);

        RefreshTokenSession session = refreshTokenStore
                .findBySessionId(token.getSessionId())
                .orElseThrow();
        assertEquals(42L, session.getMemberId());
        assertNotEquals(token.getValue(), session.getTokenHash());
        assertTrue(
                refreshTokenProvider.matches(
                        token.getValue(),
                        session.getTokenHash()
                )
        );
    }

    @Test
    void rotateRefreshToken_validToken_replacesCurrentToken() {
        RefreshToken current = refreshTokenService.issueRefreshToken(42L);

        RefreshToken replacement =
                refreshTokenService.rotateRefreshToken(current.getValue());

        assertEquals(current.getSessionId(), replacement.getSessionId());
        assertNotEquals(current.getValue(), replacement.getValue());
        RefreshTokenSession stored = refreshTokenStore
                .findBySessionId(current.getSessionId())
                .orElseThrow();
        assertFalse(
                refreshTokenProvider.matches(
                        current.getValue(),
                        stored.getTokenHash()
                )
        );
        assertTrue(
                refreshTokenProvider.matches(
                        replacement.getValue(),
                        stored.getTokenHash()
                )
        );
    }

    @Test
    void rotateRefreshToken_reusedToken_revokesCurrentSession() {
        RefreshToken original = refreshTokenService.issueRefreshToken(42L);
        refreshTokenService.rotateRefreshToken(original.getValue());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> refreshTokenService.rotateRefreshToken(original.getValue())
        );

        assertEquals(
                AuthErrorCode.REFRESH_TOKEN_REUSE_DETECTED,
                exception.getErrorCode()
        );
        assertTrue(
                refreshTokenStore.findBySessionId(
                        original.getSessionId()
                ).isEmpty()
        );
    }

    @Test
    void rotateRefreshToken_missingSession_throwsInvalidRefreshToken() {
        RefreshToken token = refreshTokenProvider.issueRefreshToken();

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> refreshTokenService.rotateRefreshToken(token.getValue())
        );

        assertEquals(
                AuthErrorCode.INVALID_REFRESH_TOKEN,
                exception.getErrorCode()
        );
    }

    @Test
    void rotateRefreshToken_malformedToken_throwsInvalidRefreshToken() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> refreshTokenService.rotateRefreshToken("malformed")
        );

        assertEquals(
                AuthErrorCode.INVALID_REFRESH_TOKEN,
                exception.getErrorCode()
        );
    }

    private static final class InMemoryRefreshTokenStore
            implements RefreshTokenStore {
        private final Map<UUID, RefreshTokenSession> sessions = new HashMap<>();

        @Override
        public void save(RefreshTokenSession session) {
            sessions.put(session.getSessionId(), session);
        }

        @Override
        public Optional<RefreshTokenSession> findBySessionId(UUID sessionId) {
            return Optional.ofNullable(sessions.get(sessionId));
        }

        @Override
        public RefreshTokenRotationResult rotate(
                UUID sessionId,
                String currentTokenHash,
                RefreshTokenSession replacementSession) {
            RefreshTokenSession current = sessions.get(sessionId);
            if (current == null) {
                return RefreshTokenRotationResult.NOT_FOUND;
            }
            if (!current.getTokenHash().equals(currentTokenHash)) {
                sessions.remove(sessionId);
                return RefreshTokenRotationResult.REUSE_DETECTED;
            }
            sessions.put(sessionId, replacementSession);
            return RefreshTokenRotationResult.ROTATED;
        }

        @Override
        public void deleteBySessionId(UUID sessionId) {
            sessions.remove(sessionId);
        }
    }
}
