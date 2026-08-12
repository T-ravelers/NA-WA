package me.nawa.settlement.domain;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 정산 상세 화면을 조립하기 위한 조회 모델이다. */
@Getter
@Setter
@NoArgsConstructor
public class SettlementDetail {
    private Long settlementId;
    private String splitMethod;
    private BigDecimal totalAmount;
    private String settlementStatus;
    private String requestedBy;
    private String gatheringName;
    private String merchantName;
    private String transactionNumber;
    private String paidBy;
    private Long createdByMemberId;
    private BigDecimal viewerShareAmount;
    private String viewerRequestStatus;

    public SettlementDetail(
        Long settlementId,
        String splitMethod,
        BigDecimal totalAmount,
        String settlementStatus,
        String requestedBy,
        String gatheringName,
        String merchantName,
        String transactionNumber,
        String paidBy
    ) {
        this.settlementId = settlementId;
        this.splitMethod = splitMethod;
        this.totalAmount = totalAmount;
        this.settlementStatus = settlementStatus;
        this.requestedBy = requestedBy;
        this.gatheringName = gatheringName;
        this.merchantName = merchantName;
        this.transactionNumber = transactionNumber;
        this.paidBy = paidBy;
    }
}
