package me.nawa.settlement.domain;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SettlementItem {
    private Long settlementItemId;
    private Long settlementId;
    private String itemName;
    private BigDecimal unitPrice;
    private BigDecimal quantity;
    private BigDecimal lineTotal;
    private Short sourceOrder;

    public SettlementItem(
        Long settlementId,
        String itemName,
        BigDecimal unitPrice,
        BigDecimal quantity,
        BigDecimal lineTotal,
        Short sourceOrder
    ) {
        this.settlementId = settlementId;
        this.itemName = itemName;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
        this.lineTotal = lineTotal;
        this.sourceOrder = sourceOrder;
    }
}
