package me.nawa.notification.controller;

import java.util.List;
import lombok.RequiredArgsConstructor;
import me.nawa.auth.security.AuthenticatedMember;
import me.nawa.common.response.ApiResponse;
import me.nawa.notification.dto.response.NotificationReadAllResponse;
import me.nawa.notification.dto.response.NotificationResponse;
import me.nawa.notification.dto.response.UnreadNotificationCountResponse;
import me.nawa.notification.service.NotificationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 알림 목록·미읽음 개수·읽음 처리 API다.
 *
 * 벨 배지는 개수만 주기적으로 물어보고, 목록은 화면에 들어갈 때만 부른다.
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
     */
    @GetMapping
    public ApiResponse<List<NotificationResponse>> getNotifications(
        @AuthenticationPrincipal AuthenticatedMember member,
        @RequestParam(value = "limit", required = false) Integer limit
    ) {
        return ApiResponse.success(
            notificationService.getNotifications(member.getMemberId(), limit)
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
     * 알림 전체 읽음 처리
     *
     * 알림 목록 화면에 들어갈 때 한 번 호출합니다.
     */
    @PostMapping("/read-all")
    public ApiResponse<NotificationReadAllResponse> readAll(
        @AuthenticationPrincipal AuthenticatedMember member
    ) {
        return ApiResponse.success(
            notificationService.readAll(member.getMemberId())
        );
    }
}
