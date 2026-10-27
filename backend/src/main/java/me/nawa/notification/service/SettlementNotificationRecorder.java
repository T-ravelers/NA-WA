package me.nawa.notification.service;

import java.util.List;
import me.nawa.notification.domain.Notification;

/**
 * 정산에서 일어난 일을 알림 줄로 바꿔 적는 계약이다.
 *
 * 적는 일은 호출한 쪽의 트랜잭션과 따로 자기 트랜잭션에서 하고, 메서드가 돌아올 때는 이미
 * 커밋돼 있다. 돌려주는 알림은 그렇게 커밋된 줄이므로 호출한 쪽은 이 값으로 푸시 같은 후속
 * 동작을 이어도 된다. 적재가 실패하면 값이 아니라 예외가 나오니 후속 동작도 일어나지 않는다.
 * 적을 알림이 없으면 빈 목록을 돌려준다.
 */
public interface SettlementNotificationRecorder {

    /** 아직 내지 않은 구성원 전원에게, 각자 낼 금액으로 알린다. */
    List<Notification> recordRequested(Long settlementId);

    /** 돈을 받을 사람에게, 방금 낸 사람의 이름과 그 금액으로 알린다. */
    List<Notification> recordPaid(Long settlementId, Long paidByMemberId);

    /** 참여자 전원에게 정산 총액으로 알린다. */
    List<Notification> recordCompleted(Long settlementId);
}
