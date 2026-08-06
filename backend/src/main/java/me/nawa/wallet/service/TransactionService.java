package me.nawa.wallet.service;

import me.nawa.wallet.dto.request.TransactionSearchCondition;
import me.nawa.wallet.dto.response.TransactionDetailResponse;
import me.nawa.wallet.dto.response.TransactionListResponse;

public interface TransactionService {

    TransactionListResponse getTransactions(Long memberId, TransactionSearchCondition condition);

    TransactionDetailResponse getTransactionDetail(Long memberId, Long transferId);
}
