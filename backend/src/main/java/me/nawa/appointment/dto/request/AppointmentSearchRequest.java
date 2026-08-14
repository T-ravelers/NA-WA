package me.nawa.appointment.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import me.nawa.appointment.domain.AppointmentStatus;

@Getter
@Setter
@NoArgsConstructor
public class AppointmentSearchRequest {
    private Long itemId;
    private String itemType;
    private String language;
    private String keyword;
    private AppointmentStatus status;
    private int page = 0;
    private int size = 20;
}
