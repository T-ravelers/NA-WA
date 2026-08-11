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
public class SettlementItem {
    private Long settlementItemId;
    private Long settlementId;
    private String itemName;
    private BigDecimal unitPrice;
    private BigDecimal quantity;
    private BigDecimal lineTotal;
    private Short sourceOrder;
}
