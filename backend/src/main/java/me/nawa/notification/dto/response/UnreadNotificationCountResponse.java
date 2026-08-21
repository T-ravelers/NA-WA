package me.nawa.notification.dto.response;

import lombok.Builder;
import lombok.Getter;

/** 안 읽은 알림 개수 응답. 화면의 벨 배지가 이 값 하나만 본다. */
@Getter
@Builder
public class UnreadNotificationCountResponse {
    private final int count;
}
