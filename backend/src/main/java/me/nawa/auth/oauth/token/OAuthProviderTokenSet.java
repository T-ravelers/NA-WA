package me.nawa.auth.oauth.token;

import lombok.Getter;
import me.nawa.auth.oauth.OAuthProvider;
import org.springframework.util.StringUtils;

import java.util.Objects;

@Getter
public class OAuthProviderTokenSet {
    private final OAuthProvider provider;
    private final String accessToken;
    private final String idToken;
    private final long expiresInSeconds;
    private final String tokenType;
    private final String scope;

    public OAuthProviderTokenSet(
            OAuthProvider provider,
            String accessToken,
            String idToken,
            long expiresInSeconds,
            String tokenType,
            String scope) {
        this.provider = Objects.requireNonNull(
                provider,
                "OAuth provider is required"
        );
        this.accessToken = requireText(accessToken, "OAuth access token");
        this.idToken = requireText(idToken, "OAuth ID token");
        if (expiresInSeconds <= 0) {
            throw new IllegalArgumentException(
                    "OAuth token expiration must be positive"
            );
        }
        if (!"Bearer".equalsIgnoreCase(
                requireText(tokenType, "OAuth token type")
        )) {
            throw new IllegalArgumentException(
                    "OAuth token type must be Bearer"
            );
        }
        this.expiresInSeconds = expiresInSeconds;
        this.tokenType = "Bearer";
        this.scope = StringUtils.hasText(scope) ? scope.trim() : null;
    }

    private static String requireText(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(
                    fieldName + " must not be blank"
            );
        }
        return value.trim();
    }
}
