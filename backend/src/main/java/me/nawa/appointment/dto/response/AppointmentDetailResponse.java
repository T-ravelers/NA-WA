package me.nawa.appointment.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import me.nawa.appointment.domain.AppointmentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class AppointmentDetailResponse {
    private final Long appointmentId;
    private final Long itemId;
    private final String itemType;
    private final String appointmentName;
    private final String languageCode;
    private final Integer maxMembers;
    private final Integer currentMemberCount;
    private final BigDecimal depositAmount;
    private final AppointmentStatus appointmentStatus;
    private final String meetingPlace;
    private final String description;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private final LocalDateTime activityStartAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private final LocalDateTime activityEndAt;

    private final String hostDisplayName;
    private final List<AppointmentMemberResponse> members;
}
