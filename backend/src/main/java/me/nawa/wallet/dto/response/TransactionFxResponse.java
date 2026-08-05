package me.nawa.wallet.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionFxResponse(
    BigDecimal sourceAmount, String sourceCurrency, String displayCurrency,
    BigDecimal exchangeRate, LocalDateTime ratedAt
) {
}
