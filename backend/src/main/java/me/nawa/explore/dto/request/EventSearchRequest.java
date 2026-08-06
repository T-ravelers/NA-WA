package me.nawa.explore.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
    private List<String> region3;
    private String keyword;

    private String datePreset;
    private LocalDate startDate;
    private LocalDate endDate;

    private Boolean freeOnly;
    private Boolean openWeekendOnly;
    private Boolean opensLateOnly;
    private Boolean preReservationOnly;
    private Boolean experienceOnly;
    private Boolean photoZoneOnly;
    private Boolean savedOnly;

    private String sort = "LATEST";
    private String language = "en";
    private int page = 0;
    private int size = 20;
}
