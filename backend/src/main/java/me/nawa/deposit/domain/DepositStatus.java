package me.nawa.deposit.domain;

public enum DepositStatus {

    /**
     * 보증금 상태
     *
     * `PENDING`은 약속 참여 직후, 보증금 납부 대기 상태입니다.
     * `HELD`는 보증금 예치가 완료되어 에스크로에 보관 중인 상태입니다.
     * `REFUNDED`는 보증금 환급이 완료된 상태입니다.
     * `DISTRIBUTED`는 노쇼 회원의 보증금이 참석 회원에게 분배 완료된 상태입니다.
     * `CANCELLED`는 보증금 납부 전에 약속이 취소되어 종료된 상태입니다.
     */
    PENDING,
    HELD,
    /**
     * 보증금 환급 완료 조건
     *
     * 1. 출석 처리된 회원의 보증금 환급이 완료된 경우
     * 2. 보증금이 예치된 후 호스트가 약속을 취소하여
     *    보증금 환급이 완료된 경우
     */
    REFUNDED,
    DISTRIBUTED,
    CANCELLED;

    /**
     * 보증금 상태 전이 가능 여부
     *
     * 현재 보증금 상태에서 허용된 다음 상태로 전이할 수 있는지 확인합니다.
     *
     * @param nextStatus 전이할 다음 상태
     * @return 상태 전이가 가능하면 true, 그렇지 않으면 false
     */
    public boolean canTransitionTo(DepositStatus nextStatus) {
        if (nextStatus == null) {
            return false;
        }

        return switch (this) {
            case PENDING ->
                nextStatus == HELD || nextStatus == CANCELLED;

            case HELD ->
                nextStatus == REFUNDED || nextStatus == DISTRIBUTED;

            // 참여 취소로 환급된 회원이 마감 시각 전에 재참여하면, 같은
            // 보증금 행을 재활용해 PENDING으로 되돌린다(appointment_member_id
            // UNIQUE 제약 때문에 새 보증금 행을 만들 수 없음).
            case REFUNDED ->
                nextStatus == PENDING;

            case DISTRIBUTED, CANCELLED ->
                false;
        };
    }
}
