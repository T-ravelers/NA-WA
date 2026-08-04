package me.nawa.explore.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventDetailResponse {

    private Long eventId;
    private String eventType;

    private String title;
    private String subtitle;
    private String description;
    private String programText;
    private String thumbnailUrl;

    private String status;
    private Boolean isPermanent;

    private LocalDate startDate;
    private LocalDate endDate;

    private String operatingHours;
    private String openDays;
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

    private List<EventActivityResponse> activities;
}
