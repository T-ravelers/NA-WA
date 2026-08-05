package me.nawa.deposit.domain;

/**
 * 보증금 정산 사유
 *
 * 약속의 종료 사유에 따라 보증금 정산 방식을 구분합니다.
 */
public enum ResolutionReason {

    /**
     * 약속이 정상적으로 완료되어 출석 결과에 따라 정산하는 경우입니다.
     */
    APPOINTMENT_COMPLETED,

    /**
     * 약속이 취소되어 예치된 보증금을 환급하는 경우입니다.
     */
    APPOINTMENT_CANCELLED
}
