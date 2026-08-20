package me.nawa.ingest.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 장소의 한 언어 번역입니다.
 *
 * <p>이벤트와 컬럼 구성이 다릅니다. 장소는 브랜드와 지점명이 따로 있고
 * 운영시간·휴무일이 원문 텍스트로 들어갑니다.
 *
 * <p>본체가 아직 없으면 이 건은 건너뜁니다.
 */
@Getter
@Setter
@NoArgsConstructor
public class PlaceTranslationIngestItem {

    private String pipelineId;

    private String languageCode;

    private String name;
    private String brand;
    private String branch;
    private String addressDisplay;
    private String addressDetail;
    private String openingHoursText;
    private String closedDaysText;
    private String menuSummary;
}
