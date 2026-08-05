package me.nawa.wallet.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import me.nawa.wallet.domain.WalletTopup;

public record TopupSummaryResponse(
    Long topupId,                        // 충전 ID
    String topupStatus,                  // 충전 상태
    BigDecimal sourceAmount,             // 원래 통화 기준 금액
    String sourceCurrencyCode,           // 원래 통화 코드
    BigDecimal exchangeRateKrwPerUnit,   // 적용 환율
    BigDecimal krwAmount,                // 환전 후 KRW 충전 금액
    LocalDateTime quotedAt,              // 환율 기준 시각
    LocalDateTime completedAt,           // 충전 완료 시각 (완료 전이면 null)
    LocalDateTime createdAt              // 충전 요청 생성 시각
) {

    public static TopupSummaryResponse from(WalletTopup topup) {
        return new TopupSummaryResponse(
            topup.getTopupId(),
            topup.getTopupStatus(),
            topup.getSourceAmount(),
            topup.getSourceCurrencyCode(),
            topup.getExchangeRateKrwPerUnit(),
            topup.getKrwAmount(),
            topup.getQuotedAt(),
            topup.getCompletedAt(),
            topup.getCreatedAt()
        );
    }
}
