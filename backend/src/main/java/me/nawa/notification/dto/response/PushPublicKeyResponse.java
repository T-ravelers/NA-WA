package me.nawa.notification.dto.response;

import lombok.Builder;
import lombok.Getter;

/** 구독할 때 브라우저에 넘길 VAPID 공개키. 푸시가 꺼져 있으면 null이다. */
@Getter
@Builder
public class PushPublicKeyResponse {
    private final String publicKey;
}
