package me.nawa.journey.dto.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JourneyTimelineResponse {

    private Long tripId;
    private List<JourneyTimelineDayResponse> timeline;
}
