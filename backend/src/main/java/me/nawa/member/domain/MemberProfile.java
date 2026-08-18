package me.nawa.member.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class MemberProfile {
    private long memberId;
    private String displayName;
    private String profileImageUrl;
    private String nationalityCode;
    private String preferredLanguage;
    private String preferredCurrencyCode;
    private String memberStatus;
    private boolean onboardingCompleted;
    private boolean deleted;
}
