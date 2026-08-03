package me.nawa.auth.token;

import lombok.RequiredArgsConstructor;
import me.nawa.auth.jwt.AccessToken;
import me.nawa.auth.jwt.JwtTokenProvider;
import me.nawa.auth.refresh.RefreshToken;
import me.nawa.auth.refresh.RefreshTokenService;
import me.nawa.auth.refresh.RotatedRefreshToken;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthTokenServiceImpl implements AuthTokenService {
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;

    @Override
    public AuthTokens issueTokens(long memberId) {
        AccessToken accessToken = jwtTokenProvider.issueAccessToken(memberId);
        RefreshToken refreshToken =
                refreshTokenService.issueRefreshToken(memberId);
        return new AuthTokens(accessToken, refreshToken);
    }

    @Override
    public AuthTokens refreshTokens(String currentRefreshToken) {
        RotatedRefreshToken rotated =
                refreshTokenService.rotateRefreshToken(currentRefreshToken);
        AccessToken accessToken = jwtTokenProvider.issueAccessToken(
                rotated.getMemberId()
        );
        return new AuthTokens(accessToken, rotated.getToken());
    }

    @Override
    public void revokeRefreshToken(String refreshToken) {
        refreshTokenService.revokeRefreshToken(refreshToken);
    }
}
