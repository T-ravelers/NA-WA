package me.nawa.notification.service;

import java.util.List;
import me.nawa.notification.dto.response.NotificationReadAllResponse;
import me.nawa.notification.dto.response.NotificationResponse;
import me.nawa.notification.dto.response.UnreadNotificationCountResponse;

/** 알림 목록·미읽음 개수·읽음 처리의 조회 계약이다. */
public interface NotificationService {

    List<NotificationResponse> getNotifications(Long memberId, Integer limit);

    UnreadNotificationCountResponse getUnreadCount(Long memberId);

    NotificationReadAllResponse readAll(Long memberId);
}
