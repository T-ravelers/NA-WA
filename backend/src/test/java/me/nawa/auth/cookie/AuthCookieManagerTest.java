package me.nawa.auth.cookie;

import me.nawa.auth.jwt.AccessToken;
import me.nawa.auth.refresh.RefreshToken;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

import javax.servlet.http.Cookie;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthCookieManagerTest {
    private static final Instant CURRENT_TIME =
            Instant.parse("2026-08-03T00:00:00Z");

    @Test
    void createTokenCookies_validTokens_setsSecurityAttributesAndPaths() {
        AuthCookieManager manager = createManager(true, "Lax", "");
        AccessToken accessToken = new AccessToken(
                "access-value",
                CURRENT_TIME.plusSeconds(900)
        );
        RefreshToken refreshToken = new RefreshToken(
                "refresh-value",
                UUID.randomUUID(),
                CURRENT_TIME,
                CURRENT_TIME.plusSeconds(1_209_600)
        );

        ResponseCookie accessCookie =
                manager.createAccessTokenCookie(accessToken);
        ResponseCookie refreshCookie =
                manager.createRefreshTokenCookie(refreshToken);

        assertEquals("access_token", accessCookie.getName());
        assertEquals("access-value", accessCookie.getValue());
        assertEquals("/", accessCookie.getPath());
        assertEquals(900, accessCookie.getMaxAge().getSeconds());
        assertTrue(accessCookie.isHttpOnly());
        assertTrue(accessCookie.isSecure());
        assertEquals("Lax", accessCookie.getSameSite());
        assertNull(accessCookie.getDomain());

        assertEquals("refresh_token", refreshCookie.getName());
        assertEquals("refresh-value", refreshCookie.getValue());
        assertEquals("/api/v1/auth", refreshCookie.getPath());
        assertEquals(1_209_600, refreshCookie.getMaxAge().getSeconds());
        assertTrue(refreshCookie.isHttpOnly());
        assertTrue(refreshCookie.isSecure());
        assertEquals("Lax", refreshCookie.getSameSite());
    }

    @Test
    void deleteTokenCookies_setsZeroMaxAgeAndMatchingPaths() {
        AuthCookieManager manager = createManager(true, "Strict", "example.com");

        ResponseCookie accessCookie = manager.deleteAccessTokenCookie();
        ResponseCookie refreshCookie = manager.deleteRefreshTokenCookie();

        assertEquals(0, accessCookie.getMaxAge().getSeconds());
        assertEquals("/", accessCookie.getPath());
        assertEquals("example.com", accessCookie.getDomain());
        assertEquals(0, refreshCookie.getMaxAge().getSeconds());
        assertEquals("/api/v1/auth", refreshCookie.getPath());
        assertEquals("example.com", refreshCookie.getDomain());
    }

    @Test
    void createOAuthStateCookie_strictConfiguration_staysLaxOnAuthPath() {
        AuthCookieManager manager = createManager(true, "Strict", "");

        ResponseCookie stateCookie = manager.createOAuthStateCookie(
                "browser-binding-value",
                CURRENT_TIME.plusSeconds(600)
        );

        assertEquals("oauth_state", stateCookie.getName());
        assertEquals("browser-binding-value", stateCookie.getValue());
        assertEquals("/api/v1/auth", stateCookie.getPath());
        assertEquals(600, stateCookie.getMaxAge().getSeconds());
        assertTrue(stateCookie.isHttpOnly());
        assertTrue(stateCookie.isSecure());
        assertEquals("Lax", stateCookie.getSameSite());
    }

    @Test
    void createOAuthStateCookie_expiredOrBlankBinding_throwsException() {
        AuthCookieManager manager = createManager(true, "Lax", "");

        assertThrows(
                IllegalArgumentException.class,
                () -> manager.createOAuthStateCookie(
                        "browser-binding-value",
                        CURRENT_TIME
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> manager.createOAuthStateCookie(
                        " ",
                        CURRENT_TIME.plusSeconds(600)
                )
        );
    }

    @Test
    void deleteOAuthStateCookie_setsZeroMaxAgeOnAuthPath() {
        AuthCookieManager manager = createManager(true, "Strict", "example.com");

        ResponseCookie stateCookie = manager.deleteOAuthStateCookie();

        assertEquals(0, stateCookie.getMaxAge().getSeconds());
        assertEquals("/api/v1/auth", stateCookie.getPath());
        assertEquals("example.com", stateCookie.getDomain());
        assertEquals("Lax", stateCookie.getSameSite());
    }

    @Test
    void findOAuthStateBinding_matchingCookie_returnsValue() {
        AuthCookieManager manager = createManager(false, "Lax", "");
        Cookie[] cookies = {
                new Cookie("access_token", "ignored"),
                new Cookie("oauth_state", "browser-binding-value")
        };

        assertEquals(
                "browser-binding-value",
                manager.findOAuthStateBinding(cookies).orElseThrow()
        );
        assertFalse(manager.findOAuthStateBinding(null).isPresent());
    }

    @Test
    void constructor_duplicateCookieNames_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AuthCookieManager(
                        "access_token",
                        "refresh_token",
                        "refresh_token",
                        false,
                        "Lax",
                        "",
                        Clock.fixed(CURRENT_TIME, ZoneOffset.UTC)
                )
        );
    }

    @Test
    void findRefreshToken_matchingCookie_returnsValue() {
        AuthCookieManager manager = createManager(false, "Lax", "");
        Cookie[] cookies = {
                new Cookie("other", "ignored"),
                new Cookie("refresh_token", "refresh-value")
        };

        assertEquals(
                "refresh-value",
                manager.findRefreshToken(cookies).orElseThrow()
        );
        assertFalse(manager.findRefreshToken(null).isPresent());
    }

    @Test
    void findAccessToken_matchingCookie_returnsValue() {
        AuthCookieManager manager = createManager(false, "Lax", "");
        Cookie[] cookies = {
                new Cookie("refresh_token", "ignored"),
                new Cookie("access_token", "access-value")
        };

        assertEquals(
                "access-value",
                manager.findAccessToken(cookies).orElseThrow()
        );
        assertFalse(
                manager.findAccessToken(
                        new Cookie[]{new Cookie("access_token", "")}
                ).isPresent()
        );
    }

    @Test
    void constructor_sameSiteNoneWithoutSecure_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> createManager(false, "None", "")
        );
    }

    private AuthCookieManager createManager(
            boolean secure,
            String sameSite,
            String domain) {
        return new AuthCookieManager(
                "access_token",
                "refresh_token",
                "oauth_state",
                secure,
                sameSite,
                domain,
                Clock.fixed(CURRENT_TIME, ZoneOffset.UTC)
        );
    }
}
