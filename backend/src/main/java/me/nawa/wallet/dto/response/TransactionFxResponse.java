package me.nawa.wallet.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionFxResponse(
    BigDecimal sourceAmount,   // 원래 통화 기준 금액
    String sourceCurrency,     // 원래 통화 코드
    String displayCurrency,    // 화면에 표시할 통화 (현재 KRW 고정)
    BigDecimal exchangeRate,   // 적용된 환율
    LocalDateTime ratedAt      // 환율 기준 시각
) {
}
