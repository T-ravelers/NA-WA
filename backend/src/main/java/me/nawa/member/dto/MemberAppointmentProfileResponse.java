package me.nawa.member.dto;

import lombok.Getter;

@Getter
public class MemberAppointmentProfileResponse {
    private final long memberId;
    private final String displayName;
    private final String profileImageUrl;
    private final String preferredLanguage;
    private final Integer completionRate;
    private final int noShowCount;
    private final Double averageRating;
    private final int reviewCount;

    public MemberAppointmentProfileResponse(
            long memberId, String displayName, String profileImageUrl,
            String preferredLanguage, Integer completionRate, int noShowCount,
            Double averageRating, int reviewCount) {
        this.memberId = memberId;
        this.displayName = displayName;
        this.profileImageUrl = profileImageUrl;
        this.preferredLanguage = preferredLanguage;
        this.completionRate = completionRate;
        this.noShowCount = noShowCount;
        this.averageRating = averageRating;
        this.reviewCount = reviewCount;
    }
}
