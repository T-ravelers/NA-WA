package me.nawa.wallet.mapper;

import java.util.List;
import me.nawa.wallet.domain.WalletTopup;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface WalletTopupMapper {

    WalletTopup findFxByTransferId(@Param("transferId") Long transferId);

    List<WalletTopup> findByWalletIdWithCursor(
        @Param("walletId") Long walletId,
        @Param("cursor") Long cursor,
        @Param("limit") int limit
    );
}
