package me.nawa.notification.domain;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 리마인더 실행 한 번이 대상을 가르는 기준 시각과 횟수다.
 *
 * 실행이 시작될 때 한 번 계산해 모든 묶음이 같은 값으로 판단한다. 값을 어떻게 셀지(며칠째에
 * 첫 발송인지, 간격은 며칠인지)는 {@code SettlementReminderServiceImpl}이 정하고, 이 클래스와
 * 쿼리는 받은 값을 그대로 비교만 한다.
 */
@Getter
@RequiredArgsConstructor
public class SettlementReminderCriteria {

    /** 이번 실행이 발송 기록과 알림에 적는 시각. DB 시계가 아니라 앱이 넘긴 값이다. */
    private final LocalDateTime now;

    /** 정산을 만든 시각이 이보다 앞서야 첫 리마인더 대상이 된다. */
    private final LocalDateTime firstBefore;

    /** 마지막 리마인더 시각이 이보다 앞서야(또는 아직 없어야) 다시 보낼 수 있다. */
    private final LocalDateTime remindedBefore;

    /** 이 횟수에 닿은 구성원에게는 더 보내지 않는다. */
    private final int maxCount;
}
