package me.nawa.wallet.domain;

import lombok.Getter;
import lombok.Setter;

// 회원 가입 시 wallet_owners → wallets 두 행을 순서대로 만들기 위한 값 홀더.
// 각 INSERT의 생성 키(useGeneratedKeys)를 받아 다음 INSERT에 넘긴다.
@Getter
@Setter
public class MemberWalletProvision {

    private final long memberId;
    private final String currencyCode;
    private long walletOwnerId;
    private long walletId;

    public MemberWalletProvision(long memberId, String currencyCode) {
        this.memberId = memberId;
        this.currencyCode = currencyCode;
    }
}
