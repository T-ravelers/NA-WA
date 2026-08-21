package me.nawa.notification.dto.response;

import lombok.Builder;
import lombok.Getter;

/** 읽음 처리 결과. 이번 요청으로 실제로 읽음이 된 건수를 돌려준다. */
@Getter
@Builder
public class NotificationReadAllResponse {
    private final int updatedCount;
}
