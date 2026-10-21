package me.nawa.appointment.domain;

/**
 * 약속 참가자의 참여 상태입니다.
 *
 * <p>허용 전이를 코드 규칙표로 두지 않습니다. 실제로 막는 것은 참여·취소 쿼리의 조건이고,
 * 전이 전체의 정본은 {@code backend/docs/APPOINTMENT_DEPOSIT_STATE_MACHINE.md} 3절입니다.
 * 지운 규칙표는 호출되지 않은 채 {@code LEFT}에서 나가는 길을 막아 두었지만, 실제로는
 * 활동 시작 전 재참여가 {@code LEFT → PENDING}으로 동작합니다(#388).
 */
public enum MembershipStatus {
    PENDING,
    ACTIVE,
    LEFT
}
