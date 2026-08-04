package me.nawa.wallet.mapper;

import java.util.List;
import me.nawa.wallet.domain.WalletLedgerEntry;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface WalletLedgerMapper {

    List<WalletLedgerEntry> findRecentByWalletId(
        @Param("walletId") Long walletId,
        @Param("limit") int limit
    );
}
