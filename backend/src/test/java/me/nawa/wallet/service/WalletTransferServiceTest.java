package me.nawa.wallet.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import me.nawa.wallet.domain.Wallet;
import me.nawa.wallet.domain.WalletTransfer;
import me.nawa.wallet.mapper.WalletLedgerMapper;
import me.nawa.wallet.mapper.WalletMapper;
import me.nawa.wallet.mapper.WalletTransferMapper;
import me.nawa.wallet.util.TransactionNumberGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WalletTransferServiceTest {

    @Mock
    private WalletMapper walletMapper;

    @Mock
    private WalletTransferMapper walletTransferMapper;

    @Mock
    private WalletLedgerMapper walletLedgerMapper;

    @Mock
    private TransactionNumberGenerator transactionNumberGenerator;

    @Test
    void transfer_debitsPayerAndCreditsPayeeInOneLedgerTransfer() {
        Wallet payer = new Wallet(10L, "KRW", new BigDecimal("100.00"), "ACTIVE");
        Wallet payee = new Wallet(20L, "KRW", new BigDecimal("30.00"), "ACTIVE");
        when(walletMapper.findByMemberId(1L)).thenReturn(payer);
        when(walletMapper.findByMemberId(2L)).thenReturn(payee);
        when(walletMapper.findByWalletIdForUpdate(10L)).thenReturn(payer);
        when(walletMapper.findByWalletIdForUpdate(20L)).thenReturn(payee);
        when(transactionNumberGenerator.generate()).thenReturn("TXN-SETTLEMENT-1");
        doAnswer(invocation -> {
            invocation.getArgument(0, WalletTransfer.class).setTransferId(99L);
            return null;
        }).when(walletTransferMapper).insert(any(WalletTransfer.class));

        WalletTransferService service = new WalletTransferService(
            walletMapper,
            walletTransferMapper,
            walletLedgerMapper,
            transactionNumberGenerator
        );

        long transferId = service.transfer(1L, 1L, 2L, new BigDecimal("30.00"), "Settlement #7");

        assertEquals(99L, transferId);
        InOrder order = inOrder(walletMapper, walletTransferMapper, walletLedgerMapper);
        order.verify(walletMapper).findByWalletIdForUpdate(10L);
        order.verify(walletMapper).findByWalletIdForUpdate(20L);
        order.verify(walletTransferMapper).insert(any(WalletTransfer.class));
        order.verify(walletMapper).updateBalance(10L, new BigDecimal("70.00"));
        order.verify(walletMapper).updateBalance(20L, new BigDecimal("60.00"));
        order.verify(walletLedgerMapper).insert(99L, 10L, "DEBIT", new BigDecimal("30.00"), new BigDecimal("70.00"));
        order.verify(walletLedgerMapper).insert(99L, 20L, "CREDIT", new BigDecimal("30.00"), new BigDecimal("60.00"));
    }
}
