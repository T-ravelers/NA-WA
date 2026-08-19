package me.nawa.explore.dto.request;

import io.swagger.annotations.ApiModelProperty;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class PlaceSearchRequest {

    @ApiModelProperty("Sector ID 목록. 같은 필터 안에서는 OR로 결합")
    private List<Long> sectorIds;
    @ApiModelProperty("Activity ID 목록. 같은 필터 안에서는 OR로 결합")
    private List<Long> activityIds;
    @ApiModelProperty("RESTAURANT, CAFE, MARKET, BEAUTY, ETC")
    private List<String> placeKinds;
    @ApiModelProperty("광역 지역 목록")
    private List<String> region1;
    @ApiModelProperty("기초 지역 목록")
    private List<String> region2;
    @ApiModelProperty("region1 안에서 region2가 비어 있거나 분류되지 않은 Place 포함")
    private Boolean region2Other;
    @ApiModelProperty(hidden = true)
    private List<String> knownRegion2Values;
    @ApiModelProperty("세부 지역 목록")
    private List<String> region3;
    @ApiModelProperty("이름, 브랜드, 지점, 주소 부분 일치 검색어")
    private String keyword;
    private Boolean hasForeignLang;
    private Boolean hasParking;
    private Boolean reservable;
    private Boolean takeoutAvailable;
    private Boolean cardPaymentAvailable;
    private Boolean smokeFree;
    private Boolean kidFacility;
    private Boolean hasRestroom;
    private Boolean savedOnly;

    @ApiModelProperty("NEWEST 또는 POPULAR. 기본 POPULAR")
    private String sort = "POPULAR";
    @ApiModelProperty("Activity/Sector 이름 언어. en이면 영문, 그 외에는 한글")
    private String language = "en";
    @ApiModelProperty("0부터 시작하는 페이지 번호")
    private int page = 0;
    @ApiModelProperty("페이지 크기. 기본 20, 최대 100")
    private int size = 20;
}
