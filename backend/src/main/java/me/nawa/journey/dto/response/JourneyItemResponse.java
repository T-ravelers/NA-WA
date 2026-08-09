package me.nawa.journey.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JourneyItemResponse {

    private Long tripItemId;
    private Long journeyId;
    private Long itemId;
    private String itemType;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate visitDate;

    private String tripItemStatus;
    private Integer displayOrder;
    private String note;
    private Long appointmentId;
    private LocalDateTime confirmedAt;
    private LocalDateTime createdAt;
}
