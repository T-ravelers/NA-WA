package me.nawa.settlement.dto.response;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

/**
 * 정산 목록 응답
 *
 * 회원이 받은 정산 요청과 보낸 정산 요청을 각각 목록으로 반환합니다.
 */
@Getter
@Builder
public class SettlementListResponse {

    private final List<SettlementSummaryResponse> received;
    private final List<SettlementSummaryResponse> sent;
}
