package me.nawa.wallet.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import me.nawa.common.exception.BusinessException;
import me.nawa.wallet.domain.TransactionCounterparty;
import me.nawa.wallet.domain.Wallet;
import me.nawa.wallet.domain.WalletLedgerEntry;
import me.nawa.wallet.domain.WalletTopup;
import me.nawa.wallet.domain.WalletTransfer;
import me.nawa.wallet.domain.enums.TransferStatus;
import me.nawa.wallet.domain.enums.TransferType;
import me.nawa.wallet.dto.request.TransactionSearchCondition;
import me.nawa.wallet.dto.response.TransactionDetailResponse;
import me.nawa.wallet.dto.response.TransactionListResponse;
import me.nawa.wallet.exception.WalletErrorCode;
import me.nawa.wallet.mapper.WalletLedgerMapper;
import me.nawa.wallet.mapper.WalletMapper;
import me.nawa.wallet.mapper.WalletTopupMapper;
import me.nawa.wallet.mapper.WalletTransferMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransactionServiceImplTest {

    @Mock
    private WalletMapper walletMapper;

    @Mock
    private WalletLedgerMapper walletLedgerMapper;

    @Mock
    private WalletTransferMapper walletTransferMapper;

    @Mock
    private WalletTopupMapper walletTopupMapper;

    @InjectMocks
    private TransactionServiceImpl transactionService;

    private Wallet wallet() {
        return new Wallet(100L, "KRW", BigDecimal.valueOf(50000), "ACTIVE");
    }

    private WalletLedgerEntry entry(long transferId, long ledgerEntryId) {
        return new WalletLedgerEntry(
            transferId, "TOPUP", "CREDIT",
            BigDecimal.valueOf(10000), BigDecimal.valueOf(50000),
            "충전", LocalDateTime.now(), ledgerEntryId
        );
    }

    private WalletTransfer transfer(
        Long transferId, String transferType, String transferStatus,
        LocalDateTime completedAt, LocalDateTime createdAt
    ) {
        return new WalletTransfer(
            transferId, "TXN-20260805-0001", transferType, transferStatus,
            BigDecimal.valueOf(10000), "카드 충전", "FOOD",
            completedAt, createdAt, null, null
        );
    }

    // ===== getTransactions (목록 조회) =====

    @Test
    void getTransactions_returnsPageWithoutNextCursor_whenEntriesWithinSize() {
        Wallet wallet = wallet();
        TransactionSearchCondition condition = new TransactionSearchCondition();
        condition.setSize(20);

        when(walletMapper.findByMemberId(1L)).thenReturn(wallet);
        when(walletLedgerMapper.findByWalletIdWithCursor(
            eq(100L), isNull(), isNull(), isNull(), isNull(), isNull(), eq(21)
        )).thenReturn(List.of(entry(10L, 10L), entry(9L, 9L)));

        TransactionListResponse response = transactionService.getTransactions(1L, condition);

        assertEquals(2, response.transactions().size());
        assertNull(response.nextCursor());
    }

    @Test
    void getTransactions_returnsNextCursor_whenMoreEntriesExistThanSize() {
        Wallet wallet = wallet();
        TransactionSearchCondition condition = new TransactionSearchCondition();
        condition.setSize(2);

        when(walletMapper.findByMemberId(1L)).thenReturn(wallet);
        when(walletLedgerMapper.findByWalletIdWithCursor(
            eq(100L), isNull(), isNull(), isNull(), isNull(), isNull(), eq(3)
        )).thenReturn(List.of(entry(12L, 12L), entry(11L, 11L), entry(10L, 10L)));

        TransactionListResponse response = transactionService.getTransactions(1L, condition);

        assertEquals(2, response.transactions().size());
        assertEquals("11", response.nextCursor());
    }

    @Test
    void getTransactions_throwsBusinessException_whenWalletNotFound() {
        when(walletMapper.findByMemberId(anyLong())).thenReturn(null);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> transactionService.getTransactions(1L, new TransactionSearchCondition())
        );

        assertEquals(WalletErrorCode.WALLET_NOT_FOUND, exception.getErrorCode());
        verifyNoInteractions(walletLedgerMapper);
    }

    @Test
    void getTransactions_passesTypeStatusAndDateRangeToMapper() {
        Wallet wallet = wallet();
        TransactionSearchCondition condition = new TransactionSearchCondition();
        condition.setType(TransferType.QR_PAYMENT);
        condition.setStatus(TransferStatus.COMPLETED);
        condition.setFrom(LocalDate.of(2026, 8, 1));
        condition.setTo(LocalDate.of(2026, 8, 4));
        condition.setCursor(50L);
        condition.setSize(10);

        when(walletMapper.findByMemberId(1L)).thenReturn(wallet);
        when(walletLedgerMapper.findByWalletIdWithCursor(
            any(), any(), any(), any(), any(), any(), anyInt()
        )).thenReturn(List.of());

        transactionService.getTransactions(1L, condition);

        verify(walletLedgerMapper).findByWalletIdWithCursor(
            100L,
            "QR_PAYMENT",
            "COMPLETED",
            LocalDateTime.of(2026, 8, 1, 0, 0),
            LocalDateTime.of(2026, 8, 5, 0, 0),
            50L,
            11
        );
    }

    @Test
    void getTransactions_appliesDefaultAndMaxSize() {
        Wallet wallet = wallet();

        when(walletMapper.findByMemberId(1L)).thenReturn(wallet);
        when(walletLedgerMapper.findByWalletIdWithCursor(
            any(), any(), any(), any(), any(), any(), anyInt()
        )).thenReturn(List.of());

        TransactionSearchCondition noSizeCondition = new TransactionSearchCondition();
        transactionService.getTransactions(1L, noSizeCondition);
        verify(walletLedgerMapper).findByWalletIdWithCursor(
            eq(100L), isNull(), isNull(), isNull(), isNull(), isNull(), eq(21)
        );

        TransactionSearchCondition oversizedCondition = new TransactionSearchCondition();
        oversizedCondition.setSize(500);
        transactionService.getTransactions(1L, oversizedCondition);
        verify(walletLedgerMapper).findByWalletIdWithCursor(
            eq(100L), isNull(), isNull(), isNull(), isNull(), isNull(), eq(51)
        );
    }

    // ===== getTransactionDetail (상세 조회) =====

    @Test
    void getTransactionDetail_throwsBusinessException_whenWalletNotFound() {
        when(walletMapper.findByMemberId(anyLong())).thenReturn(null);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> transactionService.getTransactionDetail(1L, 482L)
        );

        assertEquals(WalletErrorCode.WALLET_NOT_FOUND, exception.getErrorCode());
        verifyNoInteractions(walletTransferMapper);
        verifyNoInteractions(walletLedgerMapper);
    }

    @Test
    void getTransactionDetail_throwsNotFound_whenTransferDoesNotExist() {
        when(walletMapper.findByMemberId(1L)).thenReturn(wallet());
        when(walletTransferMapper.findByTransferId(482L)).thenReturn(null);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> transactionService.getTransactionDetail(1L, 482L)
        );

        assertEquals(WalletErrorCode.TRANSACTION_NOT_FOUND, exception.getErrorCode());
        verifyNoInteractions(walletLedgerMapper);
    }

    @Test
    void getTransactionDetail_throwsForbidden_whenTransferNotOwnedByWallet() {
        WalletTransfer transfer = transfer(
            482L, "QR_PAYMENT", "COMPLETED",
            LocalDateTime.of(2026, 8, 4, 10, 0), LocalDateTime.of(2026, 8, 4, 9, 55)
        );

        when(walletMapper.findByMemberId(1L)).thenReturn(wallet());
        when(walletTransferMapper.findByTransferId(482L)).thenReturn(transfer);
        when(walletLedgerMapper.findByTransferIdAndWalletId(482L, 100L)).thenReturn(null);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> transactionService.getTransactionDetail(1L, 482L)
        );

        assertEquals(WalletErrorCode.TRANSACTION_FORBIDDEN, exception.getErrorCode());
        verifyNoInteractions(walletTopupMapper);
    }

    @Test
    void getTransactionDetail_returnsExternalCounterpartyAndFx_whenTopupWithNoCounterpartyRow() {
        LocalDateTime completedAt = LocalDateTime.of(2026, 8, 4, 10, 0);
        WalletTransfer transfer = transfer(482L, "TOPUP", "COMPLETED", completedAt, completedAt.minusMinutes(5));
        WalletTopup topup = new WalletTopup(
            BigDecimal.valueOf(10000), "USD", BigDecimal.valueOf(1350.5), LocalDateTime.of(2026, 8, 4, 9, 59),
            900L, "COMPLETED", BigDecimal.valueOf(13505000), completedAt, completedAt.minusMinutes(5),
            100L, "stripe", "pi_test_900", "succeeded", "idem-test-900", 482L
        );

        when(walletMapper.findByMemberId(1L)).thenReturn(wallet());
        when(walletTransferMapper.findByTransferId(482L)).thenReturn(transfer);
        when(walletLedgerMapper.findByTransferIdAndWalletId(482L, 100L)).thenReturn(entry(482L, 482L));
        when(walletLedgerMapper.findCounterpartyByTransferId(482L, 100L)).thenReturn(null);
        when(walletTopupMapper.findFxByTransferId(482L)).thenReturn(topup);

        TransactionDetailResponse response = transactionService.getTransactionDetail(1L, 482L);

        assertEquals("EXTERNAL", response.counterparty().type());
        assertEquals("Stripe", response.counterparty().name());
        assertEquals(topup.getSourceAmount(), response.fx().sourceAmount());
        assertEquals("USD", response.fx().sourceCurrency());
        assertEquals("KRW", response.fx().displayCurrency());
        assertEquals(topup.getExchangeRateKrwPerUnit(), response.fx().exchangeRate());
        assertEquals(topup.getQuotedAt(), response.fx().ratedAt());
        assertEquals(completedAt, response.occurredAt());
        assertEquals("TXN-20260805-0001", response.transactionNumber());
    }

    @Test
    void getTransactionDetail_returnsNullFx_whenTransferTypeIsNotTopup() {
        WalletTransfer transfer = transfer(
            483L, "QR_PAYMENT", "COMPLETED",
            LocalDateTime.of(2026, 8, 4, 11, 0), LocalDateTime.of(2026, 8, 4, 10, 55)
        );
        TransactionCounterparty counterparty = new TransactionCounterparty("MEMBER", "홍길동", null);

        when(walletMapper.findByMemberId(1L)).thenReturn(wallet());
        when(walletTransferMapper.findByTransferId(483L)).thenReturn(transfer);
        when(walletLedgerMapper.findByTransferIdAndWalletId(483L, 100L)).thenReturn(entry(483L, 483L));
        when(walletLedgerMapper.findCounterpartyByTransferId(483L, 100L)).thenReturn(counterparty);

        TransactionDetailResponse response = transactionService.getTransactionDetail(1L, 483L);

        assertNull(response.fx());
        assertEquals("MEMBER", response.counterparty().type());
        assertEquals("홍길동", response.counterparty().name());
        verifyNoInteractions(walletTopupMapper);
    }

    @Test
    void getTransactionDetail_returnsSystemCounterparty_whenOwnerTypeIsSystem() {
        WalletTransfer transfer = transfer(
            484L, "DEPOSIT_REFUND", "COMPLETED",
            LocalDateTime.of(2026, 8, 4, 12, 0), LocalDateTime.of(2026, 8, 4, 11, 55)
        );
        TransactionCounterparty counterparty = new TransactionCounterparty("SYSTEM", null, "DEPOSIT_POOL");

        when(walletMapper.findByMemberId(1L)).thenReturn(wallet());
        when(walletTransferMapper.findByTransferId(484L)).thenReturn(transfer);
        when(walletLedgerMapper.findByTransferIdAndWalletId(484L, 100L)).thenReturn(entry(484L, 484L));
        when(walletLedgerMapper.findCounterpartyByTransferId(484L, 100L)).thenReturn(counterparty);

        TransactionDetailResponse response = transactionService.getTransactionDetail(1L, 484L);

        assertEquals("SYSTEM", response.counterparty().type());
        assertEquals("DEPOSIT_POOL", response.counterparty().name());
    }

    @Test
    void getTransactionDetail_fallsBackToCreatedAt_whenNotYetCompleted() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 8, 4, 13, 0);
        WalletTransfer transfer = transfer(485L, "TOPUP", "PENDING", null, createdAt);

        when(walletMapper.findByMemberId(1L)).thenReturn(wallet());
        when(walletTransferMapper.findByTransferId(485L)).thenReturn(transfer);
        when(walletLedgerMapper.findByTransferIdAndWalletId(485L, 100L)).thenReturn(entry(485L, 485L));
        when(walletLedgerMapper.findCounterpartyByTransferId(485L, 100L)).thenReturn(null);
        when(walletTopupMapper.findFxByTransferId(485L)).thenReturn(null);

        TransactionDetailResponse response = transactionService.getTransactionDetail(1L, 485L);

        assertEquals(createdAt, response.occurredAt());
        assertNull(response.fx());
    }
}
