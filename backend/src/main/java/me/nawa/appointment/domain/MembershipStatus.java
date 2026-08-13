package me.nawa.appointment.domain;

/**
 * 약속 참가자의 참여 상태입니다.
 */
public enum MembershipStatus {
    PENDING,
    ACTIVE,
    LEFT;

    public boolean canTransitionTo(MembershipStatus nextStatus) {
        if (nextStatus == null) {
            return false;
        }

        return switch (this) {
            case PENDING -> nextStatus == ACTIVE || nextStatus == LEFT;
            case ACTIVE -> nextStatus == LEFT;
            case LEFT -> false;
        };
    }
}
