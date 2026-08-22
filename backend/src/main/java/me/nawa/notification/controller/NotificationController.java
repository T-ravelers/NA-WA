package me.nawa.notification.controller;

import lombok.RequiredArgsConstructor;
import me.nawa.auth.security.AuthenticatedMember;
import me.nawa.common.response.ApiResponse;
import me.nawa.notification.dto.response.NotificationDeleteAllResponse;
import me.nawa.notification.dto.response.NotificationListResponse;
import me.nawa.notification.dto.response.NotificationReadAllResponse;
import me.nawa.notification.dto.response.UnreadNotificationCountResponse;
import me.nawa.notification.service.NotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 알림 목록·미읽음 개수·읽음 처리·지우기 API다.
 *
 * 벨 배지는 개수만 주기적으로 물어보고, 목록은 화면에 들어갈 때만 부른다.
 *
 * 알림 번호를 경로로 받는 것은 읽음·지우기뿐이고, 그 둘은 서비스가 수신자로 범위를 좁혀
 * 처리한다. 남의 번호를 적어 보내도 아무 행도 바뀌지 않으며 응답은 성공과 똑같다.
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * 알림 목록 조회
     *
     * 최신순으로 조회합니다. limit은 범위를 벗어나면 오류 대신 가능한 값으로 맞춥니다.
     * cursor에는 직전 응답의 nextCursor를 그대로 넘겨 다음 쪽을 받습니다.
     */
    @GetMapping
    public ApiResponse<NotificationListResponse> getNotifications(
        @AuthenticationPrincipal AuthenticatedMember member,
        @RequestParam(value = "limit", required = false) Integer limit,
        @RequestParam(value = "cursor", required = false) String cursor
    ) {
        return ApiResponse.success(
            notificationService.getNotifications(member.getMemberId(), limit, cursor)
        );
    }

    /**
     * 안 읽은 알림 개수 조회
     *
     * 화면의 벨 배지가 주기적으로 부르는 엔드포인트입니다.
     */
    @GetMapping("/unread-count")
    public ApiResponse<UnreadNotificationCountResponse> getUnreadCount(
        @AuthenticationPrincipal AuthenticatedMember member
    ) {
        return ApiResponse.success(
            notificationService.getUnreadCount(member.getMemberId())
        );
    }

    /**
     * 알림 하나 읽음 처리
     *
     * 목록에서 알림을 눌렀을 때 호출합니다. 이미 읽었거나 없는 알림이어도 성공입니다.
     */
    @PostMapping("/{notificationId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(
        @AuthenticationPrincipal AuthenticatedMember member,
        @PathVariable Long notificationId
    ) {
        notificationService.markRead(member.getMemberId(), notificationId);
    }

    /**
     * 알림 전체 읽음 처리
     *
     * 알림 목록 화면의 "모두 읽음"이 호출합니다.
     */
    @PostMapping("/read-all")
    public ApiResponse<NotificationReadAllResponse> readAll(
        @AuthenticationPrincipal AuthenticatedMember member
    ) {
        return ApiResponse.success(
            notificationService.readAll(member.getMemberId())
        );
    }

    /**
     * 알림 하나 지우기
     *
     * 목록에서 카드의 X를 눌렀을 때 호출합니다. 없는 알림이어도 성공입니다.
     */
    @DeleteMapping("/{notificationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
        @AuthenticationPrincipal AuthenticatedMember member,
        @PathVariable Long notificationId
    ) {
        notificationService.delete(member.getMemberId(), notificationId);
    }

    /**
     * 알림 모두 지우기
     *
     * 지워진 건수를 돌려줍니다. 이미 비어 있었으면 0입니다.
     */
    @DeleteMapping
    public ApiResponse<NotificationDeleteAllResponse> deleteAll(
        @AuthenticationPrincipal AuthenticatedMember member
    ) {
        return ApiResponse.success(
            notificationService.deleteAll(member.getMemberId())
        );
    }
}
