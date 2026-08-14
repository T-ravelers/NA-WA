package me.nawa.appointment.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class AppointmentCreateRequest {
    private Long itemId;

    @ApiModelProperty(value = "EVENT 또는 PLACE", required = true)
    private String itemType;

    @ApiModelProperty(value = "en, ja, zh-TW, vi", required = true)
    private String languageCode;

    private String appointmentName;
    private Integer maxMembers;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime joinDeadline;

    private BigDecimal depositAmount;
    private String meetingPlace;
    private String meetingAddress;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime activityStartAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime activityEndAt;
}
