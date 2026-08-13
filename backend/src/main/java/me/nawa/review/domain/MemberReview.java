package me.nawa.review.domain;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberReview {
    private Long reviewId;
    private Long appointmentId;
    private Long reviewerAppointmentMemberId;
    private Long reviewedAppointmentMemberId;
}
