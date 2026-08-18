package me.nawa.appointment.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import me.nawa.appointment.domain.MyOngoingAppointment;

public record MyOngoingAppointmentResponse(
    Long appointmentId,
    String appointmentName,
    Long tripId,
    String meetingPlace,

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    LocalDateTime activityStartAt,

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    LocalDateTime activityEndAt,

    Long itemId,
    String itemType,
    String appointmentStatus
) {

    public static MyOngoingAppointmentResponse from(MyOngoingAppointment appointment){
        return new MyOngoingAppointmentResponse(
            appointment.getAppointmentId(),
            appointment.getAppointmentName(),
            appointment.getTripId(),
            appointment.getMeetingPlace(),
            appointment.getActivityStartAt(),
            appointment.getActivityEndAt(),
            appointment.getItemId(),
            appointment.getItemType(),
            appointment.getAppointmentStatus()
        );
    }
}
