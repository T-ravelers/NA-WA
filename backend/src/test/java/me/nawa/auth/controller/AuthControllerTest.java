package me.nawa.auth.controller;

import me.nawa.auth.cookie.AuthCookieManager;
import me.nawa.auth.jwt.AccessToken;
import me.nawa.auth.refresh.RefreshToken;
import me.nawa.auth.token.AuthTokenService;
import me.nawa.auth.token.AuthTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import javax.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class AuthControllerTest {
    private FakeAuthTokenService authTokenService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        authTokenService = new FakeAuthTokenService();
        AuthCookieManager authCookieManager = new AuthCookieManager(
                "access_token",
                "refresh_token",
                false,
                "Lax",
                ""
        );
        AuthController controller = new AuthController(
                authTokenService,
                authCookieManager
        );
        AuthExceptionHandler exceptionHandler = new AuthExceptionHandler(
                authCookieManager
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(exceptionHandler)
                .build();
    }

    @Test
    void csrf_securityToken_returnsTokenAndHeaderName() throws Exception {
        CsrfToken csrfToken = new DefaultCsrfToken(
                "X-XSRF-TOKEN",
                "_csrf",
                "csrf-value"
        );

        MockHttpServletResponse response = mockMvc.perform(
                        get("/api/auth/csrf")
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
                        post("/api/auth/refresh")
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
        assertTrue(setCookies.get(1).contains("Path=/api/auth"));
    }

    @Test
    void refresh_missingCookie_returnsUnauthorizedAndDeletesCookies()
            throws Exception {
        MockHttpServletResponse response = mockMvc.perform(
                        post("/api/auth/refresh")
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
                        post("/api/auth/logout")
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
                        post("/api/auth/logout")
                )
                .andReturn()
                .getResponse();

        assertEquals(200, response.getStatus());
        assertNull(authTokenService.revokedValue);
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
        assertTrue(setCookies.get(1).contains("refresh_token="));
        assertTrue(setCookies.get(1).contains("Max-Age=0"));
    }

    private static final class FakeAuthTokenService
            implements AuthTokenService {
        private String refreshedValue;
        private AuthTokens refreshedTokens;
        private String revokedValue;

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
        }
    }
}
