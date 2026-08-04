package me.nawa.auth.cookie;

import me.nawa.auth.jwt.AccessToken;
import me.nawa.auth.refresh.RefreshToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.servlet.http.Cookie;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

@Component
public class AuthCookieManager {
    private static final String ACCESS_TOKEN_PATH = "/";
    private static final String REFRESH_TOKEN_PATH = "/api/v1/auth";

    private final String accessTokenName;
    private final String refreshTokenName;
    private final boolean secure;
    private final String sameSite;
    private final String domain;
    private final Clock clock;

    @Autowired
    public AuthCookieManager(
            @Value("${auth.cookie.access-token-name}") String accessTokenName,
            @Value("${auth.cookie.refresh-token-name}") String refreshTokenName,
            @Value("${auth.cookie.secure}") boolean secure,
            @Value("${auth.cookie.same-site}") String sameSite,
            @Value("${auth.cookie.domain:}") String domain) {
        this(
                accessTokenName,
                refreshTokenName,
                secure,
                sameSite,
                domain,
                Clock.systemUTC()
        );
    }

    AuthCookieManager(
            String accessTokenName,
            String refreshTokenName,
            boolean secure,
            String sameSite,
            String domain,
            Clock clock) {
        if (!StringUtils.hasText(accessTokenName)
                || !StringUtils.hasText(refreshTokenName)) {
            throw new IllegalArgumentException("Auth cookie names must not be blank");
        }
        if (accessTokenName.equals(refreshTokenName)) {
            throw new IllegalArgumentException("Auth cookie names must be different");
        }

        this.accessTokenName = accessTokenName;
        this.refreshTokenName = refreshTokenName;
        this.secure = secure;
        this.sameSite = normalizeSameSite(sameSite);
        if ("None".equals(this.sameSite) && !secure) {
            throw new IllegalArgumentException(
                    "SameSite=None requires a Secure cookie"
            );
        }
        this.domain = StringUtils.hasText(domain) ? domain.trim() : null;
        this.clock = Objects.requireNonNull(clock, "Clock is required");
    }

    public ResponseCookie createAccessTokenCookie(AccessToken token) {
        Objects.requireNonNull(token, "Access token is required");
        return createCookie(
                accessTokenName,
                token.getValue(),
                ACCESS_TOKEN_PATH,
                remainingMaxAge(token.getExpiresAt())
        );
    }

    public ResponseCookie createRefreshTokenCookie(RefreshToken token) {
        Objects.requireNonNull(token, "Refresh token is required");
        return createCookie(
                refreshTokenName,
                token.getValue(),
                REFRESH_TOKEN_PATH,
                remainingMaxAge(token.getExpiresAt())
        );
    }

    public ResponseCookie deleteAccessTokenCookie() {
        return createCookie(accessTokenName, "", ACCESS_TOKEN_PATH, Duration.ZERO);
    }

    public ResponseCookie deleteRefreshTokenCookie() {
        return createCookie(
                refreshTokenName,
                "",
                REFRESH_TOKEN_PATH,
                Duration.ZERO
        );
    }

    public Optional<String> findRefreshToken(Cookie[] cookies) {
        return findCookieValue(cookies, refreshTokenName);
    }

    public Optional<String> findAccessToken(Cookie[] cookies) {
        return findCookieValue(cookies, accessTokenName);
    }

    private Optional<String> findCookieValue(
            Cookie[] cookies,
            String cookieName) {
        if (cookies == null) {
            return Optional.empty();
        }

        for (Cookie cookie : cookies) {
            if (cookieName.equals(cookie.getName())
                    && StringUtils.hasText(cookie.getValue())) {
                return Optional.of(cookie.getValue());
            }
        }
        return Optional.empty();
    }

    private ResponseCookie createCookie(
            String name,
            String value,
            String path,
            Duration maxAge) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie
                .from(name, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path(path)
                .maxAge(maxAge);

        if (domain != null) {
            builder.domain(domain);
        }
        return builder.build();
    }

    private Duration remainingMaxAge(Instant expiresAt) {
        Duration maxAge = Duration.between(
                clock.instant(),
                Objects.requireNonNull(expiresAt, "Expiration time is required")
        );
        if (maxAge.isZero() || maxAge.isNegative()) {
            throw new IllegalArgumentException("Cannot create an expired auth cookie");
        }
        return maxAge;
    }

    private String normalizeSameSite(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("SameSite must not be blank");
        }

        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "strict" -> "Strict";
            case "lax" -> "Lax";
            case "none" -> "None";
            default -> throw new IllegalArgumentException(
                    "SameSite must be Strict, Lax, or None"
            );
        };
    }
}
