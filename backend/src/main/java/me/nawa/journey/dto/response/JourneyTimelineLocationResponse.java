package me.nawa.journey.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JourneyTimelineLocationResponse {

    private String region1;
    private String region2;
    private String region3;
    private String addressRoad;
    private String addressDetail;
    private BigDecimal latitude;
    private BigDecimal longitude;
}
