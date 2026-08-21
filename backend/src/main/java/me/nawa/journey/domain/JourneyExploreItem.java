package me.nawa.journey.domain;

import java.time.LocalDate;
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
public class JourneyExploreItem {

    private Long itemId;
    private String itemType;

    /*
     * 항목 자체의 운영 기간. EVENT 행에서만 채워진다.
     *
     * place 테이블에는 기간 컬럼이 없어 PLACE 행은 LEFT JOIN 때문에 셋 다 null로 온다.
     * 기간 검사는 반드시 itemType으로 먼저 분기해야 한다.
     *
     * chk_event_period가 `is_permanent = TRUE ⟺ end_date IS NULL`을 보장하므로 상시
     * 이벤트의 운영 기간은 무제한이 아니라 [startDate, ∞)다. is_permanent 컬럼을 따로
     * 나르지 않는 것은 그래서다 — endDate가 null인 것이 곧 상시다.
     */
    private LocalDate startDate;
    private LocalDate endDate;
}
