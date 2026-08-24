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
     * place 테이블에는 기간 컬럼이 없어 PLACE 행은 LEFT JOIN 때문에 둘 다 null로 온다.
     * 기간 검사는 반드시 itemType으로 먼저 분기해야 한다.
     *
     * chk_event_period가 `is_permanent = TRUE ⟺ end_date IS NULL`을 보장하므로 상시
     * 이벤트의 운영 기간은 무제한이 아니라 [startDate, ∞)다. is_permanent 컬럼을 따로
     * 나르지 않는 것은 그래서다 — endDate가 null인 것이 곧 상시다.
     */
    private LocalDate startDate;
    private LocalDate endDate;

    /**
     * 이 항목을 그 날짜에 방문할 수 있는지. 여정 담기와 약속 생성이 **같은 규칙을**
     * 써야 해서 서비스가 아니라 여기에 둔다 — 한쪽에만 두면 다른 쪽이 조용히
     * 어긋난다(약속 생성이 실제로 그랬다).
     *
     * PLACE는 기간 개념이 없어 항상 참이다. 상시 이벤트는 `endDate`가 null이라
     * 상한만 없고 하한(`startDate`)은 그대로 적용된다.
     */
    public boolean coversVisitDate(LocalDate visitDate) {
        if (!"EVENT".equals(itemType)) {
            return true;
        }
        if (startDate != null && visitDate.isBefore(startDate)) {
            return false;
        }
        return endDate == null || !visitDate.isAfter(endDate);
    }
}
