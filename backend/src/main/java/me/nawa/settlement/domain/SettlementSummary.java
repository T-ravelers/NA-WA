package me.nawa.settlement.domain;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 정산 목록 한 행에 필요한 최소 조회 모델이다. */
@Getter
@Setter
@NoArgsConstructor
public class SettlementSummary {
    private Long settlementId;
    private String title;
    private BigDecimal totalAmount;
    private BigDecimal receivableAmount;
    private String splitMethod;
    private String settlementStatus;
    private Long createdByMemberId;
    private BigDecimal viewerShareAmount;
    private String viewerRequestStatus;

    public SettlementSummary(
        Long settlementId,
        String title,
        BigDecimal amount,
        String splitMethod,
        String settlementStatus
    ) {
        this.settlementId = settlementId;
        this.title = title;
        this.totalAmount = amount;
        this.receivableAmount = amount;
        this.splitMethod = splitMethod;
        this.settlementStatus = settlementStatus;
    }
}
