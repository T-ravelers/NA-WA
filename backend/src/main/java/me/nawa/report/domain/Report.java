package me.nawa.report.domain;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Report {

    private Long reportId;
    private Long tripId;
    private Long memberId;
    private String title;
    private LocalDate startDate;
    private LocalDate endDate;
    private String generationStatus;
    private String locale;
    private JsonNode reportContent;
    private LocalDateTime generatedAt;
    private LocalDateTime createdAt;
}
