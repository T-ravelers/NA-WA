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
public class SettlementItemShare {
    private Long settlementItemShareId;
    private Long settlementItemId;
    private Long appointmentMemberId;
    private BigDecimal allocatedQuantity;
    private BigDecimal allocatedAmount;
}
