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

    private String ownerType;    // 상대방 지갑 소유자 타입 (MEMBER | SYSTEM)
    private String displayName;  // ownerType=MEMBER일 때 상대 회원 이름
    private String systemCode;   // ownerType=SYSTEM일 때 시스템 지갑 코드
}
