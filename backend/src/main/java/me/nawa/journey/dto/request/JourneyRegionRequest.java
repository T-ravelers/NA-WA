package me.nawa.journey.dto.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class JourneyRegionRequest {

    private String regionCode;
    private String regionName;
    private Integer displayOrder;
}
