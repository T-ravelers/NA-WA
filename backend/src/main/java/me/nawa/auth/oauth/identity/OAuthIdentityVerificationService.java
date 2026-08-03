package me.nawa.auth.oauth.identity;

import me.nawa.auth.oauth.state.OAuthStateSession;
import me.nawa.auth.oauth.token.OAuthProviderTokenSet;

public interface OAuthIdentityVerificationService {
    OAuthUserProfile verify(
            OAuthStateSession stateSession,
            OAuthProviderTokenSet tokenSet
    );
}
