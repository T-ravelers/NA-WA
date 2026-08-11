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
public class ReceiptAnalysis {
    private Long receiptAnalysisId;
    private Long sourceTransferId;
    private Long appointmentId;
    private Long createdByMemberId;
    private String originalFileName;
    private String analysisStatus;
    private BigDecimal recognizedTotal;
}
