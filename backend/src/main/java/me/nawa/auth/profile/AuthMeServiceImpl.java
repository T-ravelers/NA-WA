package me.nawa.auth.profile;

import lombok.RequiredArgsConstructor;
import me.nawa.auth.exception.AuthErrorCode;
import me.nawa.auth.mapper.AuthMapper;
import me.nawa.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthMeServiceImpl implements AuthMeService {
    private final AuthMapper authMapper;

    @Override
    @Transactional(readOnly = true)
    public AuthMeResponse getCurrentMember(long memberId) {
        AuthMemberProfile profile = authMapper.findMemberProfile(memberId);
        if (profile == null) {
            throw new BusinessException(
                    AuthErrorCode.AUTHENTICATION_REQUIRED
            );
        }
        if (profile.isDeleted()
                || "WITHDRAWN".equals(profile.getMemberStatus())) {
            throw new BusinessException(
                    AuthErrorCode.OAUTH_MEMBER_WITHDRAWN
            );
        }
        if ("SUSPENDED".equals(profile.getMemberStatus())) {
            throw new BusinessException(
                    AuthErrorCode.OAUTH_MEMBER_SUSPENDED
            );
        }
        if (!"ACTIVE".equals(profile.getMemberStatus())) {
            throw new BusinessException(
                    AuthErrorCode.AUTHENTICATION_REQUIRED
            );
        }
        return new AuthMeResponse(profile);
    }
}
