package me.nawa.appointment.domain;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class MyOngoingAppointment {
    private Long appointmentId;
    private String appointmentName;
    private Long tripId;
    private String meetingPlace;
    private LocalDateTime activityStartAt;
    private LocalDateTime activityEndAt;
}
