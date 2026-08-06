package me.nawa.wallet.dto.response;

import java.math.BigDecimal;

public record TopupPreviewResponse(
    BigDecimal amount,
    BigDecimal fee,
    String currency,
    BigDecimal sandboxBalance,
    BigDecimal expectedSandboxBalance,
    String warning
) {
}
