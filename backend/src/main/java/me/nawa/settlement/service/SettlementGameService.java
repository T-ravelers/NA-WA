package me.nawa.settlement.service;

import me.nawa.settlement.dto.request.GameConsentRequest;
import me.nawa.settlement.dto.response.SettlementGameResponse;
import me.nawa.settlement.dto.response.SettlementGameResultResponse;

/** 게임형 정산의 동의, 시작, 상태와 결과 조회를 정의한다. */
public interface SettlementGameService {
    void submitGameConsent(Long memberId, Long settlementId, GameConsentRequest request);
    void startGame(Long memberId, Long settlementId);
    SettlementGameResponse getGame(Long memberId, Long settlementId);
    SettlementGameResultResponse getGameResult(Long memberId, Long settlementId);
}
