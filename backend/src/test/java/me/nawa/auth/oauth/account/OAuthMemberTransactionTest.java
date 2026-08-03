package me.nawa.auth.oauth.account;

import me.nawa.auth.mapper.OAuthAccountMapper;
import me.nawa.auth.oauth.OAuthProvider;
import me.nawa.auth.oauth.identity.OAuthUserProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class OAuthMemberTransactionTest {
    private FakeOAuthAccountMapper mapper;
    private OAuthMemberTransaction transaction;

    @BeforeEach
    void setUp() {
        mapper = new FakeOAuthAccountMapper();
        transaction = new OAuthMemberTransactionImpl(mapper);
    }

    @Test
    void resolveOrCreate_existingAccount_doesNotInsertAnotherMember() {
        mapper.foundAccount = OAuthLoginAccount.newActive(42L);

        OAuthLoginAccount result = transaction.resolveOrCreate(
                googleProfile("traveler@example.com", "Traveler")
        );

        assertEquals(42L, result.getMemberId());
        assertEquals(0, mapper.insertMemberCalls);
        assertEquals(0, mapper.insertSocialAccountCalls);
    }

    @Test
    void resolveOrCreate_firstLogin_insertsMemberAndSocialAccount() {
        mapper.generatedMemberId = 101L;

        OAuthLoginAccount result = transaction.resolveOrCreate(
                googleProfile("traveler@example.com", "Traveler")
        );

        assertEquals(101L, result.getMemberId());
        assertEquals("ACTIVE", result.getMemberStatus());
        assertFalse(result.isMemberDeleted());
        assertEquals("Traveler", mapper.insertedMember.getDisplayName());
        assertEquals(101L, mapper.insertedSocialMemberId);
        assertEquals("google", mapper.insertedProvider);
        assertEquals("google-user-id", mapper.insertedProviderUserId);
        assertEquals(
                "traveler@example.com",
                mapper.insertedProviderEmail
        );
    }

    @Test
    void resolveOrCreate_lineWithoutEmail_usesDefaultNameAndNullEmail() {
        mapper.generatedMemberId = 102L;
        OAuthUserProfile profile = new OAuthUserProfile(
                OAuthProvider.LINE,
                "line-user-id",
                null,
                null,
                null
        );

        transaction.resolveOrCreate(profile);

        assertEquals("여행자", mapper.insertedMember.getDisplayName());
        assertEquals("line", mapper.insertedProvider);
        assertEquals("line-user-id", mapper.insertedProviderUserId);
        assertNull(mapper.insertedProviderEmail);
    }

    @Test
    void resolveOrCreate_longProviderValues_areSafeForSchemaColumns() {
        mapper.generatedMemberId = 103L;
        String longName = "여".repeat(60);
        String longImageUrl = "https://images.example/" + "a".repeat(500);
        String longEmail = "a".repeat(321);

        transaction.resolveOrCreate(googleProfile(longEmail, longName)
                .withProfileImageUrlForTest(longImageUrl));

        assertEquals(
                50,
                mapper.insertedMember.getDisplayName().codePointCount(
                        0,
                        mapper.insertedMember.getDisplayName().length()
                )
        );
        assertNull(mapper.insertedMember.getProfileImageUrl());
        assertNull(mapper.insertedProviderEmail);
    }

    private TestOAuthUserProfile googleProfile(
            String email,
            String displayName) {
        return new TestOAuthUserProfile(
                email,
                displayName,
                "https://images.example/profile.png"
        );
    }

    private static final class TestOAuthUserProfile extends OAuthUserProfile {
        private TestOAuthUserProfile(
                String email,
                String displayName,
                String profileImageUrl) {
            super(
                    OAuthProvider.GOOGLE,
                    "google-user-id",
                    email,
                    displayName,
                    profileImageUrl
            );
        }

        private TestOAuthUserProfile withProfileImageUrlForTest(String url) {
            return new TestOAuthUserProfile(getEmail(), getDisplayName(), url);
        }
    }

    private static final class FakeOAuthAccountMapper
            implements OAuthAccountMapper {
        private OAuthLoginAccount foundAccount;
        private long generatedMemberId;
        private int insertMemberCalls;
        private int insertSocialAccountCalls;
        private OAuthMemberInsert insertedMember;
        private long insertedSocialMemberId;
        private String insertedProvider;
        private String insertedProviderUserId;
        private String insertedProviderEmail;

        @Override
        public OAuthLoginAccount findLoginAccount(
                String provider,
                String providerUserId) {
            return foundAccount;
        }

        @Override
        public int insertMember(OAuthMemberInsert member) {
            insertMemberCalls++;
            insertedMember = member;
            member.setMemberId(generatedMemberId);
            return 1;
        }

        @Override
        public int insertSocialAccount(
                long memberId,
                String provider,
                String providerUserId,
                String providerEmail) {
            insertSocialAccountCalls++;
            insertedSocialMemberId = memberId;
            insertedProvider = provider;
            insertedProviderUserId = providerUserId;
            insertedProviderEmail = providerEmail;
            return 1;
        }
    }
}
