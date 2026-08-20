package me.nawa.wallet.mapper;

import java.math.BigDecimal;
import me.nawa.wallet.domain.MemberWalletProvision;
import me.nawa.wallet.domain.Wallet;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface WalletMapper {

    Wallet findByMemberId(@Param("memberId")Long memberId);

    // DEPOSIT_POOL 같은 시스템 지갑을 owner_type='SYSTEM' + system_code로 찾는다.
    Wallet findBySystemCode(@Param("systemCode") String systemCode);

    // 가입 시 지갑 생성 — wallet_owners 먼저, 생성된 wallet_owner_id로 wallets를 만든다
    int insertMemberWalletOwner(MemberWalletProvision provision);

    int insertMemberWallet(MemberWalletProvision provision);

    // uq_wallet_owners_member·uq_wallets_owner에는 deleted_at이 없다. soft-delete된 행이 남아 있으면
    // 같은 회원·소유자로 새 행을 INSERT할 수 없으므로, 아래 두 조회는 deleted_at을 조건에 넣지 않는다.
    Long findWalletOwnerIdIncludingDeleted(@Param("memberId") long memberId);

    Long findWalletIdIncludingDeleted(@Param("walletOwnerId") long walletOwnerId);

    // soft-delete된 지갑 정체성을 되살린다. 잔액·wallet_status·원장 이력은 건드리지 않는다.
    int restoreWalletOwner(@Param("walletOwnerId") long walletOwnerId);

    int restoreWallet(@Param("walletId") long walletId);

    // 잔액을 바꾸기 전에 이 지갑 행을 잠근다 (SELECT ... FOR UPDATE) — 동시 요청으로 잔액이 꼬이는 것 방지
    Wallet findByWalletIdForUpdate(@Param("walletId") Long walletId);

    void updateBalance(@Param("walletId") Long walletId, @Param("availableBalance") BigDecimal availableBalance);
}
