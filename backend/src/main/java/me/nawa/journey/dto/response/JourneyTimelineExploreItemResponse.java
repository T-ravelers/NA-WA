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
public class JourneyTimelineExploreItemResponse {

    private String itemType;
    private String title;
    private String thumbnailUrl;
    private List<String> imageUrls;
    private JourneyTimelineLocationResponse location;
}
