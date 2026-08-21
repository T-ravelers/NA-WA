package me.nawa.notification.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

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

    @Test
    void listener_forwardsEachEventToTheRecorder() {
        SettlementNotificationListener listener = new SettlementNotificationListener(recorder);

        listener.onSettlementRequested(new SettlementRequestedEvent(90L));
        listener.onSettlementPaid(new SettlementPaidEvent(90L, 3L));
        listener.onSettlementCompleted(new SettlementCompletedEvent(90L));

        verify(recorder).recordRequested(90L);
        verify(recorder).recordPaid(90L, 3L);
        verify(recorder).recordCompleted(90L);
    }

    /**
     * 알림 적재가 실패해도 예외를 밖으로 내보내지 않는다.
     *
     * 이 리스너는 이미 커밋된 정산 뒤에 불린다. 여기서 예외가 새어 나가면 돈이 오간 뒤에
     * 알림 하나 때문에 요청 전체가 실패한 것처럼 보인다. 알림은 정산 상태에서 파생된
     * 표시용 데이터라 유실을 허용한다.
     */
    @Test
    void listener_recorderFails_swallowsTheFailure() {
        doThrow(new IllegalStateException("적재 실패")).when(recorder).recordPaid(90L, 3L);
        SettlementNotificationListener listener = new SettlementNotificationListener(recorder);

        assertDoesNotThrow(() -> listener.onSettlementPaid(new SettlementPaidEvent(90L, 3L)));
    }
}
