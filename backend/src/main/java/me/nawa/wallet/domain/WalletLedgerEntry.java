package me.nawa.wallet.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class WalletLedgerEntry {

    private Long transferId;         // 이 원장 행이 속한 거래(wallet_transfers) ID
    private String transferType;     // 거래 종류 (TOPUP | QR_PAYMENT | ...)
    private String entryType;        // 이 지갑 기준 증감 방향 (DEBIT | CREDIT)
    private BigDecimal amount;       // 거래 금액
    private BigDecimal balanceAfter; // 이 거래가 반영된 후의 지갑 잔액
    private String memo;             // 거래 메모
    private LocalDateTime createdAt; // 원장 생성 시각
    private Long ledgerEntryId;      // 원장 PK — 커서 페이지네이션에 사용
}
