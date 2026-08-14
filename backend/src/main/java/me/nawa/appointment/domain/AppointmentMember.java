package me.nawa.appointment.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import me.nawa.deposit.domain.AttendanceStatus;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentMember {
    private Long appointmentMemberId;
    private Long appointmentId;
    private Long memberId;
    private Long tripId;
    private String displayName;
    private String profileImageUrl;
    private String preferredLanguage;
    private MembershipStatus membershipStatus;
    private AttendanceStatus attendanceStatus;
    private Boolean host;
    private LocalDateTime joinedAt;
    private LocalDateTime leftAt;
    private LocalDateTime attendanceConfirmedAt;
}
