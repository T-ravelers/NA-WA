package me.nawa.settlement.service;

/** 정산 부담금 결제와 취소를 정의한다. */
public interface SettlementPaymentService {
    void paySettlement(Long memberId, Long settlementId);
    void cancelSettlement(Long memberId, Long settlementId);
}
