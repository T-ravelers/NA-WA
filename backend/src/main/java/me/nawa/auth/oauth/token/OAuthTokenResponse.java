package me.nawa.auth.oauth.token;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import me.nawa.auth.oauth.OAuthProvider;

@JsonIgnoreProperties(ignoreUnknown = true)
final class OAuthTokenResponse {
    private final String accessToken;
    private final String idToken;
    private final Long expiresIn;
    private final String tokenType;
    private final String scope;

    OAuthTokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("id_token") String idToken,
            @JsonProperty("expires_in") Long expiresIn,
            @JsonProperty("token_type") String tokenType,
            @JsonProperty("scope") String scope) {
        this.accessToken = accessToken;
        this.idToken = idToken;
        this.expiresIn = expiresIn;
        this.tokenType = tokenType;
        this.scope = scope;
    }

    OAuthProviderTokenSet toTokenSet(OAuthProvider provider) {
        if (expiresIn == null) {
            throw new IllegalArgumentException(
                    "OAuth token expiration is required"
            );
        }
        return new OAuthProviderTokenSet(
                provider,
                accessToken,
                idToken,
                expiresIn,
                tokenType,
                scope
        );
    }
}
