package me.nawa.auth.oauth.account;

import lombok.Getter;
import lombok.Setter;
import org.springframework.util.StringUtils;

import java.util.Locale;

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

    /**
     * 수정 경로(MemberProfileServiceImpl)와 같은 기준으로 http·https 스킴만 받는다.
     * 다만 이 값은 사용자가 아니라 OAuth 제공자가 주므로, 어긋나면 오류 대신
     * null로 떨궈 가입 자체는 막지 않는다(기본 아바타로 대체).
     */
    private static String normalizeProfileImageUrl(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        String lowerCased = normalized.toLowerCase(Locale.ROOT);
        boolean hasAllowedScheme = lowerCased.startsWith("http://")
                || lowerCased.startsWith("https://");
        if (!hasAllowedScheme
                || normalized.length() > MAX_PROFILE_IMAGE_URL_LENGTH) {
            return null;
        }
        return normalized;
    }
}
