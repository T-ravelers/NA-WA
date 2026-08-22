package me.nawa.appointment.dto.request;

import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 약속 참여 요청. 방문 날짜는 받지 않는다 — 약속이 이미 활동 날짜를 갖고 있어서
 * 참여자가 고를 여지가 없다. 고르는 것은 "그 날짜를 어느 여정에 넣을지"뿐이다.
 */
@Getter
@Setter
@NoArgsConstructor
public class AppointmentJoinRequest {

    @ApiModelProperty(value = "약속을 넣을 여정 ID", required = true)
    private Long tripId;
}
