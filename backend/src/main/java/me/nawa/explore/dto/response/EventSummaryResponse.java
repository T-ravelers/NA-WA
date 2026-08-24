package me.nawa.explore.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import me.nawa.explore.domain.EventStatus;

@Getter
@Builder
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EventSummaryResponse {

    private Long itemId;
    private String eventKind;
    private EventStatus status;
    private String title;
    private String subtitle;
    private String thumbnailUrl;
    private String region1;
    private String region2;
    private String region3;
    private BigDecimal latitude;
    private BigDecimal longitude;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate startDate;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate endDate;

    /**
     * 상시 Event 여부. 참이면 endDate가 없습니다(DB 불변식 chk_event_period).
     *
     * <p>카드가 이 값 없이 startDate만 그리면 상시 Event가 "그 하루짜리 지난 행사"로
     * 읽힙니다. 상세는 기간 자리에 Permanent를 적고 있어 둘이 다른 말을 했습니다.
     */
    private Boolean isPermanent;

    private boolean saved;
}
