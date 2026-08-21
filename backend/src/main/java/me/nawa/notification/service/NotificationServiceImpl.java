package me.nawa.notification.service;

import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import me.nawa.notification.domain.Notification;
import me.nawa.notification.dto.response.NotificationReadAllResponse;
import me.nawa.notification.dto.response.NotificationResponse;
import me.nawa.notification.dto.response.UnreadNotificationCountResponse;
import me.nawa.notification.mapper.NotificationMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 알림 화면이 쓰는 조회와 읽음 처리다. */
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private static final int DEFAULT_LIMIT = 30;
    private static final int MAX_LIMIT = 100;

    private final NotificationMapper notificationMapper;

    @Override
    @Transactional(readOnly = true)
    public List<NotificationResponse> getNotifications(Long memberId, Integer limit) {
        return notificationMapper.findByRecipient(memberId, clampLimit(limit)).stream()
            .map(NotificationServiceImpl::toResponse)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public UnreadNotificationCountResponse getUnreadCount(Long memberId) {
        return UnreadNotificationCountResponse.builder()
            .count(notificationMapper.countUnreadByRecipient(memberId))
            .build();
    }

    @Override
    @Transactional
    public NotificationReadAllResponse readAll(Long memberId) {
        return NotificationReadAllResponse.builder()
            .updatedCount(notificationMapper.markAllRead(memberId, LocalDateTime.now()))
            .build();
    }

    /**
     * 요청한 개수를 쓸 수 있는 범위로 접는다.
     *
     * 범위를 벗어난 값을 오류로 돌려주지 않는 이유는, 이 값이 화면의 목적을 바꾸지 않기
     * 때문이다. 사용자는 "최근 알림"을 보고 싶을 뿐이고, 200을 보내든 -1을 보내든 그 뜻은
     * 같다. 오류를 내면 화면만 비고 얻는 것이 없다.
     */
    private static int clampLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        return Math.min(Math.max(limit, 1), MAX_LIMIT);
    }

    private static NotificationResponse toResponse(Notification notification) {
        return NotificationResponse.builder()
            .id(notification.getNotificationId())
            .type(notification.getNotificationType())
            .settlementId(notification.getSettlementId())
            .actorName(notification.getActorName())
            .gatheringName(notification.getGatheringName())
            .amount(notification.getAmount())
            .currencyCode(notification.getCurrencyCode())
            .readAt(notification.getReadAt())
            .createdAt(notification.getCreatedAt())
            .build();
    }
}
