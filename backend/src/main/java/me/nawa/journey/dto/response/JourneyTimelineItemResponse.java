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
public class JourneyTimelineItemResponse {

    private Long tripItemId;
    private Long itemId;
    private String status;
    private Integer displayOrder;
    private String note;
    private JourneyTimelineExploreItemResponse exploreItem;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private JourneyTimelineEventDetailResponse eventDetail;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private JourneyTimelinePlaceDetailResponse placeDetail;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private JourneyTimelineAppointmentResponse appointment;
}
