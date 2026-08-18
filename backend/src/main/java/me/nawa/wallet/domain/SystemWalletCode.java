package me.nawa.wallet.domain;

/**
 * 시스템 지갑 식별 코드
 *
 * wallet_owners.system_code에 저장되는 값이며, 시드 마이그레이션과 지갑 조회
 * 양쪽에서 같은 상수를 참조해야 한다. 두 곳의 값이 어긋나면 지갑을 찾지 못해
 * 관련 이체가 전부 실패한다.
 *
 * 번역 없이 회원의 거래 상세 화면에 상대방 이름으로 그대로 노출된다
 * (TransactionServiceImpl.resolveCounterparty).
 */
public final class SystemWalletCode {

    /** 약속 보증금을 예치·보관하는 시스템 지갑. */
    public static final String DEPOSIT_POOL = "DEPOSIT_POOL";

    private SystemWalletCode() {
    }
}
