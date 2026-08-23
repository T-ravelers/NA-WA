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

    /** 타임라인에서 가장 먼저 나오는, 썸네일이 있는 항목의 사진 주소. 없으면 null이다. */
    private String coverImageUrl;
}
