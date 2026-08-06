package me.nawa.wallet.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import me.nawa.wallet.domain.WalletLedgerEntry;

//거래 내역 조회 내용
public record TransactionSummaryResponse(
    Long transferId,        // 거래 ID
    String transferType,    // 거래 종류
    String entryType,       // 이 지갑 기준 증감 방향 (DEBIT | CREDIT)
    BigDecimal amount,      // 거래 금액
    BigDecimal balanceAfter, // 거래 반영 후 잔액
    LocalDateTime createdAt // 거래 시각
) {

    public static TransactionSummaryResponse from(WalletLedgerEntry entry){
        return new TransactionSummaryResponse(
            entry.getTransferId(),
            entry.getTransferType(),
            entry.getEntryType(),
            entry.getAmount(),
            entry.getBalanceAfter(),
            entry.getCreatedAt()
        );
    }
}
