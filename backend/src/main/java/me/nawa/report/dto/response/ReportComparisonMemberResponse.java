package me.nawa.report.dto.response;

import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

/** 비교 대상 한 명의 지출. 나와 동료가 같은 형태다. */
@Getter
@Builder
public class ReportComparisonMemberResponse {

    private Long memberId;
    private String displayName;
    private String profileImageUrl;
    private BigDecimal totalSpent;
    private BigDecimal dailyAverage;
    private List<ReportCategoryBreakdownResponse> categoryBreakdown;
}
