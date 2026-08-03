package me.nawa.auth.refresh;

public interface RefreshTokenService {
    RefreshToken issueRefreshToken(long memberId);

    RotatedRefreshToken rotateRefreshToken(String currentToken);

    void revokeRefreshToken(String token);
}
