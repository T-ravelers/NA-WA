package me.nawa.auth.oauth.state;

import lombok.Getter;

import java.time.Instant;

@Getter
public class OAuthAuthorizationRequestState {
    private final String state;
    private final String nonce;
    private final String codeChallenge;
    private final String codeChallengeMethod;
    private final String browserBinding;
    private final Instant expiresAt;

    public OAuthAuthorizationRequestState(
            String state,
            String nonce,
            String codeChallenge,
            String codeChallengeMethod,
            String browserBinding,
            Instant expiresAt) {
        this.state = state;
        this.nonce = nonce;
        this.codeChallenge = codeChallenge;
        this.codeChallengeMethod = codeChallengeMethod;
        this.browserBinding = browserBinding;
        this.expiresAt = expiresAt;
    }
}
