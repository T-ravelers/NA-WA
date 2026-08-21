package me.nawa.settlement.event;

/**
 * 낼 사람이 모두 내서 정산이 끝났다.
 *
 * 완료로 바꾸는 UPDATE가 실제로 한 줄을 바꿨을 때만 발행한다. 그 UPDATE는 아직 REQUESTED인
 * 정산만 건드리므로, 마지막 지급이 동시에 여러 번 들어와도 한 번만 성공한다. 즉 이 이벤트가
 * 두 번 나갈 일이 없다.
 */
public record SettlementCompletedEvent(Long settlementId) {
}
