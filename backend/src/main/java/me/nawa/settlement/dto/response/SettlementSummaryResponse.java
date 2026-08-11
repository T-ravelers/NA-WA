package me.nawa.settlement.dto.response;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

/**
 * 정산 요약 응답
 *
 * 정산 목록에 표시할 식별자, 제목, 금액, 유형 및 상태를 반환합니다.
 */
@Getter
@Builder
public class SettlementSummaryResponse {

    private final Long id;
    private final String title;
    private final BigDecimal amount;
    private final String type;
    private final String status;
}
