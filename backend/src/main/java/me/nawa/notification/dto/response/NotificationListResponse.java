package me.nawa.notification.dto.response;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

/**
 * 알림 한 쪽(page)과, 그 다음을 물어볼 때 쓸 표시.
 *
 * 목록을 배열 그대로 내리지 않고 감싸는 이유는 `nextCursor`를 실을 자리가 필요해서다.
 * 지갑 거래 내역(`TransactionListResponse`)이 이미 같은 모양을 쓴다.
 *
 * `nextCursor`가 비어 있으면 더 볼 것이 없다는 뜻이다. 개수를 함께 내리지 않는 것은,
 * 전체 개수를 세려면 매번 표를 한 번 더 훑어야 하는데 화면이 그 숫자를 쓰지 않기 때문이다.
 */
@Getter
@Builder
public class NotificationListResponse {

    private final List<NotificationResponse> notifications;

    /** 다음 쪽을 물어볼 때 그대로 돌려보낼 값. 더 없으면 null. */
    private final String nextCursor;
}
