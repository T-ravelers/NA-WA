package me.nawa.member.service;

import me.nawa.common.exception.BusinessException;
import me.nawa.member.domain.MemberProfile;
import me.nawa.member.dto.MemberProfileResponse;
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
}
