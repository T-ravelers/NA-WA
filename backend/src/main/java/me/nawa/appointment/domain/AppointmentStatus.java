package me.nawa.appointment.domain;

/**
 * 약속의 진행 상태입니다.
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
    CANCELLED;

    public boolean canTransitionTo(AppointmentStatus nextStatus) {
        if (nextStatus == null) {
            return false;
        }

        return switch (this) {
            case PAYMENT_PENDING ->
                    nextStatus == RECRUITING || nextStatus == CANCELLED;
            // 활동 시작 시각이 되면 방장의 별도 확정 없이 바로 진행중으로
            // 전환한다. 정원이 차지 않은 약속은 FULL을 거치지 않으므로
            // RECRUITING에서 곧장 IN_PROGRESS로 간다 — 이 경로가 없으면 정원
            // 미달 약속이 활동 시작 뒤에도 RECRUITING에 남는다.
            case RECRUITING ->
                    nextStatus == FULL
                            || nextStatus == IN_PROGRESS
                            || nextStatus == CANCELLED;
            // 정원이 차서 FULL이 된 뒤 활동 시작 전에 참여 취소가 생기면
            // 빈자리를 다시 채울 수 있도록 RECRUITING으로 되돌아간다.
            case FULL ->
                    nextStatus == RECRUITING
                            || nextStatus == IN_PROGRESS
                            || nextStatus == CANCELLED;
            // 스케줄러가 출석 확정 대기로 옮기기 전 몇 초 사이에도 화면은 이미
            // 출석 확정을 열어 주므로, 완료로 가는 길을 함께 둔다.
            case IN_PROGRESS ->
                    nextStatus == AWAITING_ATTENDANCE
                            || nextStatus == COMPLETED;
            case AWAITING_ATTENDANCE -> nextStatus == COMPLETED;
            case COMPLETED, CANCELLED -> false;
        };
    }
}
