package me.nawa.explore.dto.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import me.nawa.explore.domain.EventStatus;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventDetailResponse {

    private Long eventId;
    private String eventType;
    private String eventKind;

    private String title;
    private String subtitle;
    private String description;
    private String programText;
    private String thumbnailUrl;
    private JsonNode imageUrls;
    private JsonNode links;
    private String reservationUrl;
    private JsonNode preReservation;

    private EventStatus status;
    private Boolean isPermanent;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate endDate;

    private JsonNode operatingHours;
    private JsonNode openDays;
    private Boolean openWeekend;
    private Boolean opensLate;

    private String venueName;
    private String region1;
    private String region2;
    private String region3;
    private String addressRoad;
    private BigDecimal latitude;
    private BigDecimal longitude;

    private Boolean hasPhotoZone;
    private Boolean isExperience;
    private String ageLimit;
    private Boolean isFree;
    private String priceText;
    private Boolean hasBenefit;
    private Boolean reservable;
    private String contact;
    private String organizer;

    private List<EventActivityResponse> activities;
}
