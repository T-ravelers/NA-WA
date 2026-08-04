package me.nawa.auth.oauth.identity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import me.nawa.auth.oauth.OAuthProvider;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
final class LineIdTokenVerificationResponse {
    private static final String LINE_ISSUER = "https://access.line.me";

    private final String issuer;
    private final String subject;
    private final String audience;
    private final Long expiresAt;
    private final String nonce;
    private final String name;
    private final String picture;
    private final String email;

    LineIdTokenVerificationResponse(
            @JsonProperty("iss") String issuer,
            @JsonProperty("sub") String subject,
            @JsonProperty("aud") String audience,
            @JsonProperty("exp") Long expiresAt,
            @JsonProperty("nonce") String nonce,
            @JsonProperty("name") String name,
            @JsonProperty("picture") String picture,
            @JsonProperty("email") String email) {
        this.issuer = issuer;
        this.subject = subject;
        this.audience = audience;
        this.expiresAt = expiresAt;
        this.nonce = nonce;
        this.name = name;
        this.picture = picture;
        this.email = email;
    }

    OAuthUserProfile toUserProfile(
            String expectedAudience,
            String expectedNonce,
            Instant now) {
        if (!LINE_ISSUER.equals(issuer)
                || !expectedAudience.equals(audience)
                || expiresAt == null
                || expiresAt <= now.getEpochSecond()
                || !secureEquals(expectedNonce, nonce)) {
            throw new IllegalArgumentException(
                    "LINE ID token verification response is invalid"
            );
        }
        return new OAuthUserProfile(
                OAuthProvider.LINE,
                subject,
                email,
                name,
                picture
        );
    }

    private static boolean secureEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8)
        );
    }
}
