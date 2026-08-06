package me.nawa.wallet.mapper;

import java.time.LocalDateTime;
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

    void insert(WalletTopup topup);

    WalletTopup findByTopupId(@Param("topupId") Long topupId);

    WalletTopup findByTopupIdForUpdate(@Param("topupId") Long topupId);

    WalletTopup findByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);

    WalletTopup findByProviderPaymentId(@Param("providerPaymentId") String providerPaymentId);

    WalletTopup findByProviderPaymentIdForUpdate(@Param("providerPaymentId") String providerPaymentId);

    void updateProviderStatus(@Param("topupId") Long topupId, @Param("providerStatus") String providerStatus);

    void markCompleted(
        @Param("topupId") Long topupId,
        @Param("transferId") Long transferId,
        @Param("providerStatus") String providerStatus,
        @Param("completedAt") LocalDateTime completedAt
    );

    void markFailed(@Param("topupId") Long topupId, @Param("providerStatus") String providerStatus);

    void markCancelled(@Param("topupId") Long topupId, @Param("providerStatus") String providerStatus);
}
