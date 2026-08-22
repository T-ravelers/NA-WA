package me.nawa.notification.service;

import me.nawa.notification.dto.response.NotificationDeleteAllResponse;
import me.nawa.notification.dto.response.NotificationListResponse;
import me.nawa.notification.dto.response.NotificationReadAllResponse;
import me.nawa.notification.dto.response.UnreadNotificationCountResponse;

/** 알림 목록·미읽음 개수·읽음 처리·지우기의 계약이다. */
public interface NotificationService {

    NotificationListResponse getNotifications(Long memberId, Integer limit, String cursor);

    UnreadNotificationCountResponse getUnreadCount(Long memberId);

    void markRead(Long memberId, Long notificationId);

    NotificationReadAllResponse readAll(Long memberId);

    void delete(Long memberId, Long notificationId);

    NotificationDeleteAllResponse deleteAll(Long memberId);
}
