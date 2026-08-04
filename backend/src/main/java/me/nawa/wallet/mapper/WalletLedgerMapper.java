package me.nawa.wallet.mapper;

import java.time.LocalDateTime;
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

    List<WalletLedgerEntry> findByWalletIdWithCursor(
        @Param("walletId") Long walletId,
        @Param("type") String type,
        @Param("status") String status,
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to,
        @Param("cursor") Long cursor,
        @Param("limit") int limit
        );
}
