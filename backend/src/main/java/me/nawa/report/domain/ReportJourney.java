package me.nawa.report.domain;

import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportJourney {

    private Long tripId;
    private Long memberId;
    private String title;
    private LocalDate startDate;
    private LocalDate endDate;
}
