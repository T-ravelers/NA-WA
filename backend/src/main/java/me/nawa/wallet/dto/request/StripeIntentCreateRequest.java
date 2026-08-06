package me.nawa.wallet.dto.request;

import java.math.BigDecimal;

public record StripeIntentCreateRequest(
    BigDecimal amount, String currency
) {
}
