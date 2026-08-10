package me.nawa.auth.refresh;

import me.nawa.auth.exception.AuthErrorCode;
import me.nawa.common.exception.BusinessException;
import me.nawa.member.domain.MemberAuthState;
import me.nawa.member.mapper.MemberMapper;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RefreshTokenServiceTest {
    private static final Instant CURRENT_TIME =
            Instant.parse("2026-07-31T00:00:00Z");

    private RefreshTokenProvider refreshTokenProvider;
    private InMemoryRefreshTokenStore refreshTokenStore;
    private MemberMapper memberMapper;
    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        refreshTokenProvider = new RefreshTokenProvider(
                1_209_600,
                Clock.fixed(CURRENT_TIME, ZoneOffset.UTC),
                new SecureRandom()
        );
        refreshTokenStore = new InMemoryRefreshTokenStore();
        memberMapper = mock(MemberMapper.class);
        when(memberMapper.findAuthState(anyLong()))
                .thenReturn(memberAuthState("ACTIVE", false));
        refreshTokenService = new RefreshTokenServiceImpl(
                refreshTokenProvider,
                refreshTokenStore,
                memberMapper
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

        RotatedRefreshToken rotated =
                refreshTokenService.rotateRefreshToken(current.getValue());
        RefreshToken replacement = rotated.getToken();

        assertEquals(42L, rotated.getMemberId());
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
    void rotateRefreshToken_suspendedMember_revokesSession() {
        RefreshToken current = refreshTokenService.issueRefreshToken(42L);
        when(memberMapper.findAuthState(42L))
                .thenReturn(memberAuthState("SUSPENDED", false));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> refreshTokenService.rotateRefreshToken(
                        current.getValue()
                )
        );

        assertEquals(
                AuthErrorCode.OAUTH_MEMBER_SUSPENDED,
                exception.getErrorCode()
        );
        assertTrue(refreshTokenStore.findBySessionId(
                current.getSessionId()
        ).isEmpty());
    }

    @Test
    void rotateRefreshToken_withdrawnMember_revokesSession() {
        RefreshToken current = refreshTokenService.issueRefreshToken(42L);
        when(memberMapper.findAuthState(42L))
                .thenReturn(memberAuthState("WITHDRAWN", false));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> refreshTokenService.rotateRefreshToken(
                        current.getValue()
                )
        );

        assertEquals(
                AuthErrorCode.OAUTH_MEMBER_WITHDRAWN,
                exception.getErrorCode()
        );
        assertTrue(refreshTokenStore.findBySessionId(
                current.getSessionId()
        ).isEmpty());
    }

    @Test
    void rotateRefreshToken_deletedMember_revokesSession() {
        RefreshToken current = refreshTokenService.issueRefreshToken(42L);
        when(memberMapper.findAuthState(42L))
                .thenReturn(memberAuthState("ACTIVE", true));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> refreshTokenService.rotateRefreshToken(
                        current.getValue()
                )
        );

        assertEquals(
                AuthErrorCode.OAUTH_MEMBER_WITHDRAWN,
                exception.getErrorCode()
        );
        assertTrue(refreshTokenStore.findBySessionId(
                current.getSessionId()
        ).isEmpty());
    }

    @Test
    void rotateRefreshToken_missingMember_revokesSession() {
        RefreshToken current = refreshTokenService.issueRefreshToken(42L);
        when(memberMapper.findAuthState(42L)).thenReturn(null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> refreshTokenService.rotateRefreshToken(
                        current.getValue()
                )
        );

        assertEquals(
                AuthErrorCode.OAUTH_MEMBER_WITHDRAWN,
                exception.getErrorCode()
        );
        assertTrue(refreshTokenStore.findBySessionId(
                current.getSessionId()
        ).isEmpty());
    }

    @Test
    void rotateRefreshToken_memberLookupFailure_doesNotRotateOrDeleteSession() {
        RefreshToken current = refreshTokenService.issueRefreshToken(42L);
        when(memberMapper.findAuthState(42L))
                .thenThrow(new IllegalStateException("database unavailable"));

        assertThrows(
                IllegalStateException.class,
                () -> refreshTokenService.rotateRefreshToken(
                        current.getValue()
                )
        );

        RefreshTokenSession stored = refreshTokenStore
                .findBySessionId(current.getSessionId())
                .orElseThrow();
        assertTrue(refreshTokenProvider.matches(
                current.getValue(),
                stored.getTokenHash()
        ));
    }

    @Test
    void rotateRefreshToken_unknownMemberStatus_doesNotRotateOrDeleteSession() {
        RefreshToken current = refreshTokenService.issueRefreshToken(42L);
        when(memberMapper.findAuthState(42L))
                .thenReturn(memberAuthState("UNKNOWN", false));

        assertThrows(
                IllegalStateException.class,
                () -> refreshTokenService.rotateRefreshToken(
                        current.getValue()
                )
        );

        RefreshTokenSession stored = refreshTokenStore
                .findBySessionId(current.getSessionId())
                .orElseThrow();
        assertTrue(refreshTokenProvider.matches(
                current.getValue(),
                stored.getTokenHash()
        ));
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

    @Test
    void revokeRefreshToken_validToken_deletesSession() {
        RefreshToken token = refreshTokenService.issueRefreshToken(42L);

        refreshTokenService.revokeRefreshToken(token.getValue());

        assertTrue(
                refreshTokenStore.findBySessionId(token.getSessionId()).isEmpty()
        );
    }

    @Test
    void revokeRefreshToken_malformedToken_doesNotThrow() {
        refreshTokenService.revokeRefreshToken("malformed");
    }

    private MemberAuthState memberAuthState(String status, boolean deleted) {
        MemberAuthState state = new MemberAuthState();
        state.setMemberStatus(status);
        state.setDeleted(deleted);
        return state;
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
