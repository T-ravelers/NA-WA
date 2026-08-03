package me.nawa.auth.oauth.authorization;

import java.net.URI;

public interface OAuthAuthorizationService {
    URI createAuthorizationUri(String provider, String returnPath);
}
