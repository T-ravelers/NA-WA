package me.nawa.settlement.dto.request;

import java.math.BigDecimal;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ItemizedSettlementItemRequest {
    private String name;
    private BigDecimal unitPrice;
    private BigDecimal quantity;
    private List<ItemizedSettlementItemAllocationRequest> allocations;
}
