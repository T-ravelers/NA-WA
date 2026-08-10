package me.nawa.auth.refresh;

import lombok.RequiredArgsConstructor;
import me.nawa.auth.exception.AuthErrorCode;
import me.nawa.common.exception.BusinessException;
import me.nawa.member.domain.MemberAuthState;
import me.nawa.member.mapper.MemberMapper;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenServiceImpl implements RefreshTokenService {
    private final RefreshTokenProvider refreshTokenProvider;
    private final RefreshTokenStore refreshTokenStore;
    private final MemberMapper memberMapper;

    @Override
    public RefreshToken issueRefreshToken(long memberId) {
        RefreshToken token = refreshTokenProvider.issueRefreshToken();
        refreshTokenStore.save(toSession(memberId, token));
        return token;
    }

    @Override
    public RotatedRefreshToken rotateRefreshToken(String currentToken) {
        UUID sessionId = extractSessionId(currentToken);
        RefreshTokenSession currentSession = refreshTokenStore
                .findBySessionId(sessionId)
                .orElseThrow(
                        () -> new BusinessException(
                                AuthErrorCode.INVALID_REFRESH_TOKEN
                        )
                );

        requireActiveMember(currentSession.getMemberId(), sessionId);

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
            return new RotatedRefreshToken(
                    replacementToken,
                    currentSession.getMemberId()
            );
        }
        if (result == RefreshTokenRotationResult.REUSE_DETECTED) {
            throw new BusinessException(
                    AuthErrorCode.REFRESH_TOKEN_REUSE_DETECTED
            );
        }
        throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
    }

    @Override
    public void revokeRefreshToken(String token) {
        try {
            UUID sessionId = refreshTokenProvider.extractSessionId(token);
            refreshTokenStore.deleteBySessionId(sessionId);
        } catch (IllegalArgumentException ignored) {
            // 로그아웃은 멱등성을 유지하기 위해 잘못된 토큰도 폐기 완료로 처리합니다.
        }
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

    private void requireActiveMember(long memberId, UUID sessionId) {
        MemberAuthState authState = memberMapper.findAuthState(memberId);
        if (authState == null
                || authState.isDeleted()
                || "WITHDRAWN".equals(authState.getMemberStatus())) {
            revokeInactiveSession(
                    sessionId,
                    AuthErrorCode.OAUTH_MEMBER_WITHDRAWN
            );
        }
        if ("SUSPENDED".equals(authState.getMemberStatus())) {
            revokeInactiveSession(
                    sessionId,
                    AuthErrorCode.OAUTH_MEMBER_SUSPENDED
            );
        }
        if (!"ACTIVE".equals(authState.getMemberStatus())) {
            throw new IllegalStateException(
                    "Stored member status is invalid"
            );
        }
    }

    private void revokeInactiveSession(
            UUID sessionId,
            AuthErrorCode errorCode) {
        refreshTokenStore.deleteBySessionId(sessionId);
        throw new BusinessException(errorCode);
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
