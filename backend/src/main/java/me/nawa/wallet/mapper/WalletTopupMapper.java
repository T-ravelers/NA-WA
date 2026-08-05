package me.nawa.wallet.mapper;

import me.nawa.wallet.domain.WalletTopup;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface WalletTopupMapper {

    WalletTopup findFxByTransferId(@Param("transferId") Long transferId);
}
