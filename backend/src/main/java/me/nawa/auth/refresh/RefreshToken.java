package me.nawa.auth.refresh;

import lombok.Getter;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Getter
public final class RefreshToken {
    private final String value;
    private final UUID sessionId;
    private final Instant issuedAt;
    private final Instant expiresAt;

    public RefreshToken(
            String value,
            UUID sessionId,
            Instant issuedAt,
            Instant expiresAt) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("Refresh token must not be blank");
        }
        this.value = value;
        this.sessionId = Objects.requireNonNull(sessionId, "Session ID is required");
        this.issuedAt = Objects.requireNonNull(issuedAt, "Issued time is required");
        this.expiresAt = Objects.requireNonNull(expiresAt, "Expiration time is required");
        if (!expiresAt.isAfter(issuedAt)) {
            throw new IllegalArgumentException(
                    "Refresh token expiration must be after issuance"
            );
        }
    }
}
