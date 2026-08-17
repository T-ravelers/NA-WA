package me.nawa.auth.oauth.state;

import me.nawa.auth.oauth.OAuthProvider;

import java.util.Optional;

public interface OAuthStateService {
    OAuthAuthorizationRequestState issue(
            OAuthProvider provider,
            String returnPath);

    Optional<OAuthStateSession> consume(String state, String browserBinding);
}
