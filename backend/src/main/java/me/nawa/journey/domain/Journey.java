package me.nawa.journey.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
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
public class Journey {

    private Long tripId;
    private Long memberId;
    private String title;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal budgetAmount;
    private String companionPreference;
    private long eventCount;
    private long placeCount;
}
