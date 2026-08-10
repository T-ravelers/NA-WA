package me.nawa.settlement.dto.response;

import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ReceiptAnalysisResponse {

    private final Long receiptAnalysisId;
    private final BigDecimal recognizedTotal;
    private final List<ReceiptAnalysisItemResponse> items;
}
