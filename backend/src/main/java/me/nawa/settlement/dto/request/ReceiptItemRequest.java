package me.nawa.settlement.dto.request;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ReceiptItemRequest {

    private Long id;
    private String name;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
}
