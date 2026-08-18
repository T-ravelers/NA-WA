package me.nawa.member.service;

import lombok.RequiredArgsConstructor;
import me.nawa.auth.exception.AuthErrorCode;
import me.nawa.common.exception.BusinessException;
import me.nawa.member.domain.MemberProfile;
import me.nawa.member.dto.MemberProfileResponse;
import me.nawa.member.dto.MemberAppointmentProfileResponse;
import me.nawa.member.dto.OnboardingProfileRequest;
import me.nawa.member.dto.UpdateMemberProfileRequest;
import me.nawa.member.exception.MemberErrorCode;
import me.nawa.member.mapper.MemberMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MemberProfileServiceImpl implements MemberProfileService {

    /**
     * 서비스가 지원하는 언어. 이 목록이 정본이며 프론트엔드 SUPPORTED_LOCALES와 일치해야 한다.
     * 한국어는 서비스 로케일이 아니다(방한 외국인 대상).
     */
    private static final Set<String> SUPPORTED_LANGUAGES = Set.of("en", "ja", "zh-TW", "vi");

    /** 국적은 ISO 3166-1 alpha-2 코드로 저장한다. 프론트엔드 선택 목록도 이 집합에서 나온다. */
    private static final Set<String> ISO_COUNTRY_CODES = Set.of(Locale.getISOCountries());

    /** members.display_name VARCHAR(50) */
    private static final int DISPLAY_NAME_MAX_LENGTH = 50;

    /** members.profile_image_url VARCHAR(500) */
    private static final int PROFILE_IMAGE_URL_MAX_LENGTH = 500;

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
        String displayName = normalizeDisplayName(request.getDisplayName());
        String profileImageUrl = request.getProfileImageUrl();
        String nationality = request.getNationalityCode();
        String language = request.getPreferredLanguage();
        String currency = request.getPreferredCurrencyCode();

        // 회원을 조회하기 전에 요청 자체가 비어 있는지 먼저 본다.
        if (displayName == null && profileImageUrl == null && nationality == null
                && language == null && currency == null) {
            throw new BusinessException(MemberErrorCode.NO_UPDATABLE_FIELD);
        }
        validateDisplayName(displayName);
        validateProfileImageUrl(profileImageUrl);
        validateNationality(nationality);
        validateLanguage(language);

        loadActiveProfile(memberId);
        validateCurrencyExists(currency);

        int updatedRows = memberMapper.updateProfile(
                memberId, displayName, profileImageUrl, nationality, language, currency);

        // 검사와 갱신 사이에 소프트 삭제가 끼어들면 한 행도 안 바뀐다. 그대로 200을 내보내지 않는다.
        if (updatedRows == 0) {
            throw new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND);
        }

        return new MemberProfileResponse(loadActiveProfile(memberId));
    }

    @Override
    @Transactional
    public MemberProfileResponse completeOnboarding(
            long memberId,
            OnboardingProfileRequest request) {
        String displayName = normalizeDisplayName(request.getDisplayName());
        String nationality = request.getNationalityCode();
        String language = request.getPreferredLanguage();
        String currency = request.getPreferredCurrencyCode();

        // 온보딩은 최소 프로필을 한 번에 확정하는 절차라 부분 저장을 허용하지 않는다.
        if (displayName == null || nationality == null || language == null || currency == null) {
            throw new BusinessException(MemberErrorCode.ONBOARDING_FIELD_MISSING);
        }
        validateDisplayName(displayName);
        validateNationality(nationality);
        validateLanguage(language);

        loadActiveProfile(memberId);
        validateCurrencyExists(currency);

        int updatedRows = memberMapper.completeOnboarding(
                memberId, displayName, nationality, language, currency);

        if (updatedRows == 0) {
            throw new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND);
        }

        return new MemberProfileResponse(loadActiveProfile(memberId));
    }

    /** 앞뒤 공백을 정리하고, 공백뿐인 값은 형식 오류로 넘긴다(null과 구분해 검증 단계에서 잡는다). */
    private String normalizeDisplayName(String displayName) {
        if (displayName == null) {
            return null;
        }
        return displayName.strip();
    }

    private void validateDisplayName(String displayName) {
        if (displayName == null) {
            return;
        }
        if (displayName.isEmpty() || displayName.length() > DISPLAY_NAME_MAX_LENGTH) {
            throw new BusinessException(MemberErrorCode.INVALID_DISPLAY_NAME);
        }
    }

    private void validateProfileImageUrl(String profileImageUrl) {
        if (profileImageUrl == null) {
            return;
        }
        if (profileImageUrl.isBlank()
                || profileImageUrl.length() > PROFILE_IMAGE_URL_MAX_LENGTH) {
            throw new BusinessException(MemberErrorCode.INVALID_PROFILE_IMAGE_URL);
        }
    }

    private void validateNationality(String nationalityCode) {
        if (nationalityCode == null) {
            return;
        }
        if (!ISO_COUNTRY_CODES.contains(nationalityCode)) {
            throw new BusinessException(MemberErrorCode.UNSUPPORTED_NATIONALITY);
        }
    }

    private void validateLanguage(String language) {
        if (language == null) {
            return;
        }
        if (!SUPPORTED_LANGUAGES.contains(language)) {
            throw new BusinessException(MemberErrorCode.UNSUPPORTED_LANGUAGE);
        }
    }

    private void validateCurrencyExists(String currency) {
        if (currency == null) {
            return;
        }
        if (!memberMapper.existsActiveCurrency(currency)) {
            throw new BusinessException(MemberErrorCode.UNSUPPORTED_CURRENCY);
        }
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
