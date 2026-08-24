package me.nawa.journey.dto.response;

import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JourneyItemExistsResponse {

    @ApiModelProperty(
        value = "같은 Journey에 같은 항목·방문 날짜 조합이 이미 있는지 여부",
        example = "false"
    )
    private boolean exists;

    @ApiModelProperty(
        value = "그 자리에 다른 약속이 이미 걸려 있는지 여부. 담아만 둔 자리는 "
            + "약속 항목으로 승격되므로 exists가 true여도 이 값은 false일 수 있다",
        example = "false"
    )
    private boolean appointmentLinked;
}
