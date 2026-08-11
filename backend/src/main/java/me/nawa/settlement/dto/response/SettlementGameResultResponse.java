package me.nawa.settlement.dto.response;

import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

/**
 * 게임 정산 결과 응답
 *
 * 게임으로 확정된 부담자와 정산 금액을 반환합니다.
 */
@Getter
@Builder
public class SettlementGameResultResponse {

    private final Long settlementId;
    private final BigDecimal amount;
    private final List<SettlementParticipantResponse> liableParticipants;
}
