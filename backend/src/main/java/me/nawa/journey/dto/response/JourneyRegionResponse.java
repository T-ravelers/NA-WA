package me.nawa.journey.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JourneyRegionResponse {

    private String regionCode;
    private String regionName;
    private Integer displayOrder;
}
