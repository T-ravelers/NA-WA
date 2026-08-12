package me.nawa.wallet.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record QrPaymentStatusResponse(
    Long transferId,
    String status,
    BigDecimal amount,
    BigDecimal balanceAfter,
    String currencyCode,
    LocalDateTime completedAt
) {
}
