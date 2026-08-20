package me.nawa.member.dto;

import lombok.Getter;
import me.nawa.member.domain.MemberProfile;

@Getter
public class MemberProfileResponse {
    private final long memberId;
    private final String displayName;
    private final String profileImageUrl;
    private final String nationalityCode;
    private final String preferredLanguage;
    private final String preferredCurrencyCode;
    private final String accountType;
    private final boolean onboardingRequired;

    public MemberProfileResponse(MemberProfile profile) {
        this.memberId = profile.getMemberId();
        this.displayName = profile.getDisplayName();
        this.profileImageUrl = profile.getProfileImageUrl();
        this.nationalityCode = profile.getNationalityCode();
        this.preferredLanguage = profile.getPreferredLanguage();
        this.preferredCurrencyCode = profile.getPreferredCurrencyCode();
        this.accountType = profile.getAccountType();
        this.onboardingRequired = !profile.isOnboardingCompleted();
    }
}
