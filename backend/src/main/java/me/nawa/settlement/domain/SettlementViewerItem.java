package me.nawa.settlement.domain;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** ITEMIZED 정산에서 현재 사용자에게 배분된 품목이다. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SettlementViewerItem {

    private Long settlementItemId;
    private String name;
    private BigDecimal allocatedQuantity;
    private BigDecimal allocatedAmount;
}
