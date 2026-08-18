package me.nawa.explore.dto.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
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
public class PlaceSummaryResponse {

    private Long itemId;
    private String name;
    private String brand;
    private String branch;
    private String placeKind;
    private String thumbnailUrl;
    private JsonNode imageUrls;
    private String region1;
    private String region2;
    private String region3;
    private String addressRoad;
    private String addressDetail;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private Boolean hasForeignLang;
    private Boolean hasParking;
    private Boolean reservable;
    private Boolean takeoutAvailable;
    private Boolean cardPaymentAvailable;
    private Boolean smokeFree;
    private Boolean kidFacility;
    private Boolean hasRestroom;
    private Boolean isActive;
    private long viewCount;
    private long favoriteCount;
    private boolean saved;
    @JsonIgnore
    private JsonNode openingHours;
    @JsonIgnore
    private JsonNode closedDays;
}
