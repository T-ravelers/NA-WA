package me.nawa.settlement.dto.response;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ReceiptAnalysisItemResponse {

    private final Long id;
    private final String name;
    private final BigDecimal quantity;
    private final BigDecimal unitPrice;
}
