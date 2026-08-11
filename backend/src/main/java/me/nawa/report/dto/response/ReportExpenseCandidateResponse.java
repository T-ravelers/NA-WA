package me.nawa.report.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ReportExpenseCandidateResponse {

    private Long transferId;
    private BigDecimal amount;
    private LocalDate occurredOn;
    private String category;
    private String memo;
    private boolean selected;
}
