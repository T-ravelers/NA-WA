package me.nawa.appointment.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Appointment {
    private Long appointmentId;
    private Long itemId;
    private String itemType;
    private Long hostMemberId;
    private String hostDisplayName;
    private String languageCode;
    private String appointmentName;
    private String appointmentDescription;
    private Integer maxMembers;
    private Integer currentMemberCount;
    private LocalDateTime joinDeadline;
    private BigDecimal depositAmount;
    private AppointmentStatus appointmentStatus;
    private String meetingPlace;
    private String meetingAddress;
    private BigDecimal meetingLatitude;
    private BigDecimal meetingLongitude;
    private LocalDateTime activityStartAt;
    private LocalDateTime activityEndAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
