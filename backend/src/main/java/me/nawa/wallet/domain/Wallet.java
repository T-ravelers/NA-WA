package me.nawa.wallet.domain;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Wallet {

    private Long walletId;              // 지갑 PK
    private String currencyCode;        // 지갑 통화 (현재 KRW 고정)
    private BigDecimal availableBalance; // 사용 가능 잔액
    private String walletStatus;        // 지갑 상태 (ACTIVE | SUSPENDED | CLOSED)
}
