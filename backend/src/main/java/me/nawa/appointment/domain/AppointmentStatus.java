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
            case CLOSED ->
                    nextStatus == CONFIRMED || nextStatus == CANCELLED;
            case CONFIRMED ->
                    nextStatus == IN_PROGRESS || nextStatus == CANCELLED;
            case IN_PROGRESS -> nextStatus == COMPLETED;
            case COMPLETED, CANCELLED -> false;
        };
    }
}
