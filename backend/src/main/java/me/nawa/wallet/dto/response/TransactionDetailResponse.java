package me.nawa.wallet.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionDetailResponse(
    BigDecimal amount,                          // 거래 금액
    LocalDateTime occurredAt,                   // 거래 발생 시각 (완료 시각, 없으면 생성 시각)
    TransactionCounterpartyResponse counterparty, // 거래 상대방
    String status,                              // 거래 상태
    TransactionReceiptResponse receipt,         // 영수증 정보
    String transactionNumber,                   // 거래 번호
    TransactionFxResponse fx                    // 환율 정보 (TOPUP 거래만 값 있음, 그 외 null)
) { }
