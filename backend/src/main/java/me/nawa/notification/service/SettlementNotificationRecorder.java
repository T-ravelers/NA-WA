package me.nawa.notification.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import me.nawa.notification.domain.Notification;
import me.nawa.notification.domain.SettlementMemberShare;
import me.nawa.notification.domain.SettlementNotificationSnapshot;
import me.nawa.notification.mapper.NotificationMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 정산에서 일어난 일을 알림 줄로 바꿔 적는다.
 *
 * 정산 트랜잭션이 끝난 뒤에 불리므로 {@code REQUIRES_NEW}로 자기 트랜잭션을 연다. 여기서
 * 실패해도 이미 끝난 정산을 되돌리지 않는다 — 알림은 정산 상태에서 파생된 표시용 데이터라,
 * 알림을 못 적었다고 돈이 오간 사실이 사라져서는 안 된다.
 *
 * 알림 종류마다 SQL을 따로 두지 않고 구성원 목록 하나를 받아 Java에서 고른다. 누구에게
 * 얼마로 보낼지가 이 클래스 한 곳에만 있어야 규칙이 바뀔 때 고칠 자리가 분명하다.
 */
@Service
@RequiredArgsConstructor
public class SettlementNotificationRecorder {

    private static final String REQUESTED = "SETTLEMENT_REQUESTED";
    private static final String PAID = "SETTLEMENT_PAID";
    private static final String COMPLETED = "SETTLEMENT_COMPLETED";

    private final NotificationMapper notificationMapper;

    /** 아직 내지 않은 구성원 전원에게, 각자 낼 금액으로 알린다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordRequested(Long settlementId) {
        SettlementNotificationSnapshot snapshot = snapshotOf(settlementId);
        if (snapshot == null) {
            return;
        }
        List<Notification> notifications = shares(settlementId).stream()
            .filter(share -> "PENDING".equals(share.getRequestStatus()))
            .map(share -> notification(
                REQUESTED, settlementId, snapshot, share.getMemberId(),
                snapshot.getPayerName(), share.getShareAmount()
            ))
            .toList();
        insert(notifications);
    }

    /** 돈을 받을 사람에게, 방금 낸 사람의 이름과 그 금액으로 알린다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordPaid(Long settlementId, Long paidByMemberId) {
        SettlementNotificationSnapshot snapshot = snapshotOf(settlementId);
        if (snapshot == null) {
            return;
        }
        SettlementMemberShare payer = shares(settlementId).stream()
            .filter(share -> share.getMemberId().equals(paidByMemberId))
            .findFirst()
            .orElse(null);
        if (payer == null) {
            return;
        }
        insert(List.of(notification(
            PAID, settlementId, snapshot, snapshot.getPayerMemberId(),
            payer.getDisplayName(), payer.getShareAmount()
        )));
    }

    /**
     * 참여자 전원에게 정산 총액으로 알린다.
     *
     * 돈을 받을 사람은 자기 몫 행이 청구 대상이 아니라 구성원 목록에 없다. 그래서 따로
     * 넣어 준다 — 정산이 끝났다는 소식을 가장 기다린 사람이 빠지면 안 된다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordCompleted(Long settlementId) {
        SettlementNotificationSnapshot snapshot = snapshotOf(settlementId);
        if (snapshot == null) {
            return;
        }
        Set<Long> recipients = new LinkedHashSet<>();
        shares(settlementId).forEach(share -> recipients.add(share.getMemberId()));
        recipients.add(snapshot.getPayerMemberId());

        List<Notification> notifications = new ArrayList<>();
        recipients.forEach(recipient -> notifications.add(notification(
            COMPLETED, settlementId, snapshot, recipient,
            snapshot.getPayerName(), snapshot.getTotalAmount()
        )));
        insert(notifications);
    }

    private SettlementNotificationSnapshot snapshotOf(Long settlementId) {
        return notificationMapper.findSettlementSnapshot(settlementId);
    }

    private List<SettlementMemberShare> shares(Long settlementId) {
        return notificationMapper.findSettlementMemberShares(settlementId);
    }

    private Notification notification(String type, Long settlementId,
            SettlementNotificationSnapshot snapshot, Long recipientMemberId,
            String actorName, BigDecimal amount) {
        return Notification.builder()
            .recipientMemberId(recipientMemberId)
            .notificationType(type)
            .settlementId(settlementId)
            .actorName(actorName)
            .gatheringName(snapshot.getGatheringName())
            .amount(amount)
            .currencyCode(snapshot.getCurrencyCode())
            .build();
    }

    /** 보낼 사람이 하나도 없으면 빈 VALUES가 되어 SQL이 깨지므로 아예 부르지 않는다. */
    private void insert(List<Notification> notifications) {
        if (notifications.isEmpty()) {
            return;
        }
        notificationMapper.insertNotifications(notifications);
    }
}
