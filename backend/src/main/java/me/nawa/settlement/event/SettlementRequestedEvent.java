package me.nawa.settlement.event;

/**
 * 정산이 새로 만들어졌다.
 *
 * 정산 번호만 싣는다. 알림에 무슨 글자가 들어갈지는 알림 쪽이 정할 일이라, 정산 서비스가
 * 그것까지 알고 있으면 알림 문구가 바뀔 때마다 정산 코드를 고쳐야 한다.
 *
 * 멱등 재시도로 기존 정산을 그대로 돌려주는 경로에서는 발행하지 않는다.
 */
public record SettlementRequestedEvent(Long settlementId) {
}
