package me.nawa.settlement.domain;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** OCR 또는 사용자 입력으로 확정한 영수증 항목이다. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReceiptAnalysisItem {
    private Long receiptAnalysisItemId;
    private Long receiptAnalysisId;
    private String itemName;
    private BigDecimal unitPrice;
    private BigDecimal quantity;
    private BigDecimal lineTotal;
    private Short sourceOrder;
}
