package me.nawa.auth.oauth.authorization;

import lombok.Getter;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;

@Getter
public class OAuthAuthorizationRedirect {
    private final URI authorizationUri;
    private final String browserBinding;
    private final Instant expiresAt;

    public OAuthAuthorizationRedirect(
            URI authorizationUri,
            String browserBinding,
            Instant expiresAt) {
        if (!StringUtils.hasText(browserBinding)) {
            throw new IllegalArgumentException(
                    "OAuth browser binding must not be blank"
            );
        }
        this.authorizationUri = Objects.requireNonNull(
                authorizationUri,
                "OAuth authorization URI is required"
        );
        this.browserBinding = browserBinding;
        this.expiresAt = Objects.requireNonNull(
                expiresAt,
                "OAuth browser binding expiresAt is required"
        );
    }
}
