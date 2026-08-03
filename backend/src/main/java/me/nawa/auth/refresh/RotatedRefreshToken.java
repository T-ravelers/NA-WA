package me.nawa.auth.refresh;

import lombok.Getter;

@Getter
public final class RotatedRefreshToken {
    private final RefreshToken token;
    private final long memberId;

    public RotatedRefreshToken(RefreshToken token, long memberId) {
        if (token == null) {
            throw new IllegalArgumentException("Refresh token is required");
        }
        if (memberId <= 0) {
            throw new IllegalArgumentException("Member ID must be positive");
        }

        this.token = token;
        this.memberId = memberId;
    }
}
