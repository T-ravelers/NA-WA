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

    private BigDecimal sourceAmount;
    private String sourceCurrencyCode;
    private BigDecimal exchangeRateKrwPerUnit;
    private LocalDateTime quotedAt;
}
