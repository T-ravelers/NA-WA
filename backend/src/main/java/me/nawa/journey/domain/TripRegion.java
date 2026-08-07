package me.nawa.journey.domain;

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
public class TripRegion {

    private Long tripId;
    private String regionCode;
    private String regionName;
    private Integer displayOrder;
}
