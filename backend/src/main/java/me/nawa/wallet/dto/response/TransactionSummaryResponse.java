package me.nawa.wallet.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import me.nawa.wallet.domain.WalletLedgerEntry;

//거래 내역 조회 내용
public record TransactionSummaryResponse(
    Long transferId,
    String transferType,
    String entryType,
    BigDecimal amount,
    BigDecimal balanceAfter,
    LocalDateTime createdAt
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
