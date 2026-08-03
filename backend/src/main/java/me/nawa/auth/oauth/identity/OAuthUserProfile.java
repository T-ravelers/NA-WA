package me.nawa.auth.oauth.identity;

import lombok.Getter;
import me.nawa.auth.oauth.OAuthProvider;
import org.springframework.util.StringUtils;

import java.util.Objects;

@Getter
public class OAuthUserProfile {
    private static final int MAX_PROVIDER_USER_ID_LENGTH = 255;

    private final OAuthProvider provider;
    private final String providerUserId;
    private final String email;
    private final String displayName;
    private final String profileImageUrl;

    public OAuthUserProfile(
            OAuthProvider provider,
            String providerUserId,
            String email,
            String displayName,
            String profileImageUrl) {
        this.provider = Objects.requireNonNull(
                provider,
                "OAuth provider is required"
        );
        this.providerUserId = requireProviderUserId(providerUserId);
        this.email = optionalText(email);
        this.displayName = optionalText(displayName);
        this.profileImageUrl = optionalText(profileImageUrl);
    }

    private static String requireProviderUserId(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(
                    "OAuth provider user ID must not be blank"
            );
        }
        String normalized = value.trim();
        if (normalized.length() > MAX_PROVIDER_USER_ID_LENGTH) {
            throw new IllegalArgumentException(
                    "OAuth provider user ID is too long"
            );
        }
        return normalized;
    }

    private static String optionalText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
