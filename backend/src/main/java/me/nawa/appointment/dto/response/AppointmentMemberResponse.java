package me.nawa.appointment.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import me.nawa.appointment.domain.MembershipStatus;
import me.nawa.deposit.domain.AttendanceStatus;

@Getter
@Builder
@AllArgsConstructor
public class AppointmentMemberResponse {
    private final Long appointmentMemberId;
    private final Long memberId;
    private final String displayName;
    private final String profileImageUrl;
    private final String preferredLanguage;
    private final MembershipStatus membershipStatus;
    private final AttendanceStatus attendanceStatus;
    @JsonProperty("isHost")
    private final boolean isHost;
}
