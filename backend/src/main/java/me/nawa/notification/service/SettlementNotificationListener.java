package me.nawa.notification.service;

import java.util.List;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.nawa.notification.domain.Notification;
import me.nawa.settlement.event.SettlementCompletedEvent;
import me.nawa.settlement.event.SettlementPaidEvent;
import me.nawa.settlement.event.SettlementRequestedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 정산에서 일어난 일을 듣고 알림을 적은 뒤, 적은 알림을 웹 푸시로도 보낸다.
 *
 * <p><b>커밋된 뒤에만 듣는다.</b> {@code AFTER_COMMIT}이라, 정산 트랜잭션이 도중에
 * 되돌아가면 이 메서드는 아예 불리지 않는다. 그렇지 않으면 실패한 지급에 대해 "누가
 * 냈습니다" 알림이 남는다.
 *
 * <p><b>실패해도 조용히 넘어간다.</b> 예외를 여기서 잡아 로그만 남긴다. 적는 일은
 * {@code SettlementNotificationRecorder}가 자기 트랜잭션에서 하므로, 잡는 자리를 그
 * 트랜잭션 바깥인 여기에 두어야 실패한 트랜잭션을 억지로 커밋시키지 않는다.
 *
 * <p><b>푸시는 적재가 끝난 알림에만 보낸다.</b> 적재가 실패하면 보낼 알림이 없으니 푸시도
 * 나가지 않고, 푸시가 실패해도 이미 적힌 알림과 정산은 그대로다. 푸시는 목록을 대신하지 않는
 * 덤이라 놓친 알림은 폴링이 메운다. 이 두 실패를 따로 잡는 이유는 로그로 어느 쪽이 깨졌는지
 * 구분하기 위해서다.
 *
 * <p>이벤트가 트랜잭션 밖에서 발행되면 이 리스너는 아무 말 없이 안 불린다. 발행 지점 세 곳이
 * 모두 {@code @Transactional} 안에 있어야 하는 이유다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementNotificationListener {

    private final SettlementNotificationRecorder recorder;
    private final PushSender pushSender;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSettlementRequested(SettlementRequestedEvent event) {
        handle("requested", event.settlementId(), () -> recorder.recordRequested(event.settlementId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSettlementPaid(SettlementPaidEvent event) {
        handle("paid", event.settlementId(),
            () -> recorder.recordPaid(event.settlementId(), event.paidByMemberId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSettlementCompleted(SettlementCompletedEvent event) {
        handle("completed", event.settlementId(), () -> recorder.recordCompleted(event.settlementId()));
    }

    private void handle(String kind, Long settlementId, Supplier<List<Notification>> record) {
        List<Notification> recorded;
        try {
            recorded = record.get();
        } catch (RuntimeException exception) {
            log.error("정산 알림 적재 실패, kind={}, settlementId={}", kind, settlementId, exception);
            return;
        }

        try {
            pushSender.send(recorded);
        } catch (RuntimeException exception) {
            log.error("정산 푸시 발송 요청 실패, kind={}, settlementId={}", kind, settlementId, exception);
        }
    }
}
