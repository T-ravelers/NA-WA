package me.nawa.notification.service;

import java.util.List;
import me.nawa.notification.domain.Notification;
import me.nawa.notification.domain.SettlementReminderCriteria;

/**
 * 리마인더 묶음 하나를 자기 트랜잭션에서 끝내는 계약이다.
 *
 * 대상을 잠그고, 보냈다고 기록하고, 알림을 적는 일이 한 트랜잭션에 들어 있다. 메서드가 돌아올
 * 때는 이미 커밋돼 있으므로 호출한 쪽은 돌려받은 알림으로 푸시를 보내도 된다. 중간에 실패하면
 * 값이 아니라 예외가 나오고, 발송 기록도 알림도 남지 않는다.
 */
public interface SettlementReminderWriter {

    /**
     * 주어진 구성원 중 지금도 기준을 채우고 다른 트랜잭션이 잡지 않은 사람에게 리마인더를 적는다.
     *
     * 넘긴 번호와 처리된 사람은 같지 않을 수 있다. 그 사이에 낸 사람, 다른 인스턴스가 처리 중인
     * 사람, 지급이 진행 중인 사람은 조용히 빠진다. 적은 알림이 없으면 빈 목록을 돌려준다.
     */
    List<Notification> remind(List<Long> settlementMemberIds, SettlementReminderCriteria criteria);
}
