package me.nawa.member.service;

import me.nawa.common.exception.BusinessException;
import me.nawa.member.domain.MemberProfile;
import me.nawa.member.dto.MemberProfileResponse;
import me.nawa.member.dto.MerchantRegisterRequest;
import me.nawa.member.dto.OnboardingProfileRequest;
import me.nawa.member.dto.UpdateMemberProfileRequest;
import me.nawa.member.mapper.MemberMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberProfileServiceImplTest {

    @Mock
    private MemberMapper memberMapper;

    @InjectMocks
    private MemberProfileServiceImpl service;

    private MemberProfile profile(String status, boolean onboardingCompleted) {
        MemberProfile profile = new MemberProfile();
        profile.setMemberId(1L);
        profile.setDisplayName("여행자");
        profile.setPreferredLanguage("en");
        profile.setPreferredCurrencyCode("JPY");
        profile.setMemberStatus(status);
        profile.setOnboardingCompleted(onboardingCompleted);
        profile.setDeleted(false);
        return profile;
    }

    /** (displayName, profileImageUrl, nationalityCode, preferredLanguage, preferredCurrencyCode) */
    private UpdateMemberProfileRequest updateRequest(
            String displayName,
            String profileImageUrl,
            String nationalityCode,
            String preferredLanguage,
            String preferredCurrencyCode) {
        return new UpdateMemberProfileRequest(
                displayName, profileImageUrl, nationalityCode,
                preferredLanguage, preferredCurrencyCode);
    }

    @Test
    void getProfile_returnsProfile_whenMemberIsActive() {
        when(memberMapper.findProfile(1L)).thenReturn(profile("ACTIVE", true));

        MemberProfileResponse response = service.getProfile(1L);

        assertEquals(1L, response.getMemberId());
        assertEquals("en", response.getPreferredLanguage());
        assertEquals("JPY", response.getPreferredCurrencyCode());
        assertFalse(response.isOnboardingRequired());
    }

    @Test
    void getProfile_marksOnboardingRequired_whenOnboardingNotCompleted() {
        when(memberMapper.findProfile(1L)).thenReturn(profile("ACTIVE", false));

        assertTrue(service.getProfile(1L).isOnboardingRequired());
    }

    @Test
    void getProfile_throwsMemberNotFound_whenNoRow() {
        when(memberMapper.findProfile(1L)).thenReturn(null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.getProfile(1L)
        );

        assertEquals("MEMBER-001", exception.getErrorCode().getCode());
    }

    @Test
    void getProfile_throwsWithdrawn_whenMemberWithdrawn() {
        when(memberMapper.findProfile(1L)).thenReturn(profile("WITHDRAWN", true));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.getProfile(1L)
        );

        assertEquals("AUTH-017", exception.getErrorCode().getCode());
    }

    @Test
    void getProfile_throwsSuspended_whenMemberSuspended() {
        when(memberMapper.findProfile(1L)).thenReturn(profile("SUSPENDED", true));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.getProfile(1L)
        );

        assertEquals("AUTH-016", exception.getErrorCode().getCode());
    }

    @Test
    void updateProfile_updatesLanguage_andReturnsRefreshedProfile() {
        when(memberMapper.findProfile(1L)).thenReturn(profile("ACTIVE", true));
        when(memberMapper.updateProfile(1L, null, null, null, "ja", null)).thenReturn(1);

        MemberProfileResponse response = service.updateProfile(
                1L,
                updateRequest(null, null, null, "ja", null)
        );

        verify(memberMapper).updateProfile(1L, null, null, null, "ja", null);
        assertEquals(1L, response.getMemberId());
    }

    @Test
    void updateProfile_trimsDisplayName_beforeSaving() {
        when(memberMapper.findProfile(1L)).thenReturn(profile("ACTIVE", true));
        when(memberMapper.updateProfile(1L, "새 이름", null, null, null, null)).thenReturn(1);

        service.updateProfile(1L, updateRequest("  새 이름  ", null, null, null, null));

        verify(memberMapper).updateProfile(1L, "새 이름", null, null, null, null);
    }

    @Test
    void updateProfile_updatesNationality_whenIsoAlpha2Code() {
        when(memberMapper.findProfile(1L)).thenReturn(profile("ACTIVE", true));
        when(memberMapper.updateProfile(1L, null, null, "KR", null, null)).thenReturn(1);

        service.updateProfile(1L, updateRequest(null, null, "KR", null, null));

        verify(memberMapper).updateProfile(1L, null, null, "KR", null, null);
    }

    @Test
    void updateProfile_throwsUnsupportedNationality_whenNotIsoCode() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.updateProfile(1L, updateRequest(null, null, "XX", null, null))
        );

        assertEquals("MEMBER-005", exception.getErrorCode().getCode());
    }

    @Test
    void updateProfile_throwsInvalidDisplayName_whenBlank() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.updateProfile(1L, updateRequest("   ", null, null, null, null))
        );

        assertEquals("MEMBER-006", exception.getErrorCode().getCode());
    }

    @Test
    void updateProfile_throwsInvalidDisplayName_whenTooLong() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.updateProfile(
                        1L, updateRequest("가".repeat(51), null, null, null, null))
        );

        assertEquals("MEMBER-006", exception.getErrorCode().getCode());
    }

    @Test
    void updateProfile_throwsInvalidProfileImageUrl_whenBlank() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.updateProfile(1L, updateRequest(null, "   ", null, null, null))
        );

        assertEquals("MEMBER-007", exception.getErrorCode().getCode());
    }

    @Test
    void updateProfile_throwsUnsupportedLanguage_whenLanguageNotInAllowList() {
        // 언어 검사는 회원 조회보다 먼저 일어나므로 findProfile을 stub하지 않는다.
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.updateProfile(1L, updateRequest(null, null, null, "ko", null))
        );

        assertEquals("MEMBER-002", exception.getErrorCode().getCode());
    }

    @Test
    void updateProfile_acceptsEverySupportedLanguage() {
        when(memberMapper.findProfile(1L)).thenReturn(profile("ACTIVE", true));
        when(memberMapper.updateProfile(
                eq(1L), isNull(), isNull(), isNull(), anyString(), isNull())).thenReturn(1);

        for (String language : new String[] {"en", "ja", "zh-TW", "vi"}) {
            service.updateProfile(1L, updateRequest(null, null, null, language, null));
        }

        verify(memberMapper, times(4)).updateProfile(
                eq(1L), isNull(), isNull(), isNull(), anyString(), isNull());
    }

    @Test
    void updateProfile_throwsUnsupportedCurrency_whenCurrencyInactiveOrMissing() {
        when(memberMapper.findProfile(1L)).thenReturn(profile("ACTIVE", true));
        when(memberMapper.existsActiveCurrency("XXX")).thenReturn(false);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.updateProfile(1L, updateRequest(null, null, null, null, "XXX"))
        );

        assertEquals("MEMBER-003", exception.getErrorCode().getCode());
    }

    @Test
    void updateProfile_throwsNoUpdatableField_whenEveryFieldAbsent() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.updateProfile(1L, updateRequest(null, null, null, null, null))
        );

        assertEquals("MEMBER-004", exception.getErrorCode().getCode());
        verify(memberMapper, never()).updateProfile(
                anyLong(), any(), any(), any(), any(), any());
    }

    @Test
    void updateProfile_throwsMemberNotFound_whenNoRowUpdated() {
        // 검사와 갱신 사이에 소프트 삭제가 끼어든 경합. 무변경 200을 내보내지 않는다.
        when(memberMapper.findProfile(1L)).thenReturn(profile("ACTIVE", true));
        when(memberMapper.updateProfile(1L, null, null, null, "ja", null)).thenReturn(0);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.updateProfile(1L, updateRequest(null, null, null, "ja", null))
        );

        assertEquals("MEMBER-001", exception.getErrorCode().getCode());
    }

    @Test
    void completeOnboarding_savesProfile_andReturnsRefreshedProfile() {
        when(memberMapper.findProfile(1L))
                .thenReturn(profile("ACTIVE", false))
                .thenReturn(profile("ACTIVE", true));
        when(memberMapper.existsActiveCurrency("JPY")).thenReturn(true);
        when(memberMapper.completeOnboarding(1L, "여행자", "JP", "ja", "JPY")).thenReturn(1);

        MemberProfileResponse response = service.completeOnboarding(
                1L,
                new OnboardingProfileRequest("여행자", "JP", "ja", "JPY")
        );

        verify(memberMapper).completeOnboarding(1L, "여행자", "JP", "ja", "JPY");
        assertFalse(response.isOnboardingRequired());
    }

    @Test
    void completeOnboarding_throwsFieldMissing_whenAnyFieldAbsent() {
        OnboardingProfileRequest[] incompleteRequests = {
                new OnboardingProfileRequest(null, "JP", "ja", "JPY"),
                new OnboardingProfileRequest("여행자", null, "ja", "JPY"),
                new OnboardingProfileRequest("여행자", "JP", null, "JPY"),
                new OnboardingProfileRequest("여행자", "JP", "ja", null),
        };

        for (OnboardingProfileRequest request : incompleteRequests) {
            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> service.completeOnboarding(1L, request)
            );
            assertEquals("MEMBER-008", exception.getErrorCode().getCode());
        }
        verify(memberMapper, never()).completeOnboarding(
                anyLong(), any(), any(), any(), any());
    }

    @Test
    void completeOnboarding_throwsUnsupportedNationality_whenNotIsoCode() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.completeOnboarding(
                        1L, new OnboardingProfileRequest("여행자", "XX", "ja", "JPY"))
        );

        assertEquals("MEMBER-005", exception.getErrorCode().getCode());
    }

    @Test
    void completeOnboarding_throwsMemberNotFound_whenNoRowUpdated() {
        when(memberMapper.findProfile(1L)).thenReturn(profile("ACTIVE", false));
        when(memberMapper.existsActiveCurrency("JPY")).thenReturn(true);
        when(memberMapper.completeOnboarding(1L, "여행자", "JP", "ja", "JPY")).thenReturn(0);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.completeOnboarding(
                        1L, new OnboardingProfileRequest("여행자", "JP", "ja", "JPY"))
        );

        assertEquals("MEMBER-001", exception.getErrorCode().getCode());
    }

    @Test
    void registerAsMerchant_savesBusinessName_andReturnsRefreshedProfile() {
        MemberProfile merchant = profile("ACTIVE", true);
        merchant.setAccountType("MERCHANT");
        merchant.setDisplayName("○○ 카페");

        when(memberMapper.findProfile(1L))
                .thenReturn(profile("ACTIVE", false))
                .thenReturn(merchant);
        when(memberMapper.markAsMerchant(1L, "○○ 카페")).thenReturn(1);

        MemberProfileResponse response = service.registerAsMerchant(
                1L, new MerchantRegisterRequest("○○ 카페"));

        verify(memberMapper).markAsMerchant(1L, "○○ 카페");
        assertEquals("MERCHANT", response.getAccountType());
        assertEquals("○○ 카페", response.getDisplayName());
    }

    @Test
    void registerAsMerchant_trimsBusinessName() {
        when(memberMapper.findProfile(1L)).thenReturn(profile("ACTIVE", true));
        when(memberMapper.markAsMerchant(1L, "○○ 카페")).thenReturn(1);

        service.registerAsMerchant(1L, new MerchantRegisterRequest("  ○○ 카페  "));

        verify(memberMapper).markAsMerchant(1L, "○○ 카페");
    }

    @Test
    void registerAsMerchant_throwsInvalidDisplayName_whenBusinessNameBlank() {
        String[] blankNames = {null, "", "   "};

        for (String blankName : blankNames) {
            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> service.registerAsMerchant(1L, new MerchantRegisterRequest(blankName))
            );
            assertEquals("MEMBER-006", exception.getErrorCode().getCode());
        }
        verify(memberMapper, never()).markAsMerchant(anyLong(), anyString());
    }

    @Test
    void registerAsMerchant_throwsAlreadyMerchant_whenNoRowUpdated() {
        when(memberMapper.findProfile(1L)).thenReturn(profile("ACTIVE", true));
        when(memberMapper.markAsMerchant(1L, "○○ 카페")).thenReturn(0);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.registerAsMerchant(1L, new MerchantRegisterRequest("○○ 카페"))
        );

        assertEquals("MEMBER-009", exception.getErrorCode().getCode());
    }
}
