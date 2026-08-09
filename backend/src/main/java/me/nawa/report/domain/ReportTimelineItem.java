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
public class ReportTimelineItem {

    private Long tripItemId;
    private Long itemId;
    private LocalDate visitDate;
    private String itemType;
    private String title;
    private String status;
}
