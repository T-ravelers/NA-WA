package me.nawa.deposit.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 보증금 지급 도메인
 *
 * 보증금 환급 또는 노쇼 보증금 분배 결과를 보관하고,
 * 지급 유형별 생성 규칙을 검증합니다.
 */
@Getter
@NoArgsConstructor
public class DepositPayout {

    private Long depositPayoutId;
    private Long sourceDepositId;
    private Long recipientAppointmentMemberId;
    private Long transferId;
    private Long depositPayoutBatchId;

    private AllocationType allocationType;
    private AttendanceStatus sourceAttendanceStatusSnapshot;
    private AttendanceStatus recipientAttendanceStatusSnapshot;

    private BigDecimal amount;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;

    private DepositPayout(
        Long sourceDepositId,
        Long sourceAppointmentMemberId,
        Long recipientAppointmentMemberId,
        Long transferId,
        Long depositPayoutBatchId,
        AllocationType allocationType,
        AttendanceStatus sourceAttendanceStatusSnapshot,
        AttendanceStatus recipientAttendanceStatusSnapshot,
        BigDecimal amount
    ) {
        this.sourceDepositId = requirePositive(
            sourceDepositId,
            "원천 보증금 ID"
        );
        this.recipientAppointmentMemberId = requirePositive(
            recipientAppointmentMemberId,
            "수취 약속 참여 회원 ID"
        );
        this.transferId = requirePositive(
            transferId,
            "지급 이체 ID"
        );
        this.depositPayoutBatchId = requirePositive(
            depositPayoutBatchId,
            "정산 배치 ID"
        );

        this.allocationType = Objects.requireNonNull(
            allocationType,
            "지급 유형은 null이 될 수 없습니다."
        );
        this.sourceAttendanceStatusSnapshot =
            Objects.requireNonNull(
                sourceAttendanceStatusSnapshot,
                "원천 출석 상태 스냅샷은 null이 될 수 없습니다."
            );
        this.recipientAttendanceStatusSnapshot =
            Objects.requireNonNull(
                recipientAttendanceStatusSnapshot,
                "수취 출석 상태 스냅샷은 null이 될 수 없습니다."
            );
        this.amount = validateAmount(amount);

        validateCancellationRefundAttendanceSnapshots();
        validateAllocationRule(
            sourceAppointmentMemberId,
            recipientAppointmentMemberId
        );
    }

    /**
     * 자기 보증금 환급
     *
     * 출석 회원의 보증금을 본인에게 환급하며,
     * 원천 회원과 수취 회원은 동일하고 두 출석 상태는 `ATTENDED`여야 합니다.
     */
    public static DepositPayout selfRefund(
        Long sourceDepositId,
        Long sourceAppointmentMemberId,
        Long recipientAppointmentMemberId,
        Long transferId,
        Long depositPayoutBatchId,
        BigDecimal amount
    ) {
        return new DepositPayout(
            sourceDepositId,
            sourceAppointmentMemberId,
            recipientAppointmentMemberId,
            transferId,
            depositPayoutBatchId,
            AllocationType.SELF_REFUND,
            AttendanceStatus.ATTENDED,
            AttendanceStatus.ATTENDED,
            amount
        );
    }

    /**
     * 노쇼 보증금 분배
     *
     * 노쇼 회원의 보증금을 출석 회원에게 분배하며,
     * 원천 출석 상태는 `NO_SHOW`, 수취 출석 상태는 `ATTENDED`여야 합니다.
     */
    public static DepositPayout noShowShare(
        Long sourceDepositId,
        Long sourceAppointmentMemberId,
        Long recipientAppointmentMemberId,
        Long transferId,
        Long depositPayoutBatchId,
        BigDecimal amount
    ) {
        return new DepositPayout(
            sourceDepositId,
            sourceAppointmentMemberId,
            recipientAppointmentMemberId,
            transferId,
            depositPayoutBatchId,
            AllocationType.NO_SHOW_SHARE,
            AttendanceStatus.NO_SHOW,
            AttendanceStatus.ATTENDED,
            amount
        );
    }

    /**
     * 약속 취소 보증금 환급
     *
     * 약속이 취소된 경우 원천 회원에게 보증금을 환급합니다.
     * 출석 상태는 당시 스냅샷을 보관하며 두 스냅샷은 동일해야 합니다.
     */
    public static DepositPayout cancellationRefund(
        Long sourceDepositId,
        Long sourceAppointmentMemberId,
        Long recipientAppointmentMemberId,
        Long transferId,
        Long depositPayoutBatchId,
        AttendanceStatus sourceAttendanceStatus,
        AttendanceStatus recipientAttendanceStatus,
        BigDecimal amount
    ) {
        return new DepositPayout(
            sourceDepositId,
            sourceAppointmentMemberId,
            recipientAppointmentMemberId,
            transferId,
            depositPayoutBatchId,
            AllocationType.CANCELLATION_REFUND,
            sourceAttendanceStatus,
            recipientAttendanceStatus,
            amount
        );
    }

    /**
     * 자기 보증금 환급 여부
     *
     * 지급 유형이 `SELF_REFUND`인지 확인합니다.
     */
    public boolean isSelfRefund() {
        return allocationType == AllocationType.SELF_REFUND;
    }

    /**
     * 노쇼 보증금 분배 여부
     *
     * 지급 유형이 `NO_SHOW_SHARE`인지 확인합니다.
     */
    public boolean isNoShowShare() {
        return allocationType == AllocationType.NO_SHOW_SHARE;
    }

    /**
     * 약속 취소 환급 여부
     *
     * 지급 유형이 `CANCELLATION_REFUND`인지 확인합니다.
     */
    public boolean isCancellationRefund() {
        return allocationType == AllocationType.CANCELLATION_REFUND;
    }

    /**
     * 약속 취소 환급 출석 상태 스냅샷 검증
     *
     * 약속 취소 환급의 원천 출석 상태와 수취 출석 상태 스냅샷이
     * 동일한지 확인합니다.
     */
    private void validateCancellationRefundAttendanceSnapshots() {
        if (allocationType == AllocationType.CANCELLATION_REFUND
            && sourceAttendanceStatusSnapshot
            != recipientAttendanceStatusSnapshot) {
            throw new IllegalArgumentException(
                "약속 취소 환급의 출석 상태 스냅샷은 동일해야 합니다."
            );
        }
    }

    /**
     * 지급 유형 규칙 검증
     *
     * 자기 환급과 약속 취소 환급은 원천 회원과 수취 회원이 동일해야 하며,
     * 노쇼 분배는 원천 회원과 수취 회원이 달라야 합니다.
     */
    private void validateAllocationRule(
        Long sourceAppointmentMemberId,
        Long recipientAppointmentMemberId
    ) {
        requirePositive(
            sourceAppointmentMemberId,
            "원천 약속 참여 회원 ID"
        );

        switch (allocationType) {
            case SELF_REFUND, CANCELLATION_REFUND ->
                requireSameMember(
                    sourceAppointmentMemberId,
                    recipientAppointmentMemberId
                );

            case NO_SHOW_SHARE ->
                requireDifferentMember(
                    sourceAppointmentMemberId,
                    recipientAppointmentMemberId
                );
        }
    }

    /**
     * 동일 회원 여부 검증
     *
     * 원천 회원과 수취 회원이 동일한지 확인합니다.
     */
    private static void requireSameMember(
        Long sourceAppointmentMemberId,
        Long recipientAppointmentMemberId
    ) {
        if (!sourceAppointmentMemberId.equals(
            recipientAppointmentMemberId
        )) {
            throw new IllegalArgumentException(
                "원천 회원과 수취 회원은 동일해야 합니다."
            );
        }
    }

    /**
     * 다른 회원 여부 검증
     *
     * 노쇼 보증금 분배의 원천 회원과 수취 회원이 다른지 확인합니다.
     */
    private static void requireDifferentMember(
        Long sourceAppointmentMemberId,
        Long recipientAppointmentMemberId
    ) {
        if (sourceAppointmentMemberId.equals(
            recipientAppointmentMemberId
        )) {
            throw new IllegalArgumentException(
                "노쇼 보증금 분배의 수취 회원은 원천 회원과 달라야 합니다."
            );
        }
    }

    /**
     * 지급 금액 검증
     *
     * 지급 금액은 0보다 큰 정수 금액만 허용합니다.
     */
    private static BigDecimal validateAmount(BigDecimal amount) {
        if (amount == null
            || amount.signum() <= 0
            || amount.remainder(BigDecimal.ONE)
            .compareTo(BigDecimal.ZERO) != 0) {
            throw new IllegalArgumentException(
                "지급 금액은 0보다 큰 정수 금액이어야 합니다."
            );
        }

        return amount;
    }

    /**
     * 식별자 검증
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
}
