package me.nawa.settlement.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class Settlement {
    private Long settlementId;
    private Long appointmentId;
    private Long createdByMemberId;
    private Long payerMemberId;
    private Long sourceTransferId;
    private String idempotencyKey;
    private String requestFingerprint;
    private String settlementStatus;
    private String splitMethod;
    private BigDecimal totalAmount;
    private BigDecimal payerShareAmount;
    private BigDecimal receivableAmount;
    private LocalDateTime requestedAt;
    private String memo;
    private Long version;

    @Builder
    public Settlement(Long appointmentId, Long createdByMemberId, Long payerMemberId,
            Long sourceTransferId, String idempotencyKey, String requestFingerprint,
            String settlementStatus, String splitMethod,
            BigDecimal totalAmount, BigDecimal payerShareAmount, BigDecimal receivableAmount,
            LocalDateTime requestedAt, String memo) {
        this.appointmentId = appointmentId;
        this.createdByMemberId = createdByMemberId;
        this.payerMemberId = payerMemberId;
        this.sourceTransferId = sourceTransferId;
        this.idempotencyKey = idempotencyKey;
        this.requestFingerprint = requestFingerprint;
        this.settlementStatus = settlementStatus;
        this.splitMethod = splitMethod;
        this.totalAmount = totalAmount;
        this.payerShareAmount = payerShareAmount;
        this.receivableAmount = receivableAmount;
        this.requestedAt = requestedAt;
        this.memo = memo;
    }
}
