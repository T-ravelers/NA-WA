package me.nawa.auth.oauth.state;

import lombok.Getter;
import me.nawa.auth.oauth.OAuthProvider;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Objects;

@Getter
public class OAuthStateSession {
    private final String state;
    private final OAuthProvider provider;
    private final String nonce;
    private final String codeVerifier;
    private final String returnPath;
    private final Instant issuedAt;
    private final Instant expiresAt;

    public OAuthStateSession(
            String state,
            OAuthProvider provider,
            String nonce,
            String codeVerifier,
            String returnPath,
            Instant issuedAt,
            Instant expiresAt) {
        if (!StringUtils.hasText(state)) {
            throw new IllegalArgumentException("OAuth state must not be blank");
        }
        if (!StringUtils.hasText(nonce)) {
            throw new IllegalArgumentException("OAuth nonce must not be blank");
        }
        if (!StringUtils.hasText(returnPath)) {
            throw new IllegalArgumentException(
                    "OAuth return path must not be blank"
            );
        }

        this.provider = Objects.requireNonNull(
                provider,
                "OAuth provider is required"
        );
        if (provider.isPkceRequired()
                && !StringUtils.hasText(codeVerifier)) {
            throw new IllegalArgumentException(
                    "PKCE verifier is required for this provider"
            );
        }
        if (!provider.isPkceRequired()
                && StringUtils.hasText(codeVerifier)) {
            throw new IllegalArgumentException(
                    "PKCE verifier is not supported for this provider"
            );
        }

        this.issuedAt = Objects.requireNonNull(
                issuedAt,
                "OAuth state issuedAt is required"
        );
        this.expiresAt = Objects.requireNonNull(
                expiresAt,
                "OAuth state expiresAt is required"
        );
        if (!expiresAt.isAfter(issuedAt)) {
            throw new IllegalArgumentException(
                    "OAuth state expiresAt must be after issuedAt"
            );
        }

        this.state = state;
        this.nonce = nonce;
        this.codeVerifier = StringUtils.hasText(codeVerifier)
                ? codeVerifier
                : null;
        this.returnPath = returnPath;
    }
}
