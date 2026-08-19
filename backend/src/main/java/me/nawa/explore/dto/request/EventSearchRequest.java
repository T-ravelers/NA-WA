package me.nawa.explore.dto.request;

import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class EventSearchRequest {

    private List<Long> sectorIds;
    private List<Long> activityIds;
    private List<String> eventKinds;

    private List<String> region1;
    private List<String> region2;
    private Boolean region2Other;
    @ApiModelProperty(hidden = true)
    private List<String> knownRegion2Values;
    private List<String> region3;
    private String keyword;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;

    private Boolean freeOnly;
    private Boolean openWeekendOnly;
    private Boolean opensLateOnly;
    private Boolean preReservationOnly;
    private Boolean experienceOnly;
    private Boolean photoZoneOnly;
    private Boolean savedOnly;

    private String sort = "NEWEST";
    private String language = "en";
    private int page = 0;
    private int size = 20;
}
