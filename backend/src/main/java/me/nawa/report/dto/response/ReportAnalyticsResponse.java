package me.nawa.report.dto.response;

import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ReportAnalyticsResponse {

    private BigDecimal totalSpent;
    private BigDecimal dailyAverage;
    private List<ReportCategoryBreakdownResponse> categoryBreakdown;
    private List<ReportDailyTrendResponse> dailyTrend;
}
