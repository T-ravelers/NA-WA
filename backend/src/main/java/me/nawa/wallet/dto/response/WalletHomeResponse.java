package me.nawa.wallet.dto.response;

import java.math.BigDecimal;
import java.util.List;
import lombok.Getter;

public record WalletHomeResponse(
    BigDecimal balance,
    String availabilityStatus,
    List<TransactionSummaryResponse> recentTransactions
) {
    public static WalletHomeResponse of(
        BigDecimal balance,
        String availabilityStatus,
        List<TransactionSummaryResponse> recentTransactions
    ) {
        return new WalletHomeResponse(balance, availabilityStatus, recentTransactions);
    }
}
