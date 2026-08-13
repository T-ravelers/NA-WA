package me.nawa.wallet.mapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import me.nawa.wallet.domain.TransactionCounterparty;
import me.nawa.wallet.domain.WalletLedgerEntry;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface WalletLedgerMapper {

    // 잔액 변동 1건 기록 (DEBIT | CREDIT). 반드시 wallet_transfers insert 이후, 지갑 잔액을 갱신한 값을 balanceAfter로 넘겨서 호출한다.
    void insert(
        @Param("transferId") Long transferId,
        @Param("walletId") Long walletId,
        @Param("entryType") String entryType,
        @Param("amount") BigDecimal amount,
        @Param("balanceAfter") BigDecimal balanceAfter
    );

    List<WalletLedgerEntry> findRecentByWalletId(
        @Param("walletId") Long walletId,
        @Param("limit") int limit
    );

    // QrPaymentServiceImpl.executePayment/getIdempotentResult에서도 쓴다. 그 트랜잭션이
    // READ COMMITTED로 실행되므로, 다른 트랜잭션이 방금 커밋한 원장 행도 일반 SELECT로
    // 그대로 보인다(QrPaymentServiceImpl의 @Transactional(isolation) 주석 참고).
    WalletLedgerEntry findByTransferIdAndWalletId(
        @Param("transferId") Long transferId,
        @Param("walletId") Long walletId
    );

    TransactionCounterparty findCounterpartyByTransferId(
      @Param("transferId") Long transferId,
      @Param("walletId") Long walletId
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
