package me.nawa.report.dto.response;

import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportContentDayResponse {

    private LocalDate visitDate;
    private List<ReportContentItemResponse> items;
}
