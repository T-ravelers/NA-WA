package me.nawa.member.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

@Getter
public class MemberAppointmentProfileResponse {
    private final long memberId;
    private final String displayName;
    private final String profileImageUrl;
    private final String preferredLanguage;
    /**
     * 출석 지표 두 개는 본인 조회에만 담는다. 누구에게 공개할지 아직 정하지 않았기 때문이다.
     * 타인 조회에서는 null로 비우고 응답에서 키 자체를 뺀다(#580).
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final Integer completionRate;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final Integer noShowCount;
    private final Double averageRating;
    private final int reviewCount;

    public MemberAppointmentProfileResponse(
            long memberId, String displayName, String profileImageUrl,
            String preferredLanguage, Integer completionRate, Integer noShowCount,
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

    /** 타인에게 보여 줄 응답. 출석 지표만 비운다. */
    public MemberAppointmentProfileResponse withoutAttendance() {
        return new MemberAppointmentProfileResponse(
                memberId, displayName, profileImageUrl, preferredLanguage,
                null, null, averageRating, reviewCount);
    }
}
