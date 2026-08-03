package me.nawa.auth.oauth.account;

import me.nawa.auth.oauth.identity.OAuthUserProfile;

public interface OAuthMemberService {
    long resolveMemberId(OAuthUserProfile profile);
}
