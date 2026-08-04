package me.nawa.wallet.dto.response;

import java.util.List;

public record TransactionListResponse(
    List<TransactionSummaryResponse> transactions,
    String nextCursor,
    TransactionAppliedFilters appliedFilters
) {
    public static TransactionListResponse of(
        List<TransactionSummaryResponse> transactions,
        String nextCursor,
        TransactionAppliedFilters appliedFilters
    ){
        return new TransactionListResponse(transactions, nextCursor, appliedFilters);
    }
}
