package me.nawa.wallet.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record QrPaymentStatusResponse(
    Long transferId,
    String status,
    BigDecimal amount,
    BigDecimal balanceAfter,
    String currencyCode,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    LocalDateTime completedAt
) {
}
