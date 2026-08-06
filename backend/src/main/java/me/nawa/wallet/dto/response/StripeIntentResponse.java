package me.nawa.wallet.dto.response;

import java.math.BigDecimal;

public record StripeIntentResponse(

    Long topupId,
    String clientSecret,
    String providerPaymentId,
    BigDecimal amount,
    String currency,
    String status,  // 생성 직후 항상 "READY"
    String paymentMode // 항상 "SANDBOX"
) {
}
