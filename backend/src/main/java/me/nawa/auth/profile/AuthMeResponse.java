package me.nawa.auth.profile;

import lombok.Getter;

@Getter
public class AuthMeResponse {
    private final long memberId;
    private final String displayName;
    private final String profileImageUrl;
    private final String preferredLanguage;
    private final String preferredCurrencyCode;
    private final boolean onboardingRequired;

    public AuthMeResponse(AuthMemberProfile profile) {
        this.memberId = profile.getMemberId();
        this.displayName = profile.getDisplayName();
        this.profileImageUrl = profile.getProfileImageUrl();
        this.preferredLanguage = profile.getPreferredLanguage();
        this.preferredCurrencyCode = profile.getPreferredCurrencyCode();
        this.onboardingRequired = !profile.isOnboardingCompleted();
    }
}
