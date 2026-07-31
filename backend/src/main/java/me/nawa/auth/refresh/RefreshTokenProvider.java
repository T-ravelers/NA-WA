package me.nawa.auth.refresh;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

@Component
public class RefreshTokenProvider {
    private static final int SECRET_BYTE_LENGTH = 32;
    private static final String TOKEN_SEPARATOR = ".";

    private final Duration refreshTokenTtl;
    private final Clock clock;
    private final SecureRandom secureRandom;

    @Autowired
    public RefreshTokenProvider(
            @Value("${auth.refresh-token-ttl-seconds}")
            long refreshTokenTtlSeconds) {
        this(
                refreshTokenTtlSeconds,
                Clock.systemUTC(),
                new SecureRandom()
        );
    }

    RefreshTokenProvider(
            long refreshTokenTtlSeconds,
            Clock clock,
            SecureRandom secureRandom) {
        if (refreshTokenTtlSeconds <= 0) {
            throw new IllegalArgumentException("Refresh token TTL must be positive");
        }

        this.refreshTokenTtl = Duration.ofSeconds(refreshTokenTtlSeconds);
        this.clock = Objects.requireNonNull(clock, "Clock is required");
        this.secureRandom = Objects.requireNonNull(
                secureRandom,
                "SecureRandom is required"
        );
    }

    public RefreshToken issueRefreshToken() {
        Instant issuedAt = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = issuedAt.plus(refreshTokenTtl);
        UUID sessionId = UUID.randomUUID();

        byte[] secret = new byte[SECRET_BYTE_LENGTH];
        secureRandom.nextBytes(secret);
        String encodedSecret = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(secret);
        String value = sessionId + TOKEN_SEPARATOR + encodedSecret;

        return new RefreshToken(value, sessionId, issuedAt, expiresAt);
    }

    public UUID extractSessionId(String token) {
        if (!StringUtils.hasText(token)) {
            throw new IllegalArgumentException("Refresh token must not be blank");
        }

        int separatorIndex = token.indexOf(TOKEN_SEPARATOR);
        if (separatorIndex <= 0
                || separatorIndex != token.lastIndexOf(TOKEN_SEPARATOR)
                || separatorIndex == token.length() - 1) {
            throw new IllegalArgumentException("Refresh token format is invalid");
        }

        try {
            UUID sessionId = UUID.fromString(token.substring(0, separatorIndex));
            byte[] secret = Base64.getUrlDecoder()
                    .decode(token.substring(separatorIndex + 1));

            if (secret.length != SECRET_BYTE_LENGTH) {
                throw new IllegalArgumentException(
                        "Refresh token secret length is invalid"
                );
            }
            return sessionId;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Refresh token format is invalid",
                    exception
            );
        }
    }

    public String hashToken(String token) {
        extractSessionId(token);
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(digest(token));
    }

    public boolean matches(String token, String expectedHash) {
        if (!StringUtils.hasText(token) || !StringUtils.hasText(expectedHash)) {
            return false;
        }

        try {
            extractSessionId(token);
            byte[] expected = Base64.getUrlDecoder().decode(expectedHash);
            return MessageDigest.isEqual(digest(token), expected);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private byte[] digest(String token) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            return messageDigest.digest(token.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
