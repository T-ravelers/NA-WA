package me.nawa.notification.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import me.nawa.notification.domain.Notification;
import me.nawa.notification.domain.SettlementReminderCriteria;
import me.nawa.notification.domain.SettlementReminderTarget;
import me.nawa.notification.mapper.NotificationMapper;
import me.nawa.notification.mapper.SettlementReminderMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 누구를 기록하고 누구에게 알림을 적는지의 규칙을 고정한다. 잠금과 트랜잭션 경계가 실제로
 * 지켜지는지는 {@code SettlementReminderWriterIntegrationTest}가 DB로 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class SettlementReminderWriterImplTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 25, 10, 0);
    private static final SettlementReminderCriteria CRITERIA =
        new SettlementReminderCriteria(NOW, LocalDateTime.of(2026, 10, 23, 0, 0),
            LocalDateTime.of(2026, 10, 23, 0, 0), 3);

    @Mock
    private SettlementReminderMapper reminderMapper;
    @Mock
    private NotificationMapper notificationMapper;
    @Captor
    private ArgumentCaptor<List<Long>> markedIds;
    @Captor
    private ArgumentCaptor<List<Notification>> inserted;

    @Test
    void remind_recordsAndNotifiesOnlyTheMembersThatWereActuallyLocked() {
        // 1, 2, 3을 넘겼지만 2번은 그 사이에 냈거나 다른 곳이 쥐고 있어 잠기지 않았다.
        when(reminderMapper.lockTargets(CRITERIA, List.of(1L, 2L, 3L)))
            .thenReturn(List.of(target(1L, 11L, "30"), target(3L, 13L, "20")));

        List<Notification> recorded = writer().remind(List.of(1L, 2L, 3L), CRITERIA);

        verify(reminderMapper).markReminded(markedIds.capture(), eq(NOW));
        assertEquals(List.of(1L, 3L), markedIds.getValue());
        verify(notificationMapper).insertNotifications(inserted.capture());
        assertEquals(inserted.getValue(), recorded);
        assertEquals(List.of(11L, 13L), recorded.stream().map(Notification::getRecipientMemberId).toList());
    }

    @Test
    void remind_buildsAReminderFromTheTargetValuesAtTheRunTime() {
        when(reminderMapper.lockTargets(CRITERIA, List.of(1L))).thenReturn(List.of(target(1L, 11L, "30")));

        Notification notification = writer().remind(List.of(1L), CRITERIA).get(0);

        assertEquals("SETTLEMENT_REMINDER", notification.getNotificationType());
        assertEquals(11L, notification.getRecipientMemberId());
        assertEquals(90L, notification.getSettlementId());
        // 보낸 사람 자리는 돈을 받을 원결제자이고, 금액은 정산 총액이 아니라 그 사람 몫이다.
        assertEquals("Ari", notification.getActorName());
        assertEquals("Dinner", notification.getGatheringName());
        assertEquals(new BigDecimal("30"), notification.getAmount());
        assertEquals("KRW", notification.getCurrencyCode());
        // 시각은 DB 기본값이 아니라 이번 실행이 넘긴 값이다.
        assertEquals(NOW, notification.getCreatedAt());
    }

    @Test
    void remind_nothingLocked_writesNothing() {
        when(reminderMapper.lockTargets(CRITERIA, List.of(1L))).thenReturn(List.of());

        List<Notification> recorded = writer().remind(List.of(1L), CRITERIA);

        assertTrue(recorded.isEmpty());
        // 빈 목록으로 UPDATE·INSERT를 부르면 IN ()과 빈 VALUES로 SQL이 깨진다.
        verify(reminderMapper, never()).markReminded(any(), any());
        verify(notificationMapper, never()).insertNotifications(any());
    }

    @Test
    void remind_insertFails_letsTheExceptionOutSoTheTransactionRollsBack() {
        when(reminderMapper.lockTargets(CRITERIA, List.of(1L))).thenReturn(List.of(target(1L, 11L, "30")));
        doThrow(new IllegalStateException("insert failed"))
            .when(notificationMapper).insertNotifications(any());

        assertThrows(IllegalStateException.class, () -> writer().remind(List.of(1L), CRITERIA));
    }

    // 테스트는 인터페이스 타입으로 부른다. 구현체는 여기서 한 번만 만든다.
    private SettlementReminderWriter writer() {
        return new SettlementReminderWriterImpl(reminderMapper, notificationMapper);
    }

    private SettlementReminderTarget target(Long settlementMemberId, Long recipientMemberId, String share) {
        SettlementReminderTarget target = new SettlementReminderTarget();
        target.setSettlementMemberId(settlementMemberId);
        target.setRecipientMemberId(recipientMemberId);
        target.setSettlementId(90L);
        target.setPayerName("Ari");
        target.setGatheringName("Dinner");
        target.setShareAmount(new BigDecimal(share));
        target.setCurrencyCode("KRW");
        return target;
    }
}
