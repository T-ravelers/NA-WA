package me.nawa.auth.oauth.callback;

import lombok.Getter;
import me.nawa.auth.token.AuthTokens;

import java.net.URI;
import java.util.Objects;

@Getter
public class OAuthCallbackResult {
    private final AuthTokens tokens;
    private final URI redirectUri;

    public OAuthCallbackResult(AuthTokens tokens, URI redirectUri) {
        this.tokens = Objects.requireNonNull(
                tokens,
                "Auth tokens are required"
        );
        this.redirectUri = Objects.requireNonNull(
                redirectUri,
                "OAuth callback redirect URI is required"
        );
    }
}
