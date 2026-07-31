package me.nawa.auth.refresh;

import lombok.RequiredArgsConstructor;
import me.nawa.auth.exception.AuthErrorCode;
import me.nawa.common.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenServiceImpl implements RefreshTokenService {
    private final RefreshTokenProvider refreshTokenProvider;
    private final RefreshTokenStore refreshTokenStore;

    @Override
    public RefreshToken issueRefreshToken(long memberId) {
        RefreshToken token = refreshTokenProvider.issueRefreshToken();
        refreshTokenStore.save(toSession(memberId, token));
        return token;
    }

    @Override
    public RefreshToken rotateRefreshToken(String currentToken) {
        UUID sessionId = extractSessionId(currentToken);
        RefreshTokenSession currentSession = refreshTokenStore
                .findBySessionId(sessionId)
                .orElseThrow(
                        () -> new BusinessException(
                                AuthErrorCode.INVALID_REFRESH_TOKEN
                        )
                );

        RefreshToken replacementToken =
                refreshTokenProvider.issueRefreshToken(sessionId);
        RefreshTokenSession replacementSession = toSession(
                currentSession.getMemberId(),
                replacementToken
        );
        RefreshTokenRotationResult result = refreshTokenStore.rotate(
                sessionId,
                refreshTokenProvider.hashToken(currentToken),
                replacementSession
        );

        if (result == RefreshTokenRotationResult.ROTATED) {
            return replacementToken;
        }
        if (result == RefreshTokenRotationResult.REUSE_DETECTED) {
            throw new BusinessException(
                    AuthErrorCode.REFRESH_TOKEN_REUSE_DETECTED
            );
        }
        throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
    }

    private UUID extractSessionId(String token) {
        try {
            return refreshTokenProvider.extractSessionId(token);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(
                    AuthErrorCode.INVALID_REFRESH_TOKEN,
                    exception
            );
        }
    }

    private RefreshTokenSession toSession(long memberId, RefreshToken token) {
        return new RefreshTokenSession(
                token.getSessionId(),
                memberId,
                refreshTokenProvider.hashToken(token.getValue()),
                token.getIssuedAt(),
                token.getExpiresAt()
        );
    }
}
