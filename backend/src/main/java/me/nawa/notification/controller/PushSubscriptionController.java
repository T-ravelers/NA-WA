package me.nawa.notification.controller;

import lombok.RequiredArgsConstructor;
import me.nawa.auth.security.AuthenticatedMember;
import me.nawa.common.response.ApiResponse;
import me.nawa.notification.dto.request.PushSubscribeRequest;
import me.nawa.notification.dto.request.PushUnsubscribeRequest;
import me.nawa.notification.dto.response.PushPublicKeyResponse;
import me.nawa.notification.service.PushSubscriptionService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 기기의 푸시 구독을 등록·해지하고, 구독에 쓸 공개키를 내려주는 API다. */
@RestController
@RequestMapping("/api/v1/push-subscriptions")
@RequiredArgsConstructor
public class PushSubscriptionController {

    private final PushSubscriptionService pushSubscriptionService;

    /**
     * 푸시 공개키 조회
     *
     * 화면은 이 값으로 구독합니다. publicKey가 null이면 서버의 푸시가 꺼져 있다는 뜻입니다.
     */
    @GetMapping("/public-key")
    public ApiResponse<PushPublicKeyResponse> getPublicKey() {
        return ApiResponse.success(
            PushPublicKeyResponse.builder()
                .publicKey(pushSubscriptionService.getPublicKey())
                .build()
        );
    }

    /**
     * 푸시 구독 등록
     *
     * 같은 endpoint가 이미 있으면 키·언어를 갱신하고 소유자를 지금 회원으로 옮깁니다.
     * 같은 요청을 다시 보내도 결과가 같습니다.
     */
    @PutMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void subscribe(
        @AuthenticationPrincipal AuthenticatedMember member,
        @RequestBody PushSubscribeRequest request
    ) {
        pushSubscriptionService.subscribe(member.getMemberId(), request);
    }

    /**
     * 푸시 구독 해지
     *
     * 없거나 남의 endpoint여도 성공입니다.
     */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsubscribe(
        @AuthenticationPrincipal AuthenticatedMember member,
        @RequestBody PushUnsubscribeRequest request
    ) {
        pushSubscriptionService.unsubscribe(member.getMemberId(), request.getEndpoint());
    }
}
