package me.nawa.report.dto.response;

import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportContentJourneyResponse {

    private Long tripId;
    private String title;
    private LocalDate startDate;
    private LocalDate endDate;
}
