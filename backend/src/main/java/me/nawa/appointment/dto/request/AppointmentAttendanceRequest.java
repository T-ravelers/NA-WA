package me.nawa.appointment.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import me.nawa.deposit.domain.AttendanceStatus;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class AppointmentAttendanceRequest {
    private List<MemberAttendance> members;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class MemberAttendance {
        private Long memberId;
        private AttendanceStatus attendanceStatus;
    }
}
