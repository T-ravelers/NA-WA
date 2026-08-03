package me.nawa.auth.token;

import me.nawa.auth.jwt.AccessTokenClaims;
import me.nawa.auth.jwt.JwtTokenProvider;
import me.nawa.auth.refresh.RefreshToken;
import me.nawa.auth.refresh.RefreshTokenService;
import me.nawa.auth.refresh.RotatedRefreshToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class AuthTokenServiceTest {
    private JwtTokenProvider jwtTokenProvider;
    private FakeRefreshTokenService refreshTokenService;
    private AuthTokenService authTokenService;

    @BeforeEach
    void setUp() {
        String secret = Base64.getEncoder().encodeToString(
                "test-signing-key-that-is-at-least-32-bytes"
                        .getBytes(StandardCharsets.UTF_8)
        );
        jwtTokenProvider = new JwtTokenProvider(secret, "nawa", 900);
        refreshTokenService = new FakeRefreshTokenService();
        authTokenService = new AuthTokenServiceImpl(
                jwtTokenProvider,
                refreshTokenService
        );
    }

    @Test
    void issueTokens_validMember_issuesAccessAndRefreshTokens() {
        RefreshToken refreshToken = createRefreshToken("issued-refresh");
        refreshTokenService.issuedToken = refreshToken;

        AuthTokens tokens = authTokenService.issueTokens(42L);

        AccessTokenClaims claims = jwtTokenProvider.parseAccessToken(
                tokens.getAccessToken().getValue()
        );
        assertEquals(42L, claims.getMemberId());
        assertSame(refreshToken, tokens.getRefreshToken());
        assertEquals(42L, refreshTokenService.issuedMemberId);
    }

    @Test
    void refreshTokens_currentRefreshToken_usesRotatedSessionMember() {
        RefreshToken replacement = createRefreshToken("replacement-refresh");
        refreshTokenService.rotatedToken = new RotatedRefreshToken(
                replacement,
                84L
        );

        AuthTokens tokens = authTokenService.refreshTokens("current-refresh");

        AccessTokenClaims claims = jwtTokenProvider.parseAccessToken(
                tokens.getAccessToken().getValue()
        );
        assertEquals(84L, claims.getMemberId());
        assertSame(replacement, tokens.getRefreshToken());
        assertEquals("current-refresh", refreshTokenService.rotatedValue);
    }

    @Test
    void revokeRefreshToken_token_delegatesToRefreshService() {
        authTokenService.revokeRefreshToken("refresh-value");

        assertEquals("refresh-value", refreshTokenService.revokedValue);
    }

    private RefreshToken createRefreshToken(String value) {
        Instant issuedAt = Instant.now();
        return new RefreshToken(
                value,
                UUID.randomUUID(),
                issuedAt,
                issuedAt.plusSeconds(1_209_600)
        );
    }

    private static final class FakeRefreshTokenService
            implements RefreshTokenService {
        private long issuedMemberId;
        private RefreshToken issuedToken;
        private String rotatedValue;
        private RotatedRefreshToken rotatedToken;
        private String revokedValue;

        @Override
        public RefreshToken issueRefreshToken(long memberId) {
            issuedMemberId = memberId;
            return issuedToken;
        }

        @Override
        public RotatedRefreshToken rotateRefreshToken(String currentToken) {
            rotatedValue = currentToken;
            return rotatedToken;
        }

        @Override
        public void revokeRefreshToken(String token) {
            revokedValue = token;
        }
    }
}
