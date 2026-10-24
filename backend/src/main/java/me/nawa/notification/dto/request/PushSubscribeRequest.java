package me.nawa.notification.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 브라우저의 PushSubscription 값과, 문구를 고를 화면 언어다. */
@Getter
@Setter
@NoArgsConstructor
public class PushSubscribeRequest {
    private String endpoint;
    private String p256dh;
    private String auth;
    private String language;
}
