package me.nawa.wallet.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class WalletTopup {

    private BigDecimal sourceAmount;            // 충전 시 입력한 원래 통화 기준 금액
    private String sourceCurrencyCode;          // 원래 통화 코드 (예: USD)
    private BigDecimal exchangeRateKrwPerUnit;  // 원래 통화 1단위당 KRW 환율
    private LocalDateTime quotedAt;             // 환율 스냅샷을 뜬 시각
}
