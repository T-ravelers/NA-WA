package me.nawa.wallet.dto.response;

import java.util.List;

public record TransactionListResponse(
    List<TransactionSummaryResponse> transactions, // 이번 페이지 거래 목록
    String nextCursor,                              // 다음 페이지 커서 (더 없으면 null)
    TransactionAppliedFilters appliedFilters        // 실제 적용된 필터 값
) {
    public static TransactionListResponse of(
        List<TransactionSummaryResponse> transactions,
        String nextCursor,
        TransactionAppliedFilters appliedFilters
    ){
        return new TransactionListResponse(transactions, nextCursor, appliedFilters);
    }
}
