package me.nawa.auth.oauth.account;

import me.nawa.auth.oauth.identity.OAuthUserProfile;

import java.util.Optional;

public interface OAuthMemberTransaction {
    OAuthLoginAccount resolveOrCreate(OAuthUserProfile profile);

    Optional<OAuthLoginAccount> findExisting(OAuthUserProfile profile);
}
