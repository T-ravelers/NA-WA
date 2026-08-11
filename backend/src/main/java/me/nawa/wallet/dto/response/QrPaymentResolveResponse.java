package me.nawa.wallet.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record QrPaymentResolveResponse(
    Long qrPaymentId,
    String payeeName,
    BigDecimal amount,
    boolean amountInputRequired,
    String memo,
    String status,
    String currencyCode,
    LocalDateTime expiresAt
) {
}
