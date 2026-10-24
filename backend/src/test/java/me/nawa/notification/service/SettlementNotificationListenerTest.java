package me.nawa.notification.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import me.nawa.notification.domain.Notification;
import me.nawa.settlement.event.SettlementCompletedEvent;
import me.nawa.settlement.event.SettlementPaidEvent;
import me.nawa.settlement.event.SettlementRequestedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SettlementNotificationListenerTest {

    @Mock
    private SettlementNotificationRecorder recorder;

    @Mock
    private PushSender pushSender;

    private SettlementNotificationListener listener() {
        return new SettlementNotificationListener(recorder, pushSender);
    }

    @Test
    void listener_forwardsEachEventToTheRecorder() {
        SettlementNotificationListener listener = listener();

        listener.onSettlementRequested(new SettlementRequestedEvent(90L));
        listener.onSettlementPaid(new SettlementPaidEvent(90L, 3L));
        listener.onSettlementCompleted(new SettlementCompletedEvent(90L));

        verify(recorder).recordRequested(90L);
        verify(recorder).recordPaid(90L, 3L);
        verify(recorder).recordCompleted(90L);
    }

    /** 푸시는 기록기가 방금 적은 알림 그대로 보낸다. 이벤트마다 자기 알림이 자기 푸시로 간다. */
    @Test
    void listener_sendsPushForExactlyTheNotificationsTheRecorderWrote() {
        List<Notification> requested = List.of(notification(2L));
        List<Notification> paid = List.of(notification(1L));
        List<Notification> completed = List.of(notification(1L), notification(2L));
        when(recorder.recordRequested(90L)).thenReturn(requested);
        when(recorder.recordPaid(90L, 3L)).thenReturn(paid);
        when(recorder.recordCompleted(90L)).thenReturn(completed);
        SettlementNotificationListener listener = listener();

        listener.onSettlementRequested(new SettlementRequestedEvent(90L));
        listener.onSettlementPaid(new SettlementPaidEvent(90L, 3L));
        listener.onSettlementCompleted(new SettlementCompletedEvent(90L));

        verify(pushSender).send(requested);
        verify(pushSender).send(paid);
        verify(pushSender).send(completed);
    }

    /**
     * 알림 적재가 실패해도 예외를 밖으로 내보내지 않는다.
     *
     * 이 리스너는 이미 커밋된 정산 뒤에 불린다. 여기서 예외가 새어 나가면 돈이 오간 뒤에
     * 알림 하나 때문에 요청 전체가 실패한 것처럼 보인다. 알림은 정산 상태에서 파생된
     * 표시용 데이터라 유실을 허용한다. 적히지 않은 알림은 푸시로도 나가지 않는다.
     */
    @Test
    void listener_recorderFails_swallowsTheFailureAndSendsNoPush() {
        doThrow(new IllegalStateException("적재 실패")).when(recorder).recordPaid(90L, 3L);

        assertDoesNotThrow(() -> listener().onSettlementPaid(new SettlementPaidEvent(90L, 3L)));

        verifyNoInteractions(pushSender);
    }

    /** 푸시가 깨져도 이미 적힌 알림과 정산에는 영향이 없고, 요청도 실패하지 않는다. */
    @Test
    void listener_pushFails_swallowsTheFailure() {
        when(recorder.recordPaid(90L, 3L)).thenReturn(List.of(notification(1L)));
        doThrow(new IllegalStateException("푸시 실패")).when(pushSender).send(any());

        assertDoesNotThrow(() -> listener().onSettlementPaid(new SettlementPaidEvent(90L, 3L)));

        verify(recorder).recordPaid(90L, 3L);
    }

    private static Notification notification(Long recipientMemberId) {
        return Notification.builder()
            .recipientMemberId(recipientMemberId)
            .notificationType("SETTLEMENT_REQUESTED")
            .settlementId(90L)
            .build();
    }
}
