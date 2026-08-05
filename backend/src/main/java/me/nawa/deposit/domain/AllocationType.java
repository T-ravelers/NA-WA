package me.nawa.deposit.domain;

/**
 * 보증금 지급 분배 유형입니다.
 *
 * 보증금 원소유자와 지급 대상자, 약속 종료 사유에 따라
 * 보증금이 어떤 목적으로 지급되었는지 구분합니다.
 */
public enum AllocationType {

    /**
     * 출석한 회원에게 본인의 보증금을 환급하는 유형입니다.
     */
    SELF_REFUND,

    /**
     * 노쇼 회원의 보증금을 출석 회원에게 분배하는 유형입니다.
     */
    NO_SHOW_SHARE,

    /**
     * 약속 취소로 보증금 원소유자에게 환급하는 유형입니다.
     *
     * 약속 취소에 따른 환급이므로 출석 상태에는 제한을 두지 않습니다.
     */
    CANCELLATION_REFUND
}
