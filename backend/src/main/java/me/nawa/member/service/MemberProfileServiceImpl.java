package me.nawa.member.service;

import lombok.RequiredArgsConstructor;
import me.nawa.auth.exception.AuthErrorCode;
import me.nawa.common.exception.BusinessException;
import me.nawa.member.domain.MemberProfile;
import me.nawa.member.dto.MemberProfileResponse;
import me.nawa.member.dto.MemberAppointmentProfileResponse;
import me.nawa.member.dto.UpdateMemberProfileRequest;
import me.nawa.member.exception.MemberErrorCode;
import me.nawa.member.mapper.MemberMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class MemberProfileServiceImpl implements MemberProfileService {

    /**
     * 서비스가 지원하는 언어. 이 목록이 정본이며 프론트엔드 SUPPORTED_LOCALES와 일치해야 한다.
     * 한국어는 서비스 로케일이 아니다(방한 외국인 대상).
     */
    private static final Set<String> SUPPORTED_LANGUAGES = Set.of("en", "ja", "zh-TW", "vi");

    private final MemberMapper memberMapper;

    @Override
    @Transactional(readOnly = true)
    public MemberProfileResponse getProfile(long memberId) {
        return new MemberProfileResponse(loadActiveProfile(memberId));
    }

    @Override
    @Transactional(readOnly = true)
    public MemberAppointmentProfileResponse getAppointmentProfile(long memberId) {
        loadActiveProfile(memberId);
        return memberMapper.findAppointmentProfile(memberId);
    }

    @Override
    @Transactional
    public MemberProfileResponse updateProfile(
            long memberId,
            UpdateMemberProfileRequest request) {
        String language = request.getPreferredLanguage();
        String currency = request.getPreferredCurrencyCode();

        // 회원을 조회하기 전에 요청 자체가 비어 있는지 먼저 본다.
        if (language == null && currency == null) {
            throw new BusinessException(MemberErrorCode.NO_UPDATABLE_FIELD);
        }
        if (language != null && !SUPPORTED_LANGUAGES.contains(language)) {
            throw new BusinessException(MemberErrorCode.UNSUPPORTED_LANGUAGE);
        }

        loadActiveProfile(memberId);

        if (currency != null && !memberMapper.existsActiveCurrency(currency)) {
            throw new BusinessException(MemberErrorCode.UNSUPPORTED_CURRENCY);
        }

        memberMapper.updateProfile(memberId, language, currency);

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
