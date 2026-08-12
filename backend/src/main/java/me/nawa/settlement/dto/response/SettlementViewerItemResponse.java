package me.nawa.settlement.dto.response;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

/** ITEMIZED 정산에서 현재 사용자에게 배분된 품목 응답이다. */
@Getter
@Builder
public class SettlementViewerItemResponse {

    private final Long settlementItemId;
    private final String name;
    private final BigDecimal allocatedQuantity;
    private final BigDecimal allocatedAmount;
}
