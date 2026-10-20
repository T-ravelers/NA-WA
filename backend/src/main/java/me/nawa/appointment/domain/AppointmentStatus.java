package me.nawa.appointment.domain;

/**
 * 약속의 진행 상태입니다.
 *
 * <p>허용 전이를 코드 규칙표로 두지 않습니다. 실제로 막는 것은 상태를 바꾸는 조건부
 * UPDATE({@code WHERE appointment_status = 이전 상태})이고, 전이 전체의 정본은
 * {@code backend/docs/APPOINTMENT_DEPOSIT_STATE_MACHINE.md} 2절입니다. 호출되지 않는
 * 규칙표는 실제 동작과 어긋나도 아무것도 잡지 못해 지웠습니다(#388).
 */
public enum AppointmentStatus {
    PAYMENT_PENDING,
    RECRUITING,
    /**
     * 정원이 모두 찬 상태.
     *
     * 시간으로는 도달하지 않습니다 — 참여 마감 시각을 없앤 뒤로 이 값은 정원
     * 충족만 뜻합니다. 정원이 차지 않은 약속은 {@code RECRUITING}인 채로 활동
     * 시작 시각을 맞습니다.
     */
    FULL,
    IN_PROGRESS,
    /**
     * 활동 종료 시각이 지났지만 방장이 아직 출석을 확정하지 않은 상태.
     *
     * 활동 종료 시각이 지난 IN_PROGRESS 약속을
     * {@link me.nawa.appointment.service.AppointmentLifecycleScheduler}가 이 값으로
     * 옮겨 DB에 저장합니다.
     */
    AWAITING_ATTENDANCE,
    COMPLETED,
    CANCELLED
}
