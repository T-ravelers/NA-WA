package me.nawa.notification.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 알림 한 줄.
 *
 * 이름·약속명·금액은 알림을 만들 때 복사해 둔 값이다. 원본을 다시 조인해 오지 않으므로
 * 상대가 이름을 바꿔도 지난 알림의 문장은 그대로 남는다.
 *
 * 만든 시각도 값으로 받는다. 이 시각은 화면에 그대로 보이고 목록 정렬 기준이기도 해서,
 * DB에게 "지금 몇 시냐"고 물으면 DB와 앱의 시간대가 어긋난 만큼 알림 시각이 통째로
 * 밀린다. 같은 행의 읽은 시각과 같은 시계에서 나와야 둘을 나란히 놓고 읽을 수 있다.
 */
@Getter
@Setter
@NoArgsConstructor
public class Notification {
    private Long notificationId;
    private Long recipientMemberId;
    private String notificationType;
    private Long settlementId;
    private String actorName;
    private String gatheringName;
    private BigDecimal amount;
    private String currencyCode;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;

    @Builder
    public Notification(Long recipientMemberId, String notificationType, Long settlementId,
            String actorName, String gatheringName, BigDecimal amount, String currencyCode,
            LocalDateTime createdAt) {
        this.recipientMemberId = recipientMemberId;
        this.notificationType = notificationType;
        this.settlementId = settlementId;
        this.actorName = actorName;
        this.gatheringName = gatheringName;
        this.amount = amount;
        this.currencyCode = currencyCode;
        this.createdAt = createdAt;
    }
}
