package me.nawa.settlement.dto.response;

import lombok.Builder;
import lombok.Getter;

/** 요청·지급 mutation 이후 서버가 확정한 현재 사용자 상태다. */
@Getter
@Builder
public class SettlementMutationResponse {

    private final Long settlementId;
    private final String settlementStatus;
    private final Long transferId;
    private final SettlementViewerResponse viewer;
}
