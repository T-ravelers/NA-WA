package me.nawa.settlement.domain;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SettlementMember {
    private Long settlementMemberId;
    private Long settlementId;
    private Long appointmentMemberId;
    private Long memberId;
    private BigDecimal shareAmount;
    private String requestStatus;
    private Long paidTransferId;
    private String paymentIdempotencyKey;

    public SettlementMember(
        Long settlementMemberId,
        Long settlementId,
        Long appointmentMemberId,
        Long memberId,
        BigDecimal shareAmount,
        String requestStatus,
        Long paidTransferId
    ) {
        this.settlementMemberId = settlementMemberId;
        this.settlementId = settlementId;
        this.appointmentMemberId = appointmentMemberId;
        this.memberId = memberId;
        this.shareAmount = shareAmount;
        this.requestStatus = requestStatus;
        this.paidTransferId = paidTransferId;
    }
}
