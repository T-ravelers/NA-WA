package me.nawa.auth.oauth.authorization;

public interface OAuthAuthorizationService {
    OAuthAuthorizationRedirect createAuthorizationRedirect(
            String provider,
            String returnPath);
}
