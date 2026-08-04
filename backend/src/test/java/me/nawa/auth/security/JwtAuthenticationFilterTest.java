package me.nawa.auth.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import me.nawa.auth.cookie.AuthCookieManager;
import me.nawa.auth.jwt.AccessToken;
import me.nawa.auth.jwt.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.crypto.SecretKey;
import javax.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtAuthenticationFilterTest {
    private static final String ISSUER = "nawa";
    private static final String SECRET = encodeSecret(
            "test-signing-key-that-is-at-least-32-bytes"
    );
    private static final String OTHER_SECRET = encodeSecret(
            "different-signing-key-that-is-at-least-32-bytes"
    );

    private JwtTokenProvider jwtTokenProvider;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        jwtTokenProvider = new JwtTokenProvider(SECRET, ISSUER, 900);
        AuthCookieManager authCookieManager = new AuthCookieManager(
                "access_token",
                "refresh_token",
                false,
                "Lax",
                ""
        );
        filter = new JwtAuthenticationFilter(
                authCookieManager,
                jwtTokenProvider
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilter_validAccessToken_authenticatesMember() throws Exception {
        AccessToken token = jwtTokenProvider.issueAccessToken(42L);

        executeFilter(new Cookie("access_token", token.getValue()));

        Authentication authentication = SecurityContextHolder.getContext()
                .getAuthentication();
        assertTrue(authentication.isAuthenticated());
        AuthenticatedMember principal =
                (AuthenticatedMember) authentication.getPrincipal();
        assertEquals(42L, principal.getMemberId());
        assertNull(authentication.getCredentials());
        assertTrue(authentication.getAuthorities().isEmpty());
    }

    @Test
    void doFilter_withoutAccessToken_continuesAnonymously() throws Exception {
        executeFilter(null);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void doFilter_malformedAccessToken_continuesAnonymously() throws Exception {
        executeFilter(new Cookie("access_token", "malformed"));

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void doFilter_invalidSignature_continuesAnonymously() throws Exception {
        JwtTokenProvider otherProvider = new JwtTokenProvider(
                OTHER_SECRET,
                ISSUER,
                900
        );
        AccessToken token = otherProvider.issueAccessToken(42L);

        executeFilter(new Cookie("access_token", token.getValue()));

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void doFilter_expiredAccessToken_continuesAnonymously() throws Exception {
        executeFilter(new Cookie("access_token", createExpiredToken()));

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    private void executeFilter(Cookie cookie) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (cookie != null) {
            request.setCookies(cookie);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean();

        filter.doFilter(
                request,
                response,
                (servletRequest, servletResponse) -> chainCalled.set(true)
        );

        assertTrue(chainCalled.get());
    }

    private String createExpiredToken() {
        Instant now = Instant.now();
        SecretKey signingKey = Keys.hmacShaKeyFor(
                Decoders.BASE64.decode(SECRET)
        );
        return Jwts.builder()
                .issuer(ISSUER)
                .subject("42")
                .issuedAt(Date.from(now.minusSeconds(901)))
                .expiration(Date.from(now.minusSeconds(1)))
                .id(UUID.randomUUID().toString())
                .claim("token_type", "access")
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    private static String encodeSecret(String secret) {
        return Base64.getEncoder().encodeToString(
                secret.getBytes(StandardCharsets.UTF_8)
        );
    }
}
