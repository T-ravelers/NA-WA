package me.nawa.auth.jwt;

import lombok.Getter;

import java.time.Instant;

@Getter
public final class AccessTokenClaims {
    private final long memberId;
    private final String tokenId;
    private final Instant issuedAt;
    private final Instant expiresAt;

    public AccessTokenClaims(
            long memberId,
            String tokenId,
            Instant issuedAt,
            Instant expiresAt) {
        this.memberId = memberId;
        this.tokenId = tokenId;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
    }
}
