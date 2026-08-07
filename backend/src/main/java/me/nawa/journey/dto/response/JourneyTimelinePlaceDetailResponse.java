package me.nawa.journey.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JourneyTimelinePlaceDetailResponse {

    private String placeKind;
    private String addressDetail;
    private String menuSummary;
    private Boolean isActive;
}
