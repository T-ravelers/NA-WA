package me.nawa.wallet.mapper;

import java.math.BigDecimal;
import me.nawa.wallet.domain.Wallet;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface WalletMapper {

    Wallet findByMemberId(@Param("memberId")Long memberId);

    // 잔액을 바꾸기 전에 이 지갑 행을 잠근다 (SELECT ... FOR UPDATE) — 동시 요청으로 잔액이 꼬이는 것 방지
    Wallet findByWalletIdForUpdate(@Param("walletId") Long walletId);

    void updateBalance(@Param("walletId") Long walletId, @Param("availableBalance") BigDecimal availableBalance);
}
