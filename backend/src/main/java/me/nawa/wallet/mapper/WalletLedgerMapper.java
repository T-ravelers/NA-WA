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

    WalletLedgerEntry findByTransferIdAndWalletId(
        @Param("transferId") Long transferId,
        @Param("walletId") Long walletId
    );

    // findByTransferIdAndWalletId의 잠금 버전. QrPaymentServiceImpl.getIdempotentResult처럼
    // 다른 트랜잭션이 방금 커밋한 원장 행을 읽어야 하는 곳에서만 쓴다 — 일반 SELECT는 이
    // 트랜잭션의 첫 조회가 만든 REPEATABLE READ 스냅샷에 묶여 그 행이 안 보일 수 있다
    // (WalletTransferMapper.findByIdempotencyKeyForUpdate 주석 참고). 같은 트랜잭션 안에서
    // 방금 만든 자기 행을 읽을 때는 이 문제가 없으므로 일반 버전을 그대로 쓰면 된다.
    WalletLedgerEntry findByTransferIdAndWalletIdForUpdate(
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
