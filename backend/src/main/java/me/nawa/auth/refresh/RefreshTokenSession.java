package me.nawa.auth.refresh;

import lombok.Getter;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Getter
public final class RefreshTokenSession {
    private final UUID sessionId;
    private final long memberId;
    private final String tokenHash;
    private final Instant issuedAt;
    private final Instant expiresAt;

    public RefreshTokenSession(
            UUID sessionId,
            long memberId,
            String tokenHash,
            Instant issuedAt,
            Instant expiresAt) {
        this.sessionId = Objects.requireNonNull(sessionId, "Session ID is required");
        if (memberId <= 0) {
            throw new IllegalArgumentException("Member ID must be positive");
        }
        if (!StringUtils.hasText(tokenHash)) {
            throw new IllegalArgumentException("Refresh token hash must not be blank");
        }
        this.issuedAt = Objects.requireNonNull(issuedAt, "Issued time is required");
        this.expiresAt = Objects.requireNonNull(expiresAt, "Expiration time is required");
        if (!expiresAt.isAfter(issuedAt)) {
            throw new IllegalArgumentException(
                    "Refresh token expiration must be after issuance"
            );
        }

        this.memberId = memberId;
        this.tokenHash = tokenHash;
    }
}
