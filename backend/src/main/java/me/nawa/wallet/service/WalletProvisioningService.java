package me.nawa.wallet.service;

// 신규 회원에게 기본 지갑(KRW)을 만들어 준다.
// 호출자(소셜 로그인 가입 트랜잭션)의 트랜잭션에 합류하므로, 회원 생성이 롤백되면 지갑도 함께 롤백된다.
public interface WalletProvisioningService {

    long provisionForMember(long memberId);
}
