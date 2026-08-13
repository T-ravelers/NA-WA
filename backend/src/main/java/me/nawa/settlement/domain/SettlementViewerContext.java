package me.nawa.settlement.domain;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

/** 현재 사용자 관점의 금액, 상태와 허용 동작을 계산할 입력이다. */
@Getter
@Builder
public class SettlementViewerContext {

    private final String role;
    private final BigDecimal shareAmount;
    private final String requestStatus;
    private final String settlementStatus;
}
