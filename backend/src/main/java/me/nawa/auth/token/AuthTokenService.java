package me.nawa.auth.token;

public interface AuthTokenService {
    AuthTokens issueTokens(long memberId);

    AuthTokens refreshTokens(String currentRefreshToken);

    void revokeRefreshToken(String refreshToken);
}
