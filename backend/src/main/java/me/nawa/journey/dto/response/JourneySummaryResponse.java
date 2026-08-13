package me.nawa.journey.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModelProperty;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 탐색 항목을 추가할 때 선택할 수 있는 여정의 요약 정보입니다. */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JourneySummaryResponse {

    private Long tripId;
    private String title;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate endDate;

    @ApiModelProperty(
        value = "여정에 담긴 EVENT 항목 수 (원본이 삭제된 항목은 제외)",
        example = "3"
    )
    private long eventCount;

    @ApiModelProperty(
        value = "여정에 담긴 PLACE 항목 수 (원본이 삭제된 항목은 제외)",
        example = "5"
    )
    private long placeCount;
}
