package me.nawa.wallet.dto.request;

import java.math.BigDecimal;
import me.nawa.wallet.domain.enums.SpendingScope;

public record QrPaymentPreviewRequest(
    String qrToken,
    BigDecimal amount,
    SpendingScope spendingScope,
    Long appointmentId
) {
}
