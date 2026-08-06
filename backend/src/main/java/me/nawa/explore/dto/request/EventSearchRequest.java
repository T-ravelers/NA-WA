package me.nawa.explore.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class EventSearchRequest {

    private List<Long> sectorIds;
    private List<Long> activityIds;

    private String region1;
    private String region2;
    private String region3;
    private String keyword;

    private String sort = "LATEST";
    private String language = "en";
    private int page = 0;
    private int size = 20;
}
