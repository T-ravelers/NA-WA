package me.nawa.auth.refresh;

public interface RefreshTokenService {
    RefreshToken issueRefreshToken(long memberId);

    RefreshToken rotateRefreshToken(String currentToken);
}
