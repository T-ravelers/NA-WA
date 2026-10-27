package me.nawa.notification.service;

import me.nawa.notification.dto.request.PushSubscribeRequest;

/** 기기별 푸시 구독의 등록·해지와, 구독할 때 쓸 공개키 조회의 계약이다. */
public interface PushSubscriptionService {

    /** 푸시가 꺼져 있으면(키가 비어 있으면) null이다. 화면은 이때 구독 UI를 숨긴다. */
    String getPublicKey();

    void subscribe(Long memberId, PushSubscribeRequest request);

    void unsubscribe(Long memberId, String endpoint);
}
