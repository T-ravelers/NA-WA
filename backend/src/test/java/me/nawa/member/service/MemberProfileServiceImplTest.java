package me.nawa.member.service;

import me.nawa.common.exception.BusinessException;
import me.nawa.member.domain.MemberProfile;
import me.nawa.member.dto.MemberProfileResponse;
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

        MemberProfileResponse response = service.updateProfile(
                1L,
                new UpdateMemberProfileRequest("ja", null)
        );

        verify(memberMapper).updateProfile(1L, "ja", null);
        assertEquals(1L, response.getMemberId());
    }

    @Test
    void updateProfile_throwsUnsupportedLanguage_whenLanguageNotInAllowList() {
        // 언어 검사는 회원 조회보다 먼저 일어나므로 findProfile을 stub하지 않는다.
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.updateProfile(1L, new UpdateMemberProfileRequest("ko", null))
        );

        assertEquals("MEMBER-002", exception.getErrorCode().getCode());
    }

    @Test
    void updateProfile_acceptsEverySupportedLanguage() {
        when(memberMapper.findProfile(1L)).thenReturn(profile("ACTIVE", true));

        for (String language : new String[] {"en", "ja", "zh-TW", "vi"}) {
            service.updateProfile(1L, new UpdateMemberProfileRequest(language, null));
        }

        verify(memberMapper, times(4)).updateProfile(eq(1L), anyString(), isNull());
    }

    @Test
    void updateProfile_throwsUnsupportedCurrency_whenCurrencyInactiveOrMissing() {
        when(memberMapper.findProfile(1L)).thenReturn(profile("ACTIVE", true));
        when(memberMapper.existsActiveCurrency("XXX")).thenReturn(false);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.updateProfile(1L, new UpdateMemberProfileRequest(null, "XXX"))
        );

        assertEquals("MEMBER-003", exception.getErrorCode().getCode());
    }

    @Test
    void updateProfile_throwsNoUpdatableField_whenBothFieldsAbsent() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.updateProfile(1L, new UpdateMemberProfileRequest(null, null))
        );

        assertEquals("MEMBER-004", exception.getErrorCode().getCode());
        verify(memberMapper, never()).updateProfile(anyLong(), anyString(), anyString());
    }
}
