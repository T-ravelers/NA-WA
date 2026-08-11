package me.nawa.report.dto.response;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ReportCategoryBreakdownResponse {

    private String category;
    private BigDecimal amount;
    private BigDecimal percentage;
}
