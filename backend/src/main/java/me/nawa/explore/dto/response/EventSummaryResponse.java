package me.nawa.explore.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import me.nawa.explore.domain.EventStatus;

@Getter
@Builder
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EventSummaryResponse {

    private Long itemId;
    private String eventKind;
    private EventStatus status;
    private String title;
    private String subtitle;
    private String thumbnailUrl;
    private String region1;
    private String region2;
    private String region3;
    private BigDecimal latitude;
    private BigDecimal longitude;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate startDate;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate endDate;
}
