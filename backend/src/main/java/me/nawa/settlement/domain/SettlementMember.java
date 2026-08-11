package me.nawa.settlement.domain;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SettlementMember {
    private Long settlementMemberId;
    private Long settlementId;
    private Long appointmentMemberId;
    private Long memberId;
    private BigDecimal shareAmount;
    private String requestStatus;
    private Long paidTransferId;
}
