package me.nawa.notification.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import me.nawa.notification.mapper.NotificationMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private NotificationMapper notificationMapper;

    @Test
    void getNotifications_noLimitGiven_usesDefault() {
        when(notificationMapper.findByRecipient(7L, 30)).thenReturn(List.of());

        service().getNotifications(7L, null);

        verify(notificationMapper).findByRecipient(7L, 30);
    }

    @Test
    void getNotifications_oversizedLimit_isClampedInsteadOfRejected() {
        when(notificationMapper.findByRecipient(7L, 100)).thenReturn(List.of());

        service().getNotifications(7L, 5000);

        verify(notificationMapper).findByRecipient(7L, 100);
    }

    @Test
    void getNotifications_nonPositiveLimit_isClampedToOne() {
        when(notificationMapper.findByRecipient(7L, 1)).thenReturn(List.of());

        service().getNotifications(7L, 0);

        verify(notificationMapper).findByRecipient(7L, 1);
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

    private NotificationService service() {
        return new NotificationServiceImpl(notificationMapper);
    }
}
