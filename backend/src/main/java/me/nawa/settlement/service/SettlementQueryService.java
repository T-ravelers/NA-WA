package me.nawa.settlement.service;

import java.util.List;
import me.nawa.settlement.dto.response.SettlementCandidateResponse;
import me.nawa.settlement.dto.response.SettlementDetailResponse;
import me.nawa.settlement.dto.response.SettlementListResponse;

/** 정산 목록, 후보, 상세 화면이 사용하는 조회 작업을 정의한다. */
public interface SettlementQueryService {
    SettlementListResponse getSettlements(Long memberId);
    List<SettlementCandidateResponse> getCandidates(Long memberId);
    SettlementDetailResponse getSettlement(Long memberId, Long settlementId);
}
