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
            String actorName, String gatheringName, BigDecimal amount, String currencyCode) {
        this.recipientMemberId = recipientMemberId;
        this.notificationType = notificationType;
        this.settlementId = settlementId;
        this.actorName = actorName;
        this.gatheringName = gatheringName;
        this.amount = amount;
        this.currencyCode = currencyCode;
    }
}
