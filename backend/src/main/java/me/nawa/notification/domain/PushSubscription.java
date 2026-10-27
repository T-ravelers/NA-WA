package me.nawa.notification.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 기기 하나의 푸시 구독이다. 발송할 때 주소와 암호화 키, 문구를 고를 언어를 읽어 쓴다. */
@Getter
@Setter
@NoArgsConstructor
public class PushSubscription {
    private Long pushSubscriptionId;
    private Long memberId;
    private String endpoint;
    private String p256dh;
    private String authSecret;
    private String languageCode;
}
