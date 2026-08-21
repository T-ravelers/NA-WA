package me.nawa.notification.domain;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 알림 문장에 넣을 값을 정산에서 한 번에 읽어 온 것.
 *
 * 수신자마다 따로 조회하지 않으려고 정산당 한 번만 읽는다.
 */
@Getter
@Setter
@NoArgsConstructor
public class SettlementNotificationSnapshot {
    /** 이 정산으로 돈을 받는 사람. 생성자와 항상 같다(스키마 제약으로 보장된다). */
    private Long payerMemberId;
    private String payerName;
    private String gatheringName;
    private BigDecimal totalAmount;
    private String currencyCode;
}
