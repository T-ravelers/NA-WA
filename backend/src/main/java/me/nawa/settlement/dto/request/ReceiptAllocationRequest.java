package me.nawa.settlement.dto.request;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ReceiptAllocationRequest {

    private Long itemId;
    private Long participantId;
    private BigDecimal quantity;
}
