package me.nawa.wallet.dto.request;

import java.math.BigDecimal;

public record TopupPreviewRequest(
    BigDecimal amount,
    String method,
    String currency
) {
}
