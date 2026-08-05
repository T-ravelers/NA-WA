package me.nawa.wallet.dto.response;

public record TransactionReceiptResponse(
    String transactionNumber,
    String memo,
    String spendingCategory
) {
}
