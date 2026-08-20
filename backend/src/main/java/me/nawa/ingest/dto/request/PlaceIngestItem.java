package me.nawa.ingest.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 파이프라인이 보내는 장소 한 건입니다. 적재 키는 pipelineId 입니다.
 */
@Getter
@Setter
@NoArgsConstructor
public class PlaceIngestItem {

    private String pipelineId;

    private String name;

    private String brand;
    private String branch;
    private String placeKind;
    private String thumbnailUrl;
    private String imageUrls;
    private String region1;
    private String region2;
    private String addressRoad;
    private String addressDetail;
    private String postalCode;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String openingHours;
    private String closedDays;
    private String tel;
    private Boolean hasParking;
    private Boolean reservable;
    private Boolean takeoutAvailable;
    private Boolean hasRestroom;
    private String menuSummary;
    private Boolean isActive;
}
