package me.nawa.auth.profile;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AuthMemberProfile {
    private long memberId;
    private String displayName;
    private String profileImageUrl;
    private String preferredLanguage;
    private String preferredCurrencyCode;
    private String memberStatus;
    private boolean onboardingCompleted;
    private boolean deleted;

    public static AuthMemberProfile active(
            long memberId,
            boolean onboardingCompleted) {
        AuthMemberProfile profile = new AuthMemberProfile();
        profile.memberId = memberId;
        profile.displayName = "여행자";
        profile.preferredLanguage = "en";
        profile.memberStatus = "ACTIVE";
        profile.onboardingCompleted = onboardingCompleted;
        return profile;
    }
}
