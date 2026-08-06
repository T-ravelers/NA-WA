package me.nawa.wallet.dto.response;

import java.math.BigDecimal;

public record StripeTopupStatusResponse(
    Long topupId,
    Long transactionId, // nullable - 완료 전이면 null
    String status,  // PENDING | READY | SUCCESS | FAILED | CANCELLED
    String providerStatus,
    boolean retryable,
    BigDecimal sandboxBalance
) {
}
