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

    private Long transferId;
    private String transferType;
    private String entryType;
    private BigDecimal amount;
    private BigDecimal balanceAfter;
    private String memo;
    private LocalDateTime createdAt;
}
