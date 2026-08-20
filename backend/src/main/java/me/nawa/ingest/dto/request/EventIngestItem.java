package me.nawa.ingest.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 파이프라인이 보내는 이벤트 한 건입니다.
 *
 * <p>적재 키는 pipelineId 입니다. 파이프라인 쪽 숫자 PK 는 받지 않습니다.
 * 두 DB 의 숫자 PK 가 어긋나 있어(실측: 겹치는 510건 중 330건만 일치)
 * 신뢰할 수 없기 때문입니다.
 *
 * <p>viewCount, favoriteCount, createdAt 은 앱이 소유하므로 받지 않습니다.
 */
@Getter
@Setter
@NoArgsConstructor
public class EventIngestItem {

    private String pipelineId;

    private String title;

    private String eventKind;
    private String description;
    private String venueName;
    private String thumbnailUrl;
    private String imageUrls;
    private String links;
    private String reservationUrl;
    private String preReservation;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
    private Boolean isPermanent;
    private String operatingHours;
    private String openDays;
    private Boolean openWeekend;
    private Boolean opensLate;
    private String region1;
    private String region2;
    private String addressRoad;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private Boolean hasPhotoZone;
    private Boolean isExperience;
    private String ageLimit;
    private String contact;
    private String organizer;
    private Boolean isFree;
    private String priceText;
}
