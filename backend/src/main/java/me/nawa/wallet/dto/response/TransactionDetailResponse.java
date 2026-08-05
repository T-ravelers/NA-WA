package me.nawa.wallet.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionDetailResponse(
    BigDecimal amount,
    LocalDateTime occurredAt,
    TransactionCounterpartyResponse counterparty,
    String status,
    TransactionReceiptResponse receipt,
    String transactionNumber,
    TransactionFxResponse fx
) { }
