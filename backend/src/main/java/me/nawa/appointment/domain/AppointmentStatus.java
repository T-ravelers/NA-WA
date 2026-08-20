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
            case CONFIRMED, COMPLETED, CANCELLED -> false;
        };
    }
}
