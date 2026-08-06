package me.nawa.wallet.dto.response;

public record TransactionReceiptResponse(
    String transactionNumber, // 거래 번호
    String memo,               // 거래 메모
    String spendingCategory    // 지출 카테고리
) {
}
