package me.nawa.deposit.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 보증금 정산 배치 도메인
 *
 * 하나의 약속에 대한 보증금 정산을 묶어 관리하고,
 * 정산 상태 전이와 정산 금액의 합게 규칙을 검증합니다.
 */
@Getter
@NoArgsConstructor
public class DepositPayoutBatch {

    /**
     * 금액 초기값
     *
     * 정산 배치를 생성할 때 아직 처리되지 않은 금액을 0원으로 초기화합니다.
     */
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    /**
     * 정산 배치 영속화 필드
     *
     * `deposit_payout_batches` 테이블의 컬럼과 매핑되는 값을 보관합니다.
     */
    private Long depositPayoutBatchId;
    private Long appointmentId;
    private Long resolvedByMemberId;

    /**
     * 정산 사유
     *
     * 약속 완료에 따른 정산인지 약속 취소에 따른 정산인지 구분합니다.
     */
    private ResolutionReason resolutionReason;

    /**
     * 정산 배치 상태
     *
     * `PENDING`은 정산 대기, `PROCESSING`은 정산 처리 중,
     * `COMPLETED`는 정산 완료, `FAILED`는 정산 실패를 의미합니다.
     */
    private ResolutionStatus resolutionStatus;

    private BigDecimal totalHeldAmount;
    private BigDecimal totalRefundedAmount;
    private BigDecimal totalNoShowAmount;
    private BigDecimal totalNoShowDistributedAmount;

    private LocalDateTime attendanceSnapshotAt;
    private String idempotencyKey;
    private LocalDateTime resolvedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;

    /**
     * 정산 배치 생성
     *
     * 약속의 보증금 정산에 필요한 기준 정보와 중복 처리 방지 키를 저장하고,
     * 정산 상태를 `PENDING`으로 초기화합니다.
     */
    private DepositPayoutBatch(
        Long appointmentId,
        ResolutionReason resolutionReason,
        BigDecimal totalHeldAmount,
        LocalDateTime attendanceSnapshotAt,
        String idempotencyKey
    ) {
        this.appointmentId = requirePositive(
            appointmentId,
            "약속 ID"
        );
        this.resolutionReason = requireNonNull(
            resolutionReason,
            "정산 사유"
        );
        this.totalHeldAmount = validateAmount(
            totalHeldAmount,
            "총 예치 금액"
        );
        this.attendanceSnapshotAt = requireNonNull(
            attendanceSnapshotAt,
            "출석 상태 스냅샷 시각"
        );
        this.idempotencyKey = validateIdempotencyKey(idempotencyKey);

        this.resolutionStatus = ResolutionStatus.PENDING;
        this.totalRefundedAmount = ZERO;
        this.totalNoShowAmount = ZERO;
        this.totalNoShowDistributedAmount = ZERO;
    }

    /**
     * 정산 배치 생성
     *
     * 약속의 보증금 정산을 대기 상태로 생성합니다.
     */
    public static DepositPayoutBatch pending(
        Long appointmentId,
        ResolutionReason resolutionReason,
        BigDecimal totalHeldAmount,
        LocalDateTime attendanceSnapshotAt,
        String idempotencyKey
    ) {
        return new DepositPayoutBatch(
            appointmentId,
            resolutionReason,
            totalHeldAmount,
            attendanceSnapshotAt,
            idempotencyKey
        );
    }

    /**
     * 정산 처리 시작
     *
     * 정산 대기 또는 정산 실패 상태의 배치를 처리 중 상태로 변경합니다.
     * 실패한 배치는 동일한 멱등성 키를 기준으로 재처리할 수 있습니다.
     */
    public void startProcessing() {
        transitionTo(ResolutionStatus.PROCESSING);
    }

    /**
     * 정산 배치 완료
     *
     * 정산 처리 중인 배치를 완료 상태로 변경하고 처리 결과를 기록합니다.
     * 총 환급 금액과 총 노쇼 금액의 합은 총 예치 금액과 같아야 하며,
     * 총 노쇼 분배 금액은 총 노쇼 금액과 같아야 합니다.
     */
    public void complete(
        BigDecimal totalRefundedAmount,
        BigDecimal totalNoShowAmount,
        BigDecimal totalNoShowDistributedAmount,
        Long resolvedByMemberId,
        LocalDateTime resolvedAt
    ) {
        requireTransitionTo(ResolutionStatus.COMPLETED);

        BigDecimal refunded = validateAmount(
            totalRefundedAmount,
            "총 환급 금액"
        );
        BigDecimal noShow = validateAmount(
            totalNoShowAmount,
            "총 노쇼 금액"
        );
        BigDecimal distributed = validateAmount(
            totalNoShowDistributedAmount,
            "총 노쇼 분배 금액"
        );

        validateCompletedTotals(
            refunded,
            noShow,
            distributed
        );

        if (resolvedByMemberId != null && resolvedByMemberId <= 0) {
            throw new IllegalArgumentException(
                "정산 처리자 ID는 양수 값을 가져야 합니다."
            );
        }

        if (resolvedAt == null) {
            throw new IllegalArgumentException(
                "정산 완료 시각은 입력되어야 합니다."
            );
        }

        this.totalRefundedAmount = refunded;
        this.totalNoShowAmount = noShow;
        this.totalNoShowDistributedAmount = distributed;
        this.resolvedByMemberId = resolvedByMemberId;
        this.resolvedAt = resolvedAt;
        this.resolutionStatus = ResolutionStatus.COMPLETED;
    }

    /**
     * 정산 실패 처리
     *
     * 정산 처리 중 오류가 발생한 배치를 실패 상태로 변경합니다.
     * 이후 `startProcessing()`을 호출해 재처리할 수 있습니다.
     */
    public void fail() {
        transitionTo(ResolutionStatus.FAILED);
    }

    /**
     * 정산 대기 여부
     *
     * 정산 배치가 아직 처리되지 않은 `PENDING` 상태인지 확인합니다.
     */
    public boolean isPending() {
        return resolutionStatus == ResolutionStatus.PENDING;
    }

    /**
     * 정산 처리 중 여부
     *
     * 정산 배치가 현재 `PROCESSING` 상태인지 확인합니다.
     */
    public boolean isProcessing() {
        return resolutionStatus == ResolutionStatus.PROCESSING;
    }

    /**
     * 정산 완료 여부
     *
     * 정산 배치의 모든 보증금 처리가 완료된 `COMPLETED` 상태인지 확인합니다.
     */
    public boolean isCompleted() {
        return resolutionStatus == ResolutionStatus.COMPLETED;
    }

    /**
     * 정산 실패 여부
     *
     * 정산 처리 중 오류가 발생한 `FAILED` 상태인지 확인합니다.
     */
    public boolean isFailed() {
        return resolutionStatus == ResolutionStatus.FAILED;
    }

    /**
     * 정산 합계 검증
     *
     * 정산 완료 시 필요한 금액 합계가 일치하는지 확인하고,
     * 약속 취소 정산에 노쇼 관련 금액이 포함되지 않았는지 검증합니다.
     */
    private void validateCompletedTotals(
        BigDecimal refunded,
        BigDecimal noShow,
        BigDecimal distributed
    ) {
        if (refunded.add(noShow).compareTo(totalHeldAmount) != 0) {
            throw new IllegalArgumentException(
                "총 환급 금액과 총 노쇼 금액의 합은 총 예치 금액과 같아야 합니다."
            );
        }

        if (distributed.compareTo(noShow) != 0) {
            throw new IllegalArgumentException(
                "총 노쇼 분배 금액은 총 노쇼 금액과 같아야 합니다."
            );
        }

        if (resolutionReason == ResolutionReason.APPOINTMENT_CANCELLED
            && (noShow.signum() != 0
            || distributed.signum() != 0)) {
            throw new IllegalArgumentException(
                "약속 취소 정산에는 노쇼 금액 또는 노쇼 분배 금액이 포함될 수 없습니다."
            );
        }
    }

    /**
     * 정산 배치 상태 전이
     *
     * 현재 상태에서 허용된 다음 상태로만 변경하며,
     * 허용되지 않는 상태 전이는 예외로 처리합니다.
     */
    private void transitionTo(ResolutionStatus targetStatus) {
        requireTransitionTo(targetStatus);
        this.resolutionStatus = targetStatus;
    }

    /**
     * 정산 배치 상태 전이 가능 여부 확인
     *
     * 현재 상태에서 대상 상태로 변경할 수 없는 경우 예외를 발생시킵니다.
     */
    private void requireTransitionTo(ResolutionStatus targetStatus) {
        if (resolutionStatus == null
            || !resolutionStatus.canTransitionTo(targetStatus)) {
            throw new IllegalStateException(
                "보증금 정산 배치 상태를 변경할 수 없습니다. "
                    + "현재 상태: "
                    + resolutionStatus
                    + ", 변경 대상 상태: "
                    + targetStatus
            );
        }
    }

    /**
     * 정산 금액 검증
     *
     * 정산 금액이 null이 아니고 0 이상인 정수 금액인지 확인합니다.
     */
    private static BigDecimal validateAmount(
        BigDecimal amount,
        String fieldName
    ) {
        if (amount == null
            || amount.signum() < 0
            || amount.remainder(BigDecimal.ONE)
            .compareTo(BigDecimal.ZERO) != 0) {
            throw new IllegalArgumentException(
                fieldName + "는 0 이상인 정수 금액이어야 합니다."
            );
        }

        return amount;
    }

    /**
     * 양수 식별자 검증
     *
     * 식별자는 null, 0 또는 음수 값을 허용하지 않습니다.
     */
    private static Long requirePositive(
        Long value,
        String fieldName
    ) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(
                fieldName + "는 양수 값을 가져야 합니다."
            );
        }

        return value;
    }

    /**
     * 필수 값 검증
     *
     * 정산 배치 생성에 필요한 값이 입력되었는지 확인합니다.
     */
    private static <T> T requireNonNull(
        T value,
        String fieldName
    ) {
        if (value == null) {
            throw new IllegalArgumentException(
                fieldName + "는 입력되어야 합니다."
            );
        }

        return value;
    }

    /**
     * 멱등성 키 검증
     *
     * 정산 중복 처리를 방지하기 위한 키가 1자 이상 100자 이하인지 확인합니다.
     */
    private static String validateIdempotencyKey(
        String idempotencyKey
    ) {
        if (idempotencyKey == null
            || idempotencyKey.isBlank()
            || idempotencyKey.length() > 100) {
            throw new IllegalArgumentException(
                "정산 중복 처리 방지 키는 1자 이상 100자 이하로 입력해야 합니다."
            );
        }

        return idempotencyKey;
    }
}
