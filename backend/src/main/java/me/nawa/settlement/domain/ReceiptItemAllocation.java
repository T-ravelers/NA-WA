package me.nawa.settlement.domain;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 영수증 항목을 약속 참여자에게 배분한 수량과 금액이다. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReceiptItemAllocation {
    private Long receiptAnalysisItemId;
    private Long appointmentMemberId;
    private BigDecimal allocatedQuantity;
    private BigDecimal allocatedAmount;
}
