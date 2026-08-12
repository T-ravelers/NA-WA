package me.nawa.wallet.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record QrPaymentExecuteResponse(
    Long transferId,
    Long qrPaymentId,
    String status,
    BigDecimal amount,
    BigDecimal balanceAfter,
    String currencyCode,
    LocalDateTime completedAt
) {
}
