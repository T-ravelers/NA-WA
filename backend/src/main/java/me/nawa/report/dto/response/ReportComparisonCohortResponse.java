package me.nawa.report.dto.response;

import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

/** 비교 대상 전체의 평균. {@code size}가 0이면 나머지는 0과 빈 목록이다. */
@Getter
@Builder
public class ReportComparisonCohortResponse {

    private int size;
    private BigDecimal avgTotalSpent;
    private BigDecimal avgDailyAverage;
    private List<ReportCategoryBreakdownResponse> categoryBreakdown;
}
