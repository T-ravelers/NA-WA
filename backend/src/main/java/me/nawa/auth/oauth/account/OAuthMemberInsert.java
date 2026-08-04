package me.nawa.auth.oauth.account;

import lombok.Getter;
import lombok.Setter;
import org.springframework.util.StringUtils;

@Getter
public class OAuthMemberInsert {
    private static final String DEFAULT_DISPLAY_NAME = "여행자";
    private static final int MAX_DISPLAY_NAME_CODE_POINTS = 50;
    private static final int MAX_PROFILE_IMAGE_URL_LENGTH = 500;

    @Setter
    private long memberId;
    private final String displayName;
    private final String profileImageUrl;

    public OAuthMemberInsert(
            String displayName,
            String profileImageUrl) {
        this.displayName = normalizeDisplayName(displayName);
        this.profileImageUrl = normalizeProfileImageUrl(profileImageUrl);
    }

    private static String normalizeDisplayName(String value) {
        String normalized = StringUtils.hasText(value)
                ? value.trim()
                : DEFAULT_DISPLAY_NAME;
        int codePointCount = normalized.codePointCount(
                0,
                normalized.length()
        );
        if (codePointCount <= MAX_DISPLAY_NAME_CODE_POINTS) {
            return normalized;
        }
        int endIndex = normalized.offsetByCodePoints(
                0,
                MAX_DISPLAY_NAME_CODE_POINTS
        );
        return normalized.substring(0, endIndex);
    }

    private static String normalizeProfileImageUrl(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() <= MAX_PROFILE_IMAGE_URL_LENGTH
                ? normalized
                : null;
    }
}
