package me.nawa.settlement.service;

import me.nawa.settlement.dto.response.SettlementMutationResponse;

/** 정산 부담금 결제를 정의한다. */
public interface SettlementPaymentService {
    SettlementMutationResponse paySettlement(
        Long memberId,
        Long settlementId,
        String idempotencyKey
    );
}
