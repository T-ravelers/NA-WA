package me.nawa.report.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportSummaryResponse {

    private Long reportId;
    private Long tripId;
    private String title;
    private LocalDate startDate;
    private LocalDate endDate;
    private String generationStatus;
    private String locale;
    private LocalDateTime generatedAt;
    private LocalDateTime createdAt;
}
