package me.nawa.journey.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModelProperty;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class JourneyItemCreateRequest {

    @ApiModelProperty(value = "추가할 Explore item ID", required = true)
    private Long itemId;

    @ApiModelProperty(value = "방문 날짜", required = true, example = "2026-08-08")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate visitDate;

    @ApiModelProperty(value = "Journey 내 표시 순서", example = "0")
    private Integer displayOrder = 0;

    @ApiModelProperty(value = "일정 메모", example = "오전 방문")
    private String note;
}
