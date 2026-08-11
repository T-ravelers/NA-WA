package me.nawa.settlement.domain;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 정산 상세 화면을 조립하기 위한 조회 모델이다. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
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
}
