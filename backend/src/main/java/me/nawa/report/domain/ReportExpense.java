package me.nawa.report.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportExpense {

    private Long transferId;
    private Long ledgerEntryId;
    private BigDecimal amount;
    private LocalDate occurredOn;
    private String category;
    private String memo;
    private boolean selected;
}
