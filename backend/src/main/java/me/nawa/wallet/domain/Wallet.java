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

    private Long walletId;
    private String currencyCode;
    private BigDecimal availableBalance;
    private String walletStatus;
}
