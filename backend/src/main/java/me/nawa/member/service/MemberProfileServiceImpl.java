package me.nawa.member.service;

import lombok.RequiredArgsConstructor;
import me.nawa.auth.exception.AuthErrorCode;
import me.nawa.common.exception.BusinessException;
import me.nawa.member.domain.MemberProfile;
import me.nawa.member.dto.MemberProfileResponse;
import me.nawa.member.exception.MemberErrorCode;
import me.nawa.member.mapper.MemberMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberProfileServiceImpl implements MemberProfileService {

    private final MemberMapper memberMapper;

    @Override
    @Transactional(readOnly = true)
    public MemberProfileResponse getProfile(long memberId) {
        return new MemberProfileResponse(loadActiveProfile(memberId));
    }

    /**
     * 활성 회원의 프로필만 돌려준다.
     *
     * 탈퇴·정지 회원은 인증 쿠키가 아직 살아 있어도 서비스에 들어오지 못한다.
     * 조회와 수정이 같은 검사를 공유해야 하므로 여기 한 곳에 둔다.
     */
    private MemberProfile loadActiveProfile(long memberId) {
        MemberProfile profile = memberMapper.findProfile(memberId);

        if (profile == null) {
            throw new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND);
        }
        if (profile.isDeleted() || "WITHDRAWN".equals(profile.getMemberStatus())) {
            throw new BusinessException(AuthErrorCode.OAUTH_MEMBER_WITHDRAWN);
        }
        if ("SUSPENDED".equals(profile.getMemberStatus())) {
            throw new BusinessException(AuthErrorCode.OAUTH_MEMBER_SUSPENDED);
        }
        if (!"ACTIVE".equals(profile.getMemberStatus())) {
            throw new BusinessException(AuthErrorCode.AUTHENTICATION_REQUIRED);
        }

        return profile;
    }
}
