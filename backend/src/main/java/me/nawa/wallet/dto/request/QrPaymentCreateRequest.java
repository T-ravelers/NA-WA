package me.nawa.wallet.dto.request;

import java.math.BigDecimal;

public record QrPaymentCreateRequest(
    BigDecimal amount,
    String memo
) {
}
