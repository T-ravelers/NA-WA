package me.nawa.auth.profile;

import me.nawa.auth.exception.AuthErrorCode;
import me.nawa.auth.mapper.AuthMapper;
import me.nawa.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthMeServiceTest {

    @Test
    void getCurrentMember_activeIncompleteMember_returnsOnboardingRequired() {
        AuthMemberProfile profile = AuthMemberProfile.active(7L, false);
        AuthMeService service = serviceReturning(profile);

        AuthMeResponse response = service.getCurrentMember(7L);

        assertEquals(7L, response.getMemberId());
        assertEquals("여행자", response.getDisplayName());
        assertEquals("en", response.getPreferredLanguage());
        assertTrue(response.isOnboardingRequired());
    }

    @Test
    void getCurrentMember_activeCompletedMember_returnsOnboardingNotRequired() {
        AuthMeResponse response = serviceReturning(
                AuthMemberProfile.active(8L, true)
        ).getCurrentMember(8L);

        assertFalse(response.isOnboardingRequired());
    }

    @Test
    void getCurrentMember_missingMember_returnsAuthenticationRequired() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> serviceReturning(null).getCurrentMember(9L)
        );

        assertEquals(
                AuthErrorCode.AUTHENTICATION_REQUIRED,
                exception.getErrorCode()
        );
    }

    @Test
    void getCurrentMember_suspendedMember_returnsForbiddenStatus() {
        AuthMemberProfile profile = AuthMemberProfile.active(10L, true);
        profile.setMemberStatus("SUSPENDED");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> serviceReturning(profile).getCurrentMember(10L)
        );

        assertEquals(
                AuthErrorCode.OAUTH_MEMBER_SUSPENDED,
                exception.getErrorCode()
        );
    }

    @Test
    void getCurrentMember_withdrawnOrDeletedMember_returnsWithdrawnStatus() {
        AuthMemberProfile profile = AuthMemberProfile.active(11L, true);
        profile.setDeleted(true);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> serviceReturning(profile).getCurrentMember(11L)
        );

        assertEquals(
                AuthErrorCode.OAUTH_MEMBER_WITHDRAWN,
                exception.getErrorCode()
        );
    }

    private AuthMeService serviceReturning(AuthMemberProfile profile) {
        AuthMapper mapper = memberId -> profile;
        return new AuthMeServiceImpl(mapper);
    }
}
