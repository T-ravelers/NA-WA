package me.nawa.appointment.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import me.nawa.appointment.domain.MembershipStatus;
import me.nawa.deposit.domain.AttendanceStatus;

@Getter
@Builder
@AllArgsConstructor
public class AppointmentParticipationResponse {
    private final boolean joined;
    private final Long appointmentMemberId;
    private final MembershipStatus membershipStatus;
    private final AttendanceStatus attendanceStatus;
    private final boolean host;

    public static AppointmentParticipationResponse notJoined() {
        return AppointmentParticipationResponse.builder()
                .joined(false)
                .host(false)
                .build();
    }
}
