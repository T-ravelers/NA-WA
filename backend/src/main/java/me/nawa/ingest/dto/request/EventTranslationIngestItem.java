package me.nawa.ingest.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 이벤트의 한 언어 번역입니다.
 *
 * <p>본체가 아직 없으면 이 건은 건너뜁니다. 번역만 먼저 들어와 고아가 되는
 * 것보다, 본체가 들어온 다음 회차에 함께 붙는 편이 낫습니다.
 */
@Getter
@Setter
@NoArgsConstructor
public class EventTranslationIngestItem {

    private String pipelineId;

    private String languageCode;

    private String title;
    private String description;
    private String operatingHours;
    private String addressDisplay;
    private String venueDetail;
    private String ageLimit;
    private String organizer;
    private String priceText;
}
