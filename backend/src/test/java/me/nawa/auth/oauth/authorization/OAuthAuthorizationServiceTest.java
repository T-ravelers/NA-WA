package me.nawa.auth.oauth.authorization;

import me.nawa.auth.exception.AuthErrorCode;
import me.nawa.auth.oauth.OAuthProvider;
import me.nawa.auth.oauth.state.OAuthAuthorizationRequestState;
import me.nawa.auth.oauth.state.OAuthStateService;
import me.nawa.auth.oauth.state.OAuthStateSession;
import me.nawa.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OAuthAuthorizationServiceTest {
    private FakeOAuthStateService stateService;
    private OAuthAuthorizationService service;

    @BeforeEach
    void setUp() {
        stateService = new FakeOAuthStateService();
        service = new OAuthAuthorizationServiceImpl(
                stateService,
                "google-client-id",
                "http://localhost:8080/api/v1/auth/oauth2/callback/google",
                "openid,profile,email",
                "line-client-id",
                "http://localhost:8080/api/v1/auth/oauth2/callback/line",
                "openid,profile"
        );
    }

    @Test
    void createAuthorizationUri_google_usesOfficialOidcParameters() {
        URI uri = service.createAuthorizationUri("google", "/journeys");

        assertEquals(
                "https://accounts.google.com/o/oauth2/v2/auth",
                uri.getScheme() + "://" + uri.getAuthority() + uri.getPath()
        );
        assertEquals(
                Map.of(
                        "response_type", "code",
                        "client_id", "google-client-id",
                        "redirect_uri",
                        "http://localhost:8080/api/v1/auth/oauth2/callback/google",
                        "scope", "openid profile email",
                        "state", "state-value",
                        "nonce", "nonce-value",
                        "code_challenge", "challenge-value",
                        "code_challenge_method", "S256"
                ),
                queryParameters(uri)
        );
        assertEquals(OAuthProvider.GOOGLE, stateService.issuedProvider);
        assertEquals("/journeys", stateService.issuedReturnPath);
    }

    @Test
    void createAuthorizationUri_line_usesV21AndConfiguredScopes() {
        URI uri = service.createAuthorizationUri("line", null);

        assertEquals(
                "https://access.line.me/oauth2/v2.1/authorize",
                uri.getScheme() + "://" + uri.getAuthority() + uri.getPath()
        );
        Map<String, String> parameters = queryParameters(uri);
        assertEquals("line-client-id", parameters.get("client_id"));
        assertEquals("openid profile", parameters.get("scope"));
        assertEquals("nonce-value", parameters.get("nonce"));
        assertEquals("S256", parameters.get("code_challenge_method"));
        assertEquals(OAuthProvider.LINE, stateService.issuedProvider);
        assertNull(stateService.issuedReturnPath);
    }

    @Test
    void createAuthorizationUri_unsupportedProvider_rejectsBeforeStateIssue() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.createAuthorizationUri("wechat", "/")
        );

        assertEquals(
                AuthErrorCode.UNSUPPORTED_OAUTH_PROVIDER,
                exception.getErrorCode()
        );
        assertFalse(stateService.issueCalled);
    }

    @Test
    void createAuthorizationUri_disallowedReturnPath_returnsStableAuthError() {
        stateService.rejectReturnPath = true;

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.createAuthorizationUri(
                        "google",
                        "https://evil.example"
                )
        );

        assertEquals(
                AuthErrorCode.INVALID_OAUTH_RETURN_PATH,
                exception.getErrorCode()
        );
    }

    @Test
    void createAuthorizationUri_unconfiguredProvider_doesNotCreateState() {
        service = new OAuthAuthorizationServiceImpl(
                stateService,
                "",
                "http://localhost:8080/api/v1/auth/oauth2/callback/google",
                "openid,profile,email",
                "line-client-id",
                "http://localhost:8080/api/v1/auth/oauth2/callback/line",
                "openid,profile"
        );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.createAuthorizationUri("google", "/")
        );

        assertEquals(
                AuthErrorCode.OAUTH_PROVIDER_NOT_CONFIGURED,
                exception.getErrorCode()
        );
        assertFalse(stateService.issueCalled);
    }

    private Map<String, String> queryParameters(URI uri) {
        LinkedHashMap<String, String> parameters = new LinkedHashMap<>();
        for (String pair : uri.getRawQuery().split("&")) {
            String[] parts = pair.split("=", 2);
            parameters.put(decode(parts[0]), decode(parts[1]));
        }
        return parameters;
    }

    private String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static final class FakeOAuthStateService
            implements OAuthStateService {
        private boolean issueCalled;
        private boolean rejectReturnPath;
        private OAuthProvider issuedProvider;
        private String issuedReturnPath;

        @Override
        public OAuthAuthorizationRequestState issue(
                OAuthProvider provider,
                String returnPath) {
            issueCalled = true;
            issuedProvider = provider;
            issuedReturnPath = returnPath;
            if (rejectReturnPath) {
                throw new IllegalArgumentException(
                        "OAuth return path is not allowed"
                );
            }
            return new OAuthAuthorizationRequestState(
                    "state-value",
                    "nonce-value",
                    "challenge-value",
                    "S256",
                    Instant.parse("2026-08-03T00:10:00Z")
            );
        }

        @Override
        public Optional<OAuthStateSession> consume(String state) {
            throw new UnsupportedOperationException();
        }
    }
}
