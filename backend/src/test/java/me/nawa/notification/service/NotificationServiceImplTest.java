package me.nawa.notification.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;
import me.nawa.notification.domain.Notification;
import me.nawa.notification.dto.response.NotificationListResponse;
import me.nawa.notification.mapper.NotificationMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private NotificationMapper notificationMapper;

    // 다음 쪽이 있는지 보려고 한 건을 더 달라고 하므로, 매퍼에 가는 개수는 언제나 화면
    // 개수 + 1이다. 아래 세 테스트가 확인하는 것은 그 앞의 "화면 개수" 쪽 보정이다.
    @Test
    void getNotifications_noLimitGiven_usesDefault() {
        when(notificationMapper.findByRecipient(eq(7L), isNull(), eq(31))).thenReturn(List.of());

        service().getNotifications(7L, null, null);

        verify(notificationMapper).findByRecipient(7L, null, 31);
    }

    @Test
    void getNotifications_oversizedLimit_isClampedInsteadOfRejected() {
        when(notificationMapper.findByRecipient(eq(7L), isNull(), eq(101))).thenReturn(List.of());

        service().getNotifications(7L, 5000, null);

        verify(notificationMapper).findByRecipient(7L, null, 101);
    }

    @Test
    void getNotifications_nonPositiveLimit_isClampedToOne() {
        when(notificationMapper.findByRecipient(eq(7L), isNull(), eq(2))).thenReturn(List.of());

        service().getNotifications(7L, 0, null);

        verify(notificationMapper).findByRecipient(7L, null, 2);
    }

    /** 넘치게 받아 온 한 건은 화면에 내보내지 않고, 그 대신 다음 커서를 만든다. */
    @Test
    void getNotifications_whenMoreRowsExist_trimsExtraRowAndReturnsCursor() {
        when(notificationMapper.findByRecipient(eq(7L), isNull(), eq(3)))
            .thenReturn(rows(101L, 102L, 103L));

        NotificationListResponse response = service().getNotifications(7L, 2, null);

        assertEquals(2, response.getNotifications().size());
        assertEquals("102", response.getNextCursor());
    }

    @Test
    void getNotifications_whenLastPage_hasNoCursor() {
        when(notificationMapper.findByRecipient(eq(7L), isNull(), eq(3)))
            .thenReturn(rows(101L, 102L));

        NotificationListResponse response = service().getNotifications(7L, 2, null);

        assertEquals(2, response.getNotifications().size());
        assertNull(response.getNextCursor());
    }

    @Test
    void getNotifications_cursorIsPassedThroughAsNumber() {
        when(notificationMapper.findByRecipient(7L, 102L, 31)).thenReturn(List.of());

        service().getNotifications(7L, null, "102");

        verify(notificationMapper).findByRecipient(7L, 102L, 31);
    }

    /** 주소로 오는 값이라 무엇이든 올 수 있다. 오류 대신 첫 쪽으로 떨어뜨린다. */
    @Test
    void getNotifications_unparseableCursor_fallsBackToFirstPage() {
        when(notificationMapper.findByRecipient(eq(7L), isNull(), eq(31))).thenReturn(List.of());

        service().getNotifications(7L, null, "not-a-number");

        verify(notificationMapper).findByRecipient(7L, null, 31);
    }

    @Test
    void readAll_passesApplicationTimeInsteadOfLeavingItToTheDatabase() {
        when(notificationMapper.markAllRead(eq(7L), any(LocalDateTime.class))).thenReturn(3);

        assertEquals(3, service().readAll(7L).getUpdatedCount());

        verify(notificationMapper).markAllRead(eq(7L), any(LocalDateTime.class));
    }

    @Test
    void getUnreadCount_returnsMapperCount() {
        when(notificationMapper.countUnreadByRecipient(7L)).thenReturn(4);

        assertEquals(4, service().getUnreadCount(7L).getCount());
    }

    // 읽음·지우기는 모두 수신자를 함께 넘겨야 남의 알림에 닿지 않는다. 시각도 DB 시계가
    // 아니라 애플리케이션이 넘긴 값이어야 한다 — 적재·읽음과 같은 이유다.
    @Test
    void markRead_scopesToRecipientAndPassesApplicationTime() {
        service().markRead(7L, 55L);

        verify(notificationMapper).markRead(eq(7L), eq(55L), any(LocalDateTime.class));
    }

    @Test
    void delete_scopesToRecipientAndPassesApplicationTime() {
        service().delete(7L, 55L);

        verify(notificationMapper).softDelete(eq(7L), eq(55L), any(LocalDateTime.class));
    }

    /** 남의 알림이거나 없는 번호면 매퍼가 0을 돌려주는데, 그래도 예외 없이 끝나야 한다. */
    @Test
    void delete_whenNothingMatched_stillSucceeds() {
        when(notificationMapper.softDelete(eq(7L), eq(999L), any(LocalDateTime.class)))
            .thenReturn(0);

        service().delete(7L, 999L);

        verify(notificationMapper).softDelete(eq(7L), eq(999L), any(LocalDateTime.class));
    }

    @Test
    void deleteAll_returnsDeletedCount() {
        when(notificationMapper.softDeleteAll(eq(7L), any(LocalDateTime.class))).thenReturn(5);

        assertEquals(5, service().deleteAll(7L).getDeletedCount());
    }

    /** 번호는 DB가 매기는 값이라 빌더에 없다. 매퍼가 그러듯 setter로 넣는다. */
    private static List<Notification> rows(Long... ids) {
        return IntStream.range(0, ids.length)
            .mapToObj(index -> {
                Notification notification = Notification.builder()
                    .recipientMemberId(7L)
                    .notificationType("SETTLEMENT_REQUESTED")
                    .settlementId(1L)
                    .actorName("Ari")
                    .gatheringName("Dinner")
                    .amount(BigDecimal.TEN)
                    .currencyCode("KRW")
                    .createdAt(LocalDateTime.of(2026, 8, 21, 12, 0).minusMinutes(index))
                    .build();
                notification.setNotificationId(ids[index]);
                return notification;
            })
            .toList();
    }

    private NotificationService service() {
        return new NotificationServiceImpl(notificationMapper);
    }
}
