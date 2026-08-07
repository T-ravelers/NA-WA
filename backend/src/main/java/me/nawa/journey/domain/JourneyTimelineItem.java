package me.nawa.journey.domain;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
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
public class JourneyTimelineItem {

    private Long tripItemId;
    private Long itemId;
    private Long appointmentId;
    private LocalDate visitDate;
    private String status;
    private Integer displayOrder;
    private String note;
    private String itemType;
    private String title;
    private String thumbnailUrl;
    private JsonNode imageUrls;
    private String region1;
    private String region2;
    private String region3;
    private String addressRoad;
    private String addressDetail;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String eventKind;
    private LocalDate eventStartDate;
    private LocalDate eventEndDate;
    private String organizer;
    private String eventReservationUrl;
    private String venueName;
    private String placeKind;
    private String menuSummary;
    private Boolean placeActive;
    private LocalDateTime activityStartAt;
    private LocalDateTime activityEndAt;
    private String appointmentStatus;
}
