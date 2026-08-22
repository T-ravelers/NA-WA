package me.nawa.notification.service;

import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import me.nawa.notification.domain.Notification;
import me.nawa.notification.dto.response.NotificationDeleteAllResponse;
import me.nawa.notification.dto.response.NotificationListResponse;
import me.nawa.notification.dto.response.NotificationReadAllResponse;
import me.nawa.notification.dto.response.NotificationResponse;
import me.nawa.notification.dto.response.UnreadNotificationCountResponse;
import me.nawa.notification.mapper.NotificationMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 알림 화면이 쓰는 조회와 읽음·지우기 처리다. */
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private static final int DEFAULT_LIMIT = 30;
    private static final int MAX_LIMIT = 100;

    private final NotificationMapper notificationMapper;

    /**
     * 알림 한 쪽을 읽는다.
     *
     * 다음 쪽이 있는지 알려면 화면에 쓸 개수만 읽어서는 알 수 없다. 한 건을 더 달라고 해서
     * 넘치면 "더 있다"로 보고, 넘친 한 건은 버린다. 지갑 거래 내역이 쓰는 방식과 같다.
     */
    @Override
    @Transactional(readOnly = true)
    public NotificationListResponse getNotifications(Long memberId, Integer limit, String cursor) {
        int size = clampLimit(limit);
        List<Notification> rows =
            notificationMapper.findByRecipient(memberId, parseCursor(cursor), size + 1);

        boolean hasNext = rows.size() > size;
        List<Notification> page = hasNext ? rows.subList(0, size) : rows;

        return NotificationListResponse.builder()
            .notifications(page.stream().map(NotificationServiceImpl::toResponse).toList())
            .nextCursor(
                hasNext
                    ? String.valueOf(page.get(page.size() - 1).getNotificationId())
                    : null
            )
            .build();
    }

    @Override
    @Transactional(readOnly = true)
    public UnreadNotificationCountResponse getUnreadCount(Long memberId) {
        return UnreadNotificationCountResponse.builder()
            .count(notificationMapper.countUnreadByRecipient(memberId))
            .build();
    }

    /**
     * 알림 하나를 읽음으로 바꾼다.
     *
     * 바뀐 행이 없어도 아무 일도 하지 않고 끝낸다. 이미 읽었거나, 지웠거나, 남의 알림
     * 번호이거나, 아예 없는 번호일 때가 모두 여기에 해당한다. 이것들을 갈라 다른 응답을
     * 내면 그 차이만으로 "그 번호의 알림이 있다"는 것을 알 수 있게 된다.
     */
    @Override
    @Transactional
    public void markRead(Long memberId, Long notificationId) {
        notificationMapper.markRead(memberId, notificationId, LocalDateTime.now());
    }

    @Override
    @Transactional
    public NotificationReadAllResponse readAll(Long memberId) {
        return NotificationReadAllResponse.builder()
            .updatedCount(notificationMapper.markAllRead(memberId, LocalDateTime.now()))
            .build();
    }

    /** 지우는 것도 읽음과 같다 — 바뀐 행이 없어도 성공으로 끝낸다. */
    @Override
    @Transactional
    public void delete(Long memberId, Long notificationId) {
        notificationMapper.softDelete(memberId, notificationId, LocalDateTime.now());
    }

    @Override
    @Transactional
    public NotificationDeleteAllResponse deleteAll(Long memberId) {
        return NotificationDeleteAllResponse.builder()
            .deletedCount(notificationMapper.softDeleteAll(memberId, LocalDateTime.now()))
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

    /**
     * 커서를 숫자로 되돌린다.
     *
     * 커서는 우리가 직전 응답에 실어 보낸 값이지만 주소로 오는 값이라 무엇이든 올 수 있다.
     * 숫자가 아니면 오류 대신 첫 쪽으로 떨어뜨린다 — limit과 같은 이유다. 없는 번호를 넣어도
     * 비교할 짝이 없어 빈 쪽이 나오므로, 남의 번호를 넣어 남의 알림을 엿볼 방법은 없다.
     */
    private static Long parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(cursor.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
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
