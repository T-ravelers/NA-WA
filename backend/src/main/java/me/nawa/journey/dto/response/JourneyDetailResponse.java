package me.nawa.journey.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModelProperty;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JourneyDetailResponse {

    private Long tripId;
    private String title;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate endDate;

    @ApiModelProperty(value = "원화 기준 예산", example = "1800000")
    private BigDecimal budgetAmount;

    @ApiModelProperty(
        value = "여정 기간 내 완료된 원화 QR 결제·정산 지출 합계",
        example = "1284500"
    )
    private BigDecimal spentAmount;

    private String companionPreference;
    private List<JourneyRegionResponse> regions;
}
