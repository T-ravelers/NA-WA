package me.nawa.wallet.mapper;

import me.nawa.wallet.domain.WalletTransfer;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface WalletTransferMapper {

    WalletTransfer findByTransferId(@Param("transferId") Long transferId);
}
