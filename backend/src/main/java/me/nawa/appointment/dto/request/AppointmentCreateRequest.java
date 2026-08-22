package me.nawa.appointment.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
public class AppointmentCreateRequest {
    private Long itemId;

    @ApiModelProperty(value = "EVENT 또는 PLACE", required = true)
    private String itemType;

    @ApiModelProperty(value = "약속을 확정할 여정 ID", required = true)
    private Long tripId;

    @ApiModelProperty(value = "여정 안에서 활동이 이루어지는 방문 날짜", required = true)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate visitDate;

    @ApiModelProperty(value = "en, ja, zh-TW, vi", required = true)
    private String languageCode;

    private String appointmentName;
    private Integer maxMembers;

    private BigDecimal depositAmount;
    private String meetingPlace;

    @ApiModelProperty(value = "visitDate 안에서의 활동 시작 시각")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm:ss")
    private LocalTime activityStartTime;

    @ApiModelProperty(value = "visitDate 안에서의 활동 종료 시각")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm:ss")
    private LocalTime activityEndTime;
}
