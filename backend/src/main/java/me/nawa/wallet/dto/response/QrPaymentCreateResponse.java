package me.nawa.wallet.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record QrPaymentCreateResponse(
    Long qrPaymentCodeId,
    String qrToken,
    BigDecimal amount,
    String memo,
    String status,
    String currencyCode,
    LocalDateTime expiresAt
) {
}
