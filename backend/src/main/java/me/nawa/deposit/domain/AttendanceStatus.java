package me.nawa.deposit.domain;

/**
 * 약속 참여 회원의 출석 상태입니다.
 *
 * 보증금 지급 시점의 출석 상태를 스냅샷으로 보관하여
 * 이후 출석 상태가 변경되더라도 지급 당시의 판단 근거를 유지합니다.
 */
public enum AttendanceStatus {

    /**
     * 아직 출석 여부가 확정되지 않은 상태입니다.
     */
    PENDING,

    /**
     * 약속에 참석한 상태입니다.
     */
    ATTENDED,

    /**
     * 약속에 참석하지 않은 상태입니다.
     */
    NO_SHOW
}
