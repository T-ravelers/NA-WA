package me.nawa.notification.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import me.nawa.notification.domain.Notification;
import me.nawa.notification.domain.SettlementMemberShare;
import me.nawa.notification.domain.SettlementNotificationSnapshot;
import me.nawa.notification.mapper.NotificationMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 누구에게 얼마로 알릴지의 규칙을 고정한다.
 *
 * 정산 하나에 원결제자 1번(Ari), 아직 안 낸 2번(Bora), 이미 낸 3번(Chan)이 있는 상황을 쓴다.
 */
@ExtendWith(MockitoExtension.class)
class SettlementNotificationRecorderTest {

    private static final Long SETTLEMENT_ID = 90L;

    @Mock
    private NotificationMapper notificationMapper;
    @Captor
    private ArgumentCaptor<List<Notification>> captor;

    @Test
    void recordRequested_notifiesOnlyUnpaidMembersWithTheirOwnShare() {
        givenSettlement();

        recorder().recordRequested(SETTLEMENT_ID);

        verify(notificationMapper).insertNotifications(captor.capture());
        List<Notification> notifications = captor.getValue();
        assertEquals(1, notifications.size());
        Notification notification = notifications.get(0);
        assertEquals("SETTLEMENT_REQUESTED", notification.getNotificationType());
        assertEquals(2L, notification.getRecipientMemberId());
        // 요청을 보낸 사람은 돈을 받을 원결제자다.
        assertEquals("Ari", notification.getActorName());
        // 총액이 아니라 그 사람이 낼 몫이다.
        assertEquals(new BigDecimal("30"), notification.getAmount());
        assertEquals("Dinner", notification.getGatheringName());
        assertEquals("KRW", notification.getCurrencyCode());
    }

    @Test
    void recordPaid_notifiesThePayerWithWhoPaidAndHowMuch() {
        givenSettlement();

        recorder().recordPaid(SETTLEMENT_ID, 3L);

        verify(notificationMapper).insertNotifications(captor.capture());
        List<Notification> notifications = captor.getValue();
        assertEquals(1, notifications.size());
        Notification notification = notifications.get(0);
        assertEquals("SETTLEMENT_PAID", notification.getNotificationType());
        // 받는 사람은 돈을 받을 원결제자 한 명뿐이다.
        assertEquals(1L, notification.getRecipientMemberId());
        assertEquals("Chan", notification.getActorName());
        assertEquals(new BigDecimal("20"), notification.getAmount());
    }

    @Test
    void recordCompleted_notifiesEveryoneIncludingThePayerWithTheTotal() {
        givenSettlement();

        recorder().recordCompleted(SETTLEMENT_ID);

        verify(notificationMapper).insertNotifications(captor.capture());
        List<Notification> notifications = captor.getValue();
        assertEquals(3, notifications.size());
        assertTrue(notifications.stream()
            .allMatch(notification -> "SETTLEMENT_COMPLETED".equals(notification.getNotificationType())));
        assertTrue(notifications.stream()
            .allMatch(notification -> new BigDecimal("100").equals(notification.getAmount())));
        // 원결제자는 구성원 목록에 없으므로 빠지기 쉽다. 세 사람 모두 받아야 한다.
        assertEquals(
            List.of(2L, 3L, 1L),
            notifications.stream().map(Notification::getRecipientMemberId).toList()
        );
    }

    @Test
    void recordRequested_everyoneAlreadyPaid_insertsNothing() {
        when(notificationMapper.findSettlementSnapshot(SETTLEMENT_ID)).thenReturn(snapshot());
        when(notificationMapper.findSettlementMemberShares(SETTLEMENT_ID))
            .thenReturn(List.of(share(3L, "Chan", "20", "PAID")));

        recorder().recordRequested(SETTLEMENT_ID);

        // 빈 목록으로 INSERT를 부르면 VALUES가 비어 SQL이 깨진다.
        verify(notificationMapper, never()).insertNotifications(any());
    }

    @Test
    void recordPaid_memberNotInSettlement_insertsNothing() {
        givenSettlement();

        recorder().recordPaid(SETTLEMENT_ID, 999L);

        verify(notificationMapper, never()).insertNotifications(any());
    }

    @Test
    void record_missingSettlement_insertsNothing() {
        when(notificationMapper.findSettlementSnapshot(SETTLEMENT_ID)).thenReturn(null);

        recorder().recordCompleted(SETTLEMENT_ID);

        verify(notificationMapper, never()).insertNotifications(any());
    }

    private void givenSettlement() {
        when(notificationMapper.findSettlementSnapshot(SETTLEMENT_ID)).thenReturn(snapshot());
        when(notificationMapper.findSettlementMemberShares(SETTLEMENT_ID)).thenReturn(List.of(
            share(2L, "Bora", "30", "PENDING"),
            share(3L, "Chan", "20", "PAID")
        ));
    }

    private SettlementNotificationRecorder recorder() {
        return new SettlementNotificationRecorder(notificationMapper);
    }

    private SettlementNotificationSnapshot snapshot() {
        SettlementNotificationSnapshot snapshot = new SettlementNotificationSnapshot();
        snapshot.setPayerMemberId(1L);
        snapshot.setPayerName("Ari");
        snapshot.setGatheringName("Dinner");
        snapshot.setTotalAmount(new BigDecimal("100"));
        snapshot.setCurrencyCode("KRW");
        return snapshot;
    }

    private SettlementMemberShare share(Long memberId, String name, String amount, String status) {
        SettlementMemberShare share = new SettlementMemberShare();
        share.setMemberId(memberId);
        share.setDisplayName(name);
        share.setShareAmount(new BigDecimal(amount));
        share.setRequestStatus(status);
        return share;
    }
}
