package me.nawa.appointment.domain;

/**
 * 약속의 진행 상태입니다.
 */
public enum AppointmentStatus {
    PAYMENT_PENDING,
    RECRUITING,
    CLOSED,
    CONFIRMED,
    IN_PROGRESS,
    /**
     * 활동 종료 시각이 지났지만 방장이 아직 출석을 확정하지 않은 상태.
     *
     * 표시 전용 값이라 DB `appointments.appointment_status`에는 저장되지 않습니다.
     * DB는 출석 확정 전까지 IN_PROGRESS를 유지하고, 조회 응답이
     * {@code resolveDisplayStatus}에서 시간 기준으로 이 값을 계산해 내보냅니다.
     */
    AWAITING_ATTENDANCE,
    COMPLETED,
    CANCELLED;

    public boolean canTransitionTo(AppointmentStatus nextStatus) {
        if (nextStatus == null) {
            return false;
        }

        return switch (this) {
            case PAYMENT_PENDING ->
                    nextStatus == RECRUITING || nextStatus == CANCELLED;
            case RECRUITING ->
                    nextStatus == CLOSED || nextStatus == CANCELLED;
            // 활동 시작 시각이 되면 방장의 별도 확정 없이 바로 진행중으로 전환한다.
            // CONFIRMED는 이 정책 변경 전에 쓰던 값이라 트리거를 만들지 않는다
            // (CANCELLED와 같은 이유 — 정의는 남기되 도달 경로는 없음).
            // 정원 도달로 CLOSED된 뒤 마감 시각 전에 참여 취소가 생기면
            // 빈자리를 다시 채울 수 있도록 RECRUITING으로 되돌아간다.
            case CLOSED ->
                    nextStatus == RECRUITING
                            || nextStatus == IN_PROGRESS
                            || nextStatus == CANCELLED;
            case IN_PROGRESS -> nextStatus == COMPLETED;
            // AWAITING_ATTENDANCE는 표시 전용이라 DB 상태 전이에 등장하지 않는다.
            case AWAITING_ATTENDANCE, CONFIRMED, COMPLETED, CANCELLED -> false;
        };
    }
}
