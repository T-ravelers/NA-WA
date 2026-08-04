package me.nawa.wallet.mapper;

import me.nawa.wallet.domain.Wallet;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface WalletMapper {

    Wallet findByMemberId(@Param("memberId")Long memberId);
}
