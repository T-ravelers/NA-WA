package me.nawa.auth.controller;

import me.nawa.auth.cookie.AuthCookieManager;
import me.nawa.auth.jwt.AccessToken;
import me.nawa.auth.oauth.authorization.OAuthAuthorizationService;
import me.nawa.auth.oauth.callback.OAuthCallbackResult;
import me.nawa.auth.oauth.callback.OAuthCallbackService;
import me.nawa.auth.refresh.RefreshToken;
import me.nawa.auth.token.AuthTokenService;
import me.nawa.auth.token.AuthTokens;
import me.nawa.common.exception.BusinessException;
import me.nawa.common.exception.ErrorCode;
import me.nawa.common.response.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import javax.servlet.http.Cookie;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class AuthControllerTest {
    private FakeAuthTokenService authTokenService;
    private FakeOAuthAuthorizationService oauthAuthorizationService;
    private FakeOAuthCallbackService oauthCallbackService;
    private AuthController controller;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        authTokenService = new FakeAuthTokenService();
        oauthAuthorizationService = new FakeOAuthAuthorizationService();
        oauthCallbackService = new FakeOAuthCallbackService();
        AuthCookieManager authCookieManager = new AuthCookieManager(
                "access_token",
                "refresh_token",
                false,
                "Lax",
                ""
        );
        controller = new AuthController(
                authTokenService,
                authCookieManager,
                oauthAuthorizationService,
                oauthCallbackService
        );
        AuthExceptionHandler exceptionHandler = new AuthExceptionHandler(
                authCookieManager
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(exceptionHandler)
                .build();
    }

    @Test
    void authorize_validProvider_redirectsToProviderAuthorizationUri()
            throws Exception {
        oauthAuthorizationService.authorizationUri = URI.create(
                "https://accounts.google.com/o/oauth2/v2/auth?state=value"
        );

        MockHttpServletResponse response = mockMvc.perform(
                        get("/api/v1/auth/oauth2/authorization/google")
                                .param("returnPath", "/journeys")
                )
                .andReturn()
                .getResponse();

        assertEquals(302, response.getStatus());
        assertEquals(
                oauthAuthorizationService.authorizationUri.toString(),
                response.getHeader(HttpHeaders.LOCATION)
        );
        assertEquals("google", oauthAuthorizationService.provider);
        assertEquals("/journeys", oauthAuthorizationService.returnPath);
    }

    @Test
    void callback_success_setsCookiesAndRedirectsWithoutTokensInUrl()
            throws Exception {
        oauthCallbackService.result = new OAuthCallbackResult(
                createAuthTokens(),
                URI.create(
                        "http://localhost:5173/auth/callback"
                                + "?returnPath=/journeys"
                )
        );

        MockHttpServletResponse response = mockMvc.perform(
                        get("/api/v1/auth/oauth2/callback/google")
                                .param("state", "state-value")
                                .param("code", "authorization-code")
                )
                .andReturn()
                .getResponse();

        assertEquals(302, response.getStatus());
        assertEquals(
                oauthCallbackService.result.getRedirectUri().toString(),
                response.getHeader(HttpHeaders.LOCATION)
        );
        assertEquals("google", oauthCallbackService.provider);
        assertEquals("state-value", oauthCallbackService.state);
        assertEquals("authorization-code", oauthCallbackService.code);
        assertNull(oauthCallbackService.error);
        List<String> setCookies = response.getHeaders(HttpHeaders.SET_COOKIE);
        assertEquals(2, setCookies.size());
        assertTrue(setCookies.get(0).contains("access_token=access-value"));
        assertTrue(setCookies.get(1).contains("refresh_token=refresh-value"));
        assertTrue(response.getContentAsString().isEmpty());
    }

    @Test
    void callback_failure_deletesCookiesAndRedirectsWithStableCodeOnly()
            throws Exception {
        oauthCallbackService.failure = new BusinessException(
                me.nawa.auth.exception.AuthErrorCode
                        .OAUTH_AUTHORIZATION_FAILED
        );
        oauthCallbackService.failureUri = URI.create(
                "http://localhost:5173/auth/callback?error=AUTH-015"
        );

        MockHttpServletResponse response = mockMvc.perform(
                        get("/api/v1/auth/oauth2/callback/line")
                                .param("state", "state-value")
                                .param("error", "access_denied")
                                .param(
                                        "error_description",
                                        "provider sensitive description"
                                )
                )
                .andReturn()
                .getResponse();

        assertEquals(302, response.getStatus());
        assertEquals(
                oauthCallbackService.failureUri.toString(),
                response.getHeader(HttpHeaders.LOCATION)
        );
        assertEquals("access_denied", oauthCallbackService.error);
        assertFalse(response.getHeader(HttpHeaders.LOCATION).contains(
                "description"
        ));
        assertDeletedCookies(response.getHeaders(HttpHeaders.SET_COOKIE));
        assertTrue(response.getContentAsString().isEmpty());
    }

    @Test
    void callback_unexpectedFailure_redirectsWithCommonCodeAndDeletesCookies()
            throws Exception {
        oauthCallbackService.failure = new IllegalStateException(
                "sensitive internal detail"
        );
        oauthCallbackService.failureUri = URI.create(
                "http://localhost:5173/auth/callback?error=COMMON-999"
        );

        MockHttpServletResponse response = mockMvc.perform(
                        get("/api/v1/auth/oauth2/callback/google")
                                .param("state", "state-value")
                                .param("code", "authorization-code")
                )
                .andReturn()
                .getResponse();

        assertEquals(302, response.getStatus());
        assertEquals(
                oauthCallbackService.failureUri.toString(),
                response.getHeader(HttpHeaders.LOCATION)
        );
        assertDeletedCookies(response.getHeaders(HttpHeaders.SET_COOKIE));
        assertTrue(response.getContentAsString().isEmpty());
    }

    @Test
    void csrf_securityToken_returnsTokenAndHeaderName() throws Exception {
        CsrfToken csrfToken = new DefaultCsrfToken(
                "X-XSRF-TOKEN",
                "_csrf",
                "csrf-value"
        );

        MockHttpServletResponse response = mockMvc.perform(
                        get("/api/v1/auth/csrf")
                                .requestAttr(
                                        CsrfToken.class.getName(),
                                        csrfToken
                                )
                )
                .andReturn()
                .getResponse();

        assertEquals(200, response.getStatus());
        assertEquals(
                "{\"success\":true,\"data\":{"
                        + "\"token\":\"csrf-value\","
                        + "\"headerName\":\"X-XSRF-TOKEN\"}}",
                response.getContentAsString()
        );
    }

    @Test
    void refresh_validCookie_rotatesTokensAndSetsBothCookies() throws Exception {
        authTokenService.refreshedTokens = createAuthTokens();

        MockHttpServletResponse response = mockMvc.perform(
                        post("/api/v1/auth/refresh")
                                .cookie(new Cookie(
                                        "refresh_token",
                                        "current-refresh"
                                ))
                )
                .andReturn()
                .getResponse();

        assertEquals(200, response.getStatus());
        assertEquals("{\"success\":true}", response.getContentAsString());
        assertEquals("current-refresh", authTokenService.refreshedValue);
        List<String> setCookies = response.getHeaders(HttpHeaders.SET_COOKIE);
        assertEquals(2, setCookies.size());
        assertTrue(setCookies.get(0).contains("access_token=access-value"));
        assertTrue(setCookies.get(0).contains("HttpOnly"));
        assertTrue(setCookies.get(0).contains("Path=/"));
        assertTrue(setCookies.get(1).contains("refresh_token=refresh-value"));
        assertTrue(setCookies.get(1).contains("HttpOnly"));
        assertTrue(setCookies.get(1).contains("Path=/api/v1/auth"));
    }

    @Test
    void refresh_missingCookie_returnsUnauthorizedAndDeletesCookies()
            throws Exception {
        MockHttpServletResponse response = mockMvc.perform(
                        post("/api/v1/auth/refresh")
                )
                .andReturn()
                .getResponse();

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("AUTH-001"));
        assertDeletedCookies(response.getHeaders(HttpHeaders.SET_COOKIE));
    }

    @Test
    void logout_withCookie_revokesSessionAndDeletesCookies() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(
                        post("/api/v1/auth/logout")
                                .cookie(new Cookie(
                                        "refresh_token",
                                        "refresh-value"
                                ))
                )
                .andReturn()
                .getResponse();

        assertEquals(200, response.getStatus());
        assertEquals("{\"success\":true}", response.getContentAsString());
        assertEquals("refresh-value", authTokenService.revokedValue);
        assertDeletedCookies(response.getHeaders(HttpHeaders.SET_COOKIE));
    }

    @Test
    void logout_withoutCookie_isIdempotent() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(
                        post("/api/v1/auth/logout")
                )
                .andReturn()
                .getResponse();

        assertEquals(200, response.getStatus());
        assertNull(authTokenService.revokedValue);
        assertDeletedCookies(response.getHeaders(HttpHeaders.SET_COOKIE));
    }

    @Test
    void logout_revokeFailure_deletesCookiesAndReturnsSuccess()
            throws Exception {
        authTokenService.revokeFailure = new IllegalStateException(
                "redis unavailable"
        );

        MockHttpServletResponse response = mockMvc.perform(
                        post("/api/v1/auth/logout")
                                .cookie(new Cookie(
                                        "refresh_token",
                                        "refresh-value"
                                ))
                )
                .andReturn()
                .getResponse();

        assertEquals(200, response.getStatus());
        assertEquals("{\"success\":true}", response.getContentAsString());
        assertEquals("refresh-value", authTokenService.revokedValue);
        assertDeletedCookies(response.getHeaders(HttpHeaders.SET_COOKIE));
    }

    private AuthTokens createAuthTokens() {
        Instant now = Instant.now();
        return new AuthTokens(
                new AccessToken(
                        "access-value",
                        now.plusSeconds(900)
                ),
                new RefreshToken(
                        "refresh-value",
                        UUID.randomUUID(),
                        now,
                        now.plusSeconds(1_209_600)
                )
        );
    }

    private void assertDeletedCookies(List<String> setCookies) {
        assertEquals(2, setCookies.size());
        assertTrue(setCookies.get(0).contains("access_token="));
        assertTrue(setCookies.get(0).contains("Max-Age=0"));
        assertTrue(setCookies.get(0).contains("Path=/;"));
        assertTrue(setCookies.get(1).contains("refresh_token="));
        assertTrue(setCookies.get(1).contains("Max-Age=0"));
        assertTrue(setCookies.get(1).contains("Path=/api/v1/auth;"));
    }

    private static final class FakeAuthTokenService
            implements AuthTokenService {
        private String refreshedValue;
        private AuthTokens refreshedTokens;
        private String revokedValue;
        private RuntimeException revokeFailure;

        @Override
        public AuthTokens issueTokens(long memberId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AuthTokens refreshTokens(String currentRefreshToken) {
            refreshedValue = currentRefreshToken;
            return refreshedTokens;
        }

        @Override
        public void revokeRefreshToken(String refreshToken) {
            revokedValue = refreshToken;
            if (revokeFailure != null) {
                throw revokeFailure;
            }
        }
    }

    private static final class FakeOAuthAuthorizationService
            implements OAuthAuthorizationService {
        private URI authorizationUri;
        private String provider;
        private String returnPath;

        @Override
        public URI createAuthorizationUri(
                String provider,
                String returnPath) {
            this.provider = provider;
            this.returnPath = returnPath;
            return authorizationUri;
        }
    }

    private static final class FakeOAuthCallbackService
            implements OAuthCallbackService {
        private OAuthCallbackResult result;
        private RuntimeException failure;
        private URI failureUri;
        private String provider;
        private String state;
        private String code;
        private String error;

        @Override
        public OAuthCallbackResult handle(
                String provider,
                String state,
                String authorizationCode,
                String authorizationError) {
            this.provider = provider;
            this.state = state;
            this.code = authorizationCode;
            this.error = authorizationError;
            if (failure != null) {
                throw failure;
            }
            return result;
        }

        @Override
        public URI createFailureRedirectUri(ErrorCode errorCode) {
            return failureUri;
        }
    }
}
