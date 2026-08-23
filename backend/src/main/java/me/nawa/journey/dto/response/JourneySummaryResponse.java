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

    @ApiModelProperty(
        value = "여정 커버 사진 주소. 타임라인에서 가장 먼저 나오는, 썸네일이 있는 항목의 사진입니다. "
            + "담긴 항목이 없거나 모두 썸네일이 없으면 null입니다",
        example = "https://cdn.example.com/events/301.jpg"
    )
    private String coverImageUrl;
}
