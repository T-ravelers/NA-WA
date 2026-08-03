package me.nawa.auth.token;

import lombok.Getter;
import me.nawa.auth.jwt.AccessToken;
import me.nawa.auth.refresh.RefreshToken;

@Getter
public final class AuthTokens {
    private final AccessToken accessToken;
    private final RefreshToken refreshToken;

    public AuthTokens(
            AccessToken accessToken,
            RefreshToken refreshToken) {
        if (accessToken == null) {
            throw new IllegalArgumentException("Access token is required");
        }
        if (refreshToken == null) {
            throw new IllegalArgumentException("Refresh token is required");
        }

        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
    }
}
