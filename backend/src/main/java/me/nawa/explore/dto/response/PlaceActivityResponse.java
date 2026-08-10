package me.nawa.explore.dto.response;

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
public class PlaceActivityResponse {

    private Long activityId;
    private String activityCode;
    private String activityName;
    private Long sectorId;
    private String sectorCode;
    private String sectorName;
    private Boolean isPrimary;
}
