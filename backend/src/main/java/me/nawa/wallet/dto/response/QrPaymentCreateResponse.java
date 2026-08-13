package me.nawa.wallet.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record QrPaymentCreateResponse(
    Long qrPaymentCodeId,
    String qrToken,
    BigDecimal amount,
    String memo,
    String status,
    String currencyCode,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    LocalDateTime expiresAt
) {
}
