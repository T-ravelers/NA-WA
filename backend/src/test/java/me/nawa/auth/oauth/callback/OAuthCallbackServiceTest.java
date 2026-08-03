package me.nawa.auth.oauth.callback;

import me.nawa.auth.exception.AuthErrorCode;
import me.nawa.auth.jwt.AccessToken;
import me.nawa.auth.oauth.OAuthProvider;
import me.nawa.auth.oauth.account.OAuthMemberService;
import me.nawa.auth.oauth.identity.OAuthIdentityVerificationService;
import me.nawa.auth.oauth.identity.OAuthUserProfile;
import me.nawa.auth.oauth.state.OAuthAuthorizationRequestState;
import me.nawa.auth.oauth.state.OAuthStateService;
import me.nawa.auth.oauth.state.OAuthStateSession;
import me.nawa.auth.oauth.token.OAuthProviderTokenSet;
import me.nawa.auth.oauth.token.OAuthTokenExchangeService;
import me.nawa.auth.refresh.RefreshToken;
import me.nawa.auth.token.AuthTokenService;
import me.nawa.auth.token.AuthTokens;
import me.nawa.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OAuthCallbackServiceTest {
    private static final Instant NOW = Instant.parse(
            "2026-08-03T06:30:00Z"
    );

    private List<String> calls;
    private FakeStateService stateService;
    private FakeTokenExchangeService tokenExchangeService;
    private FakeIdentityVerificationService identityService;
    private FakeMemberService memberService;
    private FakeAuthTokenService authTokenService;
    private OAuthCallbackService service;

    @BeforeEach
    void setUp() {
        calls = new ArrayList<>();
        stateService = new FakeStateService(calls);
        tokenExchangeService = new FakeTokenExchangeService(calls);
        identityService = new FakeIdentityVerificationService(calls);
        memberService = new FakeMemberService(calls);
        authTokenService = new FakeAuthTokenService(calls);

        stateService.session = stateSession(OAuthProvider.GOOGLE);
        tokenExchangeService.tokenSet = tokenSet(OAuthProvider.GOOGLE);
        identityService.profile = profile(OAuthProvider.GOOGLE);
        memberService.memberId = 42L;
        authTokenService.tokens = authTokens();

        service = new OAuthCallbackServiceImpl(
                stateService,
                tokenExchangeService,
                identityService,
                memberService,
                authTokenService,
                "http://localhost:5173/auth/callback?source=oauth",
                "http://localhost:5173/auth/callback?source=oauth"
        );
    }

    @Test
    void handle_validCallback_runsPipelineAndReturnsSafeRedirect() {
        OAuthCallbackResult result = service.handle(
                "google",
                "state-value",
                "authorization-code",
                null
        );

        assertEquals(authTokenService.tokens, result.getTokens());
        assertEquals(
                "http://localhost:5173/auth/callback"
                        + "?source=oauth&returnPath=/journeys",
                result.getRedirectUri().toString()
        );
        assertFalse(result.getRedirectUri().toString().contains(
                "authorization-code"
        ));
        assertFalse(result.getRedirectUri().toString().contains(
                "access-value"
        ));
        assertEquals(
                List.of("state", "exchange", "identity", "member", "token"),
                calls
        );
        assertEquals("authorization-code", tokenExchangeService.code);
        assertEquals(42L, authTokenService.memberId);
    }

    @Test
    void handle_unknownOrConsumedState_rejectsBeforeOtherSteps() {
        stateService.session = null;

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.handle(
                        "google",
                        "reused-state",
                        "authorization-code",
                        null
                )
        );

        assertEquals(
                AuthErrorCode.INVALID_OAUTH_CALLBACK_STATE,
                exception.getErrorCode()
        );
        assertEquals(List.of("state"), calls);
    }

    @Test
    void handle_providerMismatch_consumesStateAndRejects() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.handle(
                        "line",
                        "state-value",
                        "authorization-code",
                        null
                )
        );

        assertEquals(
                AuthErrorCode.INVALID_OAUTH_CALLBACK_STATE,
                exception.getErrorCode()
        );
        assertEquals(List.of("state"), calls);
    }

    @Test
    void handle_providerAuthorizationError_consumesStateWithoutExchange() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.handle(
                        "google",
                        "state-value",
                        null,
                        "access_denied"
                )
        );

        assertEquals(
                AuthErrorCode.OAUTH_AUTHORIZATION_FAILED,
                exception.getErrorCode()
        );
        assertEquals(List.of("state"), calls);
    }

    @Test
    void handle_missingAuthorizationCode_consumesStateWithoutExchange() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.handle(
                        "google",
                        "state-value",
                        " ",
                        null
                )
        );

        assertEquals(
                AuthErrorCode.OAUTH_AUTHORIZATION_FAILED,
                exception.getErrorCode()
        );
        assertEquals(List.of("state"), calls);
    }

    @Test
    void handle_exchangedProviderMismatch_rejectsBeforeIdentityUse() {
        tokenExchangeService.tokenSet = tokenSet(OAuthProvider.LINE);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.handle(
                        "google",
                        "state-value",
                        "authorization-code",
                        null
                )
        );

        assertEquals(
                AuthErrorCode.INVALID_OAUTH_ID_TOKEN,
                exception.getErrorCode()
        );
        assertEquals(List.of("state", "exchange"), calls);
    }

    @Test
    void createFailureRedirectUri_containsOnlyStableErrorCode() {
        URI uri = service.createFailureRedirectUri(
                AuthErrorCode.OAUTH_AUTHORIZATION_FAILED
        );

        assertEquals(
                "http://localhost:5173/auth/callback"
                        + "?source=oauth&error=AUTH-015",
                uri.toString()
        );
    }

    private OAuthStateSession stateSession(OAuthProvider provider) {
        return new OAuthStateSession(
                "state-value",
                provider,
                "nonce-value",
                "code-verifier",
                "/journeys",
                NOW,
                NOW.plusSeconds(600)
        );
    }

    private OAuthProviderTokenSet tokenSet(OAuthProvider provider) {
        return new OAuthProviderTokenSet(
                provider,
                "provider-access-token",
                "provider-id-token",
                3600,
                "Bearer",
                "openid profile"
        );
    }

    private OAuthUserProfile profile(OAuthProvider provider) {
        return new OAuthUserProfile(
                provider,
                "provider-user-id",
                null,
                "Traveler",
                null
        );
    }

    private AuthTokens authTokens() {
        return new AuthTokens(
                new AccessToken("access-value", NOW.plusSeconds(900)),
                new RefreshToken(
                        "refresh-value",
                        UUID.randomUUID(),
                        NOW,
                        NOW.plusSeconds(1_209_600)
                )
        );
    }

    private static final class FakeStateService implements OAuthStateService {
        private final List<String> calls;
        private OAuthStateSession session;

        private FakeStateService(List<String> calls) {
            this.calls = calls;
        }

        @Override
        public OAuthAuthorizationRequestState issue(
                OAuthProvider provider,
                String returnPath) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<OAuthStateSession> consume(String state) {
            calls.add("state");
            return Optional.ofNullable(session);
        }
    }

    private static final class FakeTokenExchangeService
            implements OAuthTokenExchangeService {
        private final List<String> calls;
        private OAuthProviderTokenSet tokenSet;
        private String code;

        private FakeTokenExchangeService(List<String> calls) {
            this.calls = calls;
        }

        @Override
        public OAuthProviderTokenSet exchange(
                OAuthStateSession stateSession,
                String authorizationCode) {
            calls.add("exchange");
            code = authorizationCode;
            return tokenSet;
        }
    }

    private static final class FakeIdentityVerificationService
            implements OAuthIdentityVerificationService {
        private final List<String> calls;
        private OAuthUserProfile profile;

        private FakeIdentityVerificationService(List<String> calls) {
            this.calls = calls;
        }

        @Override
        public OAuthUserProfile verify(
                OAuthStateSession stateSession,
                OAuthProviderTokenSet tokenSet) {
            calls.add("identity");
            return profile;
        }
    }

    private static final class FakeMemberService
            implements OAuthMemberService {
        private final List<String> calls;
        private long memberId;

        private FakeMemberService(List<String> calls) {
            this.calls = calls;
        }

        @Override
        public long resolveMemberId(OAuthUserProfile profile) {
            calls.add("member");
            return memberId;
        }
    }

    private static final class FakeAuthTokenService
            implements AuthTokenService {
        private final List<String> calls;
        private AuthTokens tokens;
        private long memberId;

        private FakeAuthTokenService(List<String> calls) {
            this.calls = calls;
        }

        @Override
        public AuthTokens issueTokens(long memberId) {
            calls.add("token");
            this.memberId = memberId;
            return tokens;
        }

        @Override
        public AuthTokens refreshTokens(String currentRefreshToken) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void revokeRefreshToken(String refreshToken) {
            throw new UnsupportedOperationException();
        }
    }
}
