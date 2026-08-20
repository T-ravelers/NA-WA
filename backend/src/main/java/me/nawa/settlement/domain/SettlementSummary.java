package me.nawa.settlement.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
    private LocalDateTime createdAt;
    /** 정산이 끝난 시각. 아직 진행 중이면 비어 있다. */
    private LocalDateTime completedAt;

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
