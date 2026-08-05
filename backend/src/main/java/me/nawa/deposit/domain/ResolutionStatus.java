package me.nawa.deposit.domain;

/**
 * 보증금 정산 배치 상태
 *
 * 보증금 정산 배치의 처리 진행 상태를 표현합니다.
 */
public enum ResolutionStatus {

    /**
     * 정산 대기
     *
     * 정산 배치가 생성되었지만 아직 처리를 시작하지 않은 상태입니다.
     */
    PENDING,

    /**
     * 정산 처리 중
     *
     * 보증금 환급, 분배 또는 에스크로 보유 처리를 진행 중인 상태입니다.
     */
    PROCESSING,

    /**
     * 정산 완료
     *
     * 정산에 포함된 보증금 처리가 모두 완료된 최종 상태입니다.
     */
    COMPLETED,

    /**
     * 정산 실패
     *
     * 정산 처리 중 오류가 발생한 상태이며, 재처리할 수 있습니다.
     */
    FAILED;

    /**
     * 정산 배치 상태 전이 가능 여부
     *
     * `PENDING`은 `PROCESSING`으로,
     * `PROCESSING`은 `COMPLETED` 또는 `FAILED`로,
     * `FAILED`는 재처리를 위해 `PROCESSING`으로 변경할 수 있습니다.
     * `COMPLETED`는 최종 상태이므로 다른 상태로 변경할 수 없습니다.
     */
    public boolean canTransitionTo(
        ResolutionStatus nextStatus
    ) {
        if (nextStatus == null) {
            return false;
        }

        return switch (this) {
            case PENDING ->
                nextStatus == PROCESSING;

            case PROCESSING ->
                nextStatus == COMPLETED
                    || nextStatus == FAILED;

            case FAILED ->
                nextStatus == PROCESSING;

            case COMPLETED ->
                false;
        };
    }
}
