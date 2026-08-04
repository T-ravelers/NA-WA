package me.nawa.auth.oauth.account;

import me.nawa.auth.exception.AuthErrorCode;
import me.nawa.auth.oauth.OAuthProvider;
import me.nawa.auth.oauth.identity.OAuthUserProfile;
import me.nawa.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OAuthMemberServiceTest {
    private FakeOAuthMemberTransaction transaction;
    private OAuthMemberService service;

    @BeforeEach
    void setUp() {
        transaction = new FakeOAuthMemberTransaction();
        service = new OAuthMemberServiceImpl(transaction);
    }

    @Test
    void resolveMemberId_activeExistingAccount_returnsMemberId() {
        transaction.resolved = account(42L, "ACTIVE", false, false);

        assertEquals(42L, service.resolveMemberId(profile()));
    }

    @Test
    void resolveMemberId_concurrentInsertConflict_reloadsWinner() {
        transaction.duplicateOnResolve = true;
        transaction.existing = account(77L, "ACTIVE", false, false);

        assertEquals(77L, service.resolveMemberId(profile()));
        assertEquals(1, transaction.findExistingCalls);
    }

    @Test
    void resolveMemberId_concurrentInsertWithoutWinner_returnsStableError() {
        transaction.duplicateOnResolve = true;

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.resolveMemberId(profile())
        );

        assertEquals(
                AuthErrorCode.OAUTH_ACCOUNT_PROVISION_FAILED,
                exception.getErrorCode()
        );
    }

    @Test
    void resolveMemberId_suspendedMember_isForbidden() {
        transaction.resolved = account(42L, "SUSPENDED", false, false);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.resolveMemberId(profile())
        );

        assertEquals(
                AuthErrorCode.OAUTH_MEMBER_SUSPENDED,
                exception.getErrorCode()
        );
    }

    @Test
    void resolveMemberId_withdrawnMember_isForbidden() {
        transaction.resolved = account(42L, "WITHDRAWN", false, false);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.resolveMemberId(profile())
        );

        assertEquals(
                AuthErrorCode.OAUTH_MEMBER_WITHDRAWN,
                exception.getErrorCode()
        );
    }

    @Test
    void resolveMemberId_softDeletedMember_isForbidden() {
        transaction.resolved = account(42L, "ACTIVE", true, false);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.resolveMemberId(profile())
        );

        assertEquals(
                AuthErrorCode.OAUTH_MEMBER_WITHDRAWN,
                exception.getErrorCode()
        );
    }

    @Test
    void resolveMemberId_softDeletedSocialAccount_isForbidden() {
        transaction.resolved = account(42L, "ACTIVE", false, true);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.resolveMemberId(profile())
        );

        assertEquals(
                AuthErrorCode.OAUTH_MEMBER_WITHDRAWN,
                exception.getErrorCode()
        );
    }

    private OAuthUserProfile profile() {
        return new OAuthUserProfile(
                OAuthProvider.GOOGLE,
                "google-user-id",
                "traveler@example.com",
                "Traveler",
                "https://images.example/profile.png"
        );
    }

    private OAuthLoginAccount account(
            long memberId,
            String status,
            boolean memberDeleted,
            boolean socialDeleted) {
        OAuthLoginAccount account = new OAuthLoginAccount();
        account.setMemberId(memberId);
        account.setMemberStatus(status);
        account.setMemberDeleted(memberDeleted);
        account.setSocialAccountDeleted(socialDeleted);
        return account;
    }

    private static final class FakeOAuthMemberTransaction
            implements OAuthMemberTransaction {
        private OAuthLoginAccount resolved;
        private OAuthLoginAccount existing;
        private boolean duplicateOnResolve;
        private int findExistingCalls;

        @Override
        public OAuthLoginAccount resolveOrCreate(OAuthUserProfile profile) {
            if (duplicateOnResolve) {
                throw new DuplicateKeyException("duplicate social account");
            }
            return resolved;
        }

        @Override
        public Optional<OAuthLoginAccount> findExisting(
                OAuthUserProfile profile) {
            findExistingCalls++;
            return Optional.ofNullable(existing);
        }
    }
}
