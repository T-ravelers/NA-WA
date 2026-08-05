package me.nawa.wallet.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WalletTransfer {

    private Long transferId;
    private String transferNumber;
    private String transferType;
    private String transferStatus;
    private BigDecimal amount;
    private String memo;
    private String spendingCategory;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
}
