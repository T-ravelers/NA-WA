package me.nawa.wallet.dto.response;

import java.math.BigDecimal;
import java.util.List;
import lombok.Getter;

public record  WalletHomeResponse(
    BigDecimal balance,                          // 현재 사용 가능 잔액
    String availabilityStatus,                   // 지갑 상태
    List<TransactionSummaryResponse> recentTransactions // 최근 거래 5건
) {
    public static WalletHomeResponse of(
        BigDecimal balance,
        String availabilityStatus,
        List<TransactionSummaryResponse> recentTransactions
    ) {
        return new WalletHomeResponse(balance, availabilityStatus, recentTransactions);
    }
}
