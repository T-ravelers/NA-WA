package me.nawa.settlement.event;

/**
 * 구성원 한 명이 자기 몫을 냈다.
 *
 * 이미 낸 사람이 같은 멱등키로 다시 요청한 경로에서는 발행하지 않는다. 그 요청은 돈을
 * 옮기지 않으므로 알릴 일도 없다.
 */
public record SettlementPaidEvent(Long settlementId, Long paidByMemberId) {
}
