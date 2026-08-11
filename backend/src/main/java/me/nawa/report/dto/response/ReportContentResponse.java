package me.nawa.report.dto.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportContentResponse {

    private ReportContentJourneyResponse journey;
    private List<ReportContentDayResponse> days;
}
