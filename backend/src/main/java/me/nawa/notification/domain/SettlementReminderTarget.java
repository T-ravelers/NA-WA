package me.nawa.notification.domain;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 리마인더를 보낼 구성원 한 명과, 알림 문장에 넣을 값이다.
 *
 * 대상을 잠그는 쿼리가 한 번에 읽어 온다. 알림은 만들 때 값을 복사해 두는 표라서 이름·약속명·
 * 금액·통화가 필요한데, 잠근 뒤에 따로 조회하면 그 사이에 값이 바뀔 수 있고 조회도 한 번 더
 * 든다.
 */
@Getter
@Setter
@NoArgsConstructor
public class SettlementReminderTarget {
    private Long settlementMemberId;
    /** 알림을 받는 회원. 돈을 내야 하는 사람이다. */
    private Long recipientMemberId;
    private Long settlementId;
    /** 돈을 받을 사람(원결제자)의 이름. 알림에서 "누가 보낸 요청인지"가 된다. */
    private String payerName;
    private String gatheringName;
    /** 받는 회원 본인이 낼 몫이다. 정산 총액이 아니다. */
    private BigDecimal shareAmount;
    /** 정산 통화는 원거래에만 있어서 거기서 가져온다. */
    private String currencyCode;
}
