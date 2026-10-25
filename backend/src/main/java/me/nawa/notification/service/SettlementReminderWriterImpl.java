package me.nawa.notification.service;

import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import me.nawa.notification.domain.Notification;
import me.nawa.notification.domain.SettlementReminderCriteria;
import me.nawa.notification.domain.SettlementReminderTarget;
import me.nawa.notification.mapper.NotificationMapper;
import me.nawa.notification.mapper.SettlementReminderMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 리마인더 묶음 하나를 "잠금 → 발송 기록 → 알림 적재"까지 한 트랜잭션에서 처리한다.
 *
 * <p><b>선점과 적재가 한 트랜잭션인 이유.</b> 발송 기록과 알림을 따로 커밋하면 사이에서 서버가
 * 멈췄을 때 "기록은 있는데 알림이 없는" 사람이 생기고, 다음 실행은 그 사람이 이미 받았다고 보고
 * 건너뛴다. 거꾸로 알림을 먼저 커밋하면 같은 사람에게 두 번 간다. 한 트랜잭션이면 둘이 함께
 * 남거나 함께 없다.
 *
 * <p><b>푸시는 여기서 보내지 않는다.</b> 커밋되기 전에 보낸 푸시는 트랜잭션이 되돌아가도 거둘 수
 * 없다. 호출한 쪽이 돌려받은 알림, 곧 커밋된 줄로만 푸시를 보낸다.
 *
 * <p>{@code REQUIRES_NEW}인 것은 바깥 트랜잭션이 있어도 이 묶음이 따로 커밋되게 하기 위해서다.
 * 묶음마다 끝나야 앞 묶음이 롤백되지 않고, 실패한 묶음만 다시 시도할 수 있다. {@code
 * READ_COMMITTED}인 것은 잠금 읽기가 훑은 범위에 갭 잠금을 잡지 않게 하기 위해서다. 기본 격리
 * 수준(REPEATABLE READ)에서는 그 사이에 새 정산을 만드는 INSERT가 막힐 수 있다.
 */
@Service
@RequiredArgsConstructor
public class SettlementReminderWriterImpl implements SettlementReminderWriter {

    private static final String REMINDER = "SETTLEMENT_REMINDER";

    private final SettlementReminderMapper reminderMapper;
    private final NotificationMapper notificationMapper;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public List<Notification> remind(List<Long> settlementMemberIds, SettlementReminderCriteria criteria) {
        List<SettlementReminderTarget> targets = reminderMapper.lockTargets(criteria, settlementMemberIds);
        if (targets.isEmpty()) {
            return List.of();
        }
        // 넘겨받은 번호가 아니라 실제로 잠근 사람만 기록한다. 그 사이에 낸 사람이나 다른 곳이
        // 쥔 사람이 빠져 있다.
        reminderMapper.markReminded(
            targets.stream().map(SettlementReminderTarget::getSettlementMemberId).toList(),
            criteria.getNow()
        );
        List<Notification> notifications = targets.stream()
            .map(target -> notification(target, criteria.getNow()))
            .toList();
        notificationMapper.insertNotifications(notifications);
        return notifications;
    }

    /**
     * 알림은 만들 때 값을 복사해 둔다(NOTIFICATION_API.md). 이름은 돈을 받을 원결제자이고 금액은
     * 받는 사람이 낼 몫이다. 정산 요청 알림과 담는 값이 같다.
     */
    private Notification notification(SettlementReminderTarget target, LocalDateTime createdAt) {
        return Notification.builder()
            .recipientMemberId(target.getRecipientMemberId())
            .notificationType(REMINDER)
            .settlementId(target.getSettlementId())
            .actorName(target.getPayerName())
            .gatheringName(target.getGatheringName())
            .amount(target.getShareAmount())
            .currencyCode(target.getCurrencyCode())
            .createdAt(createdAt)
            .build();
    }
}
