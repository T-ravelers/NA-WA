package me.nawa.auth.oauth.token;

import me.nawa.auth.oauth.state.OAuthStateSession;

public interface OAuthTokenExchangeService {
    OAuthProviderTokenSet exchange(
            OAuthStateSession stateSession,
            String authorizationCode
    );
}
