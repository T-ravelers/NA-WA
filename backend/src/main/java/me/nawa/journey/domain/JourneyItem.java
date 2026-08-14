package me.nawa.journey.domain;

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
public class JourneyItem {

    private Long tripItemId;
    private Long tripId;
    private Long itemId;
    private String itemType;
    private LocalDate visitDate;
    private String tripItemStatus;
    private Integer displayOrder;
    private String note;
    private Long appointmentId;
    private Long appointmentHostMemberId;
    private LocalDateTime confirmedAt;
    private LocalDateTime createdAt;
}
