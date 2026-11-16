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
    private Integer currencyDecimalPlaces;
    private String memo;
    private Long version;
    /**
     * 정산을 만든 시각. 생성할 때만 값을 넣고, 조회 쿼리는 이 필드를 채우지 않는다.
     *
     * 앱이 넘긴 값이어야 한다. 미납 리마인더가 이 시각을 앱의 날짜와 견주므로, DB 기본값에
     * 맡기면 DB와 앱의 시간대가 어긋난 만큼 첫 리마인더가 통째로 밀린다.
     */
    private LocalDateTime createdAt;

    @Builder
    public Settlement(Long appointmentId, Long createdByMemberId, Long payerMemberId,
            Long sourceTransferId, String idempotencyKey, String requestFingerprint,
            String settlementStatus, String splitMethod,
            BigDecimal totalAmount, BigDecimal payerShareAmount, BigDecimal receivableAmount,
            String memo, LocalDateTime createdAt) {
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
        this.memo = memo;
        this.createdAt = createdAt;
    }
}
