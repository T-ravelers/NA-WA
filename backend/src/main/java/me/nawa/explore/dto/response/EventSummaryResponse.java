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
     * 종료일을 받지 못했는지 여부. 참이면 endDate가 없습니다(DB 불변식 chk_event_period).
     *
     * <p>"상시 운영"이 아닙니다. 적재가 종료일을 못 채우면 참이 되므로 축제·팝업·콘서트도
     * 여기 들어옵니다. 화면은 이 값을 "상시"로 옮기지 말고 끝을 모른다는 사실만 적어야
     * 합니다. 이 값 없이 startDate만 그리면 "그 하루짜리 지난 행사"로 읽힙니다.
     */
    private Boolean isPermanent;

    private boolean saved;
}
