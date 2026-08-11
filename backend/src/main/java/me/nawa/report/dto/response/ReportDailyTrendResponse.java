package me.nawa.report.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ReportDailyTrendResponse {

    private LocalDate date;
    private BigDecimal amount;
}
