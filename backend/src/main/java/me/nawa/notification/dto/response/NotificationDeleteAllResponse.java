package me.nawa.notification.dto.response;

import lombok.Builder;
import lombok.Getter;

/** 모두 지우기 결과. 이번 요청으로 실제로 지워진 건수를 돌려준다. 이미 비어 있었으면 0이다. */
@Getter
@Builder
public class NotificationDeleteAllResponse {
    private final int deletedCount;
}
