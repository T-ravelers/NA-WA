package me.nawa.wallet.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class TransactionCounterparty {

    private String ownerType;   // MEMBER | SYSTEM
    private String displayName;
    private String systemCode;
}
