package me.nawa.settlement.domain;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 정산 목록 한 행에 필요한 최소 조회 모델이다. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SettlementSummary {
    private Long settlementId;
    private String title;
    private BigDecimal amount;
    private String splitMethod;
    private String settlementStatus;
}
