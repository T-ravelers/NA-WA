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
}
