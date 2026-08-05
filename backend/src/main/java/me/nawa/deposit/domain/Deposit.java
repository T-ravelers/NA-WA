package me.nawa.deposit.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 보증금 도메인
 *
 * 보증금의 생성, 예치, 환급, 분배 및 보증금 납부 전 취소 상태 전이를 관리합니다.
 */
@Getter
@NoArgsConstructor
public class Deposit {

    /**
     * 보증금 금액 정책
     *
     * 보증금은 5,000원 이상 50,000원 이하의 정수 금액만 허용합니다.
     */
    private static final BigDecimal MIN_AMOUNT = BigDecimal.valueOf(5_000);
    private static final BigDecimal MAX_AMOUNT = BigDecimal.valueOf(50_000);

    /**
     * 보증금 영속화 필드
     *
     * `deposits` 테이블의 컬럼과 매핑되는 값을 보관합니다.
     */
    private Long depositId;
    private Long appointmentMemberId;
    private Long heldTransferId;
    private BigDecimal amount;
    private DepositStatus depositStatus;
    private LocalDateTime heldAt;
    private LocalDateTime resolvedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;

    /**
     * 보증금 생성
     *
     * 약속에 참여한 회원의 보증금 행을 생성하고,
     * 보증금 상태를 `PENDING`으로 초기화합니다.
     */
    private Deposit(Long appointmentMemberId, BigDecimal amount) {
        this.appointmentMemberId = requirePositive(
            appointmentMemberId,
            "약속 참여 회원 ID"
        );
        this.amount = validateAmount(amount);
        this.depositStatus = DepositStatus.PENDING;
    }

    /**
     * 보증금 생성
     *
     * 약속에 참여한 회원의 보증금을 납부 대기 상태로 생성합니다.
     */
    public static Deposit pending(
        Long appointmentMemberId,
        BigDecimal amount
    ) {
        return new Deposit(appointmentMemberId, amount);
    }

    /**
     * 보증금 예치 완료 반영
     *
     * 회원 지갑에서 `SYSTEM_ESCROW`로 보증금 이체가 완료되면
     * `PENDING`에서 `HELD`로 변경합니다.
     * 이때 전달받은 이체 ID는 완료된 보증금 예치 이체를 가리켜야 합니다.
     */
    public void hold(
        Long transferId,
        LocalDateTime heldAt
    ) {
        requirePositive(transferId, "보증금 예치 이체 ID");
        Objects.requireNonNull(
            heldAt,
            "보증금 예치 시각은 null이 될 수 없습니다."
        );

        transitionTo(DepositStatus.HELD);

        this.heldTransferId = transferId;
        this.heldAt = heldAt;
    }

    /**
     * 보증금 납부 전 취소
     *
     * 보증금이 아직 `HELD` 되기 전에 약속이 취소된 경우
     * `PENDING`에서 `CANCELLED`로 변경합니다.
     */
    public void cancel(LocalDateTime resolvedAt) {
        Objects.requireNonNull(
            resolvedAt,
            "resolvedAt은 null이 될 수 없습니다."
        );

        transitionTo(DepositStatus.CANCELLED);

        this.heldTransferId = null;
        this.heldAt = null;
        this.resolvedAt = resolvedAt;
    }

    /**
     * 보증금 환급 완료
     *
     * 약속에 참석한 회원의 예치된 보증금 환급이 완료되면
     * `HELD`에서 `REFUNDED`로 변경합니다.
     */
    public void refund(LocalDateTime resolvedAt) {
        resolveAs(DepositStatus.REFUNDED, resolvedAt);
    }

    /**
     * 노쇼 보증금 분배 완료
     *
     * 노쇼 회원의 예치된 보증금이 참석 회원에게 분배되면
     * `HELD`에서 `DISTRIBUTED`로 변경합니다.
     */
    public void distribute(LocalDateTime resolvedAt) {
        resolveAs(DepositStatus.DISTRIBUTED, resolvedAt);
    }


    /**
     * 보증금 납부 대기 여부
     *
     * 보증금이 아직 예치되지 않은 `PENDING` 상태인지 확인합니다.
     */
    public boolean isPending() {
        return depositStatus == DepositStatus.PENDING;
    }

    /**
     * 보증금 예치 여부
     *
     * 보증금이 회원 지갑에서 에스크로로 이동한 `HELD` 상태인지 확인합니다.
     */
    public boolean isHeld() {
        return depositStatus == DepositStatus.HELD;
    }

    /**
     * 보증금 처리 완료 여부
     *
     * 보증금 환급, 노쇼 보증금 분배 또는 보증금 납부 전 약속 취소가
     * 완료된 최종 상태인지 확인합니다.
     */
    public boolean isResolved() {
        return depositStatus == DepositStatus.REFUNDED
            || depositStatus == DepositStatus.DISTRIBUTED
            || depositStatus == DepositStatus.CANCELLED;
    }


    /**
     * 보증금 처리 완료
     *
     * 환급 또는 분배 완료 상태로 변경하고 처리 완료 시각을 기록합니다.
     */
    private void resolveAs(
        DepositStatus targetStatus,
        LocalDateTime resolvedAt
    ) {
        Objects.requireNonNull(
            resolvedAt,
            "resolvedAt은 null이 될 수 없습니다."
        );

        transitionTo(targetStatus);
        this.resolvedAt = resolvedAt;
    }


    /**
     * 보증금 상태 전이
     *
     * 현재 상태에서 허용된 다음 상태로만 변경하며,
     * 허용되지 않는 상태 전이는 예외로 처리합니다.
     */
    private void transitionTo(DepositStatus targetStatus) {
        if (depositStatus == null
            || !depositStatus.canTransitionTo(targetStatus)) {
            throw new IllegalStateException(
                "허용되지 않는 보증금 상태 전이 : "
                    + depositStatus
                    + " -> "
                    + targetStatus
            );
        }

        this.depositStatus = targetStatus;
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
     * 보증금 금액 검증
     *
     * 보증금 금액이 허용 범위에 포함되고 정수인지 확인합니다.
     */
    private static BigDecimal validateAmount(BigDecimal amount) {
        if (amount == null
            || amount.compareTo(MIN_AMOUNT) < 0
            || amount.compareTo(MAX_AMOUNT) > 0
            || amount.remainder(BigDecimal.ONE)
            .compareTo(BigDecimal.ZERO) != 0) {
            throw new IllegalArgumentException(
                "보증금 금액은 5,000원 이상 50,000원 이하의 정수여야 합니다."
            );
        }

        return amount;
    }
}
