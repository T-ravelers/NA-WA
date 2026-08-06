package me.nawa.wallet.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import me.nawa.common.exception.BusinessException;
import me.nawa.wallet.domain.Wallet;
import me.nawa.wallet.domain.WalletLedgerEntry;
import me.nawa.wallet.dto.response.WalletHomeResponse;
import me.nawa.wallet.exception.WalletErrorCode;
import me.nawa.wallet.mapper.WalletLedgerMapper;
import me.nawa.wallet.mapper.WalletMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WalletServiceImplTest {

    @Mock
    private WalletMapper walletMapper;

    @Mock
    private WalletLedgerMapper walletLedgerMapper;

    @InjectMocks
    private WalletServiceImpl walletService;

    @Test
    void getWalletHome_returnsBalanceStatusAndRecentTransactions_whenWalletExists() {
        Wallet wallet = new Wallet(100L, "KRW", BigDecimal.valueOf(50000), "ACTIVE");
        WalletLedgerEntry entry = new WalletLedgerEntry(
            10L, "TOPUP", "CREDIT", BigDecimal.valueOf(10000), BigDecimal.valueOf(50000), "충전", LocalDateTime.now(), 1L
        );

        when(walletMapper.findByMemberId(1L)).thenReturn(wallet);
        when(walletLedgerMapper.findRecentByWalletId(100L, 5)).thenReturn(List.of(entry));

        WalletHomeResponse response = walletService.getWalletHome(1L);

        assertEquals(0, BigDecimal.valueOf(50000).compareTo(response.balance()));
        assertEquals("ACTIVE", response.availabilityStatus());
        assertEquals(1, response.recentTransactions().size());
        assertEquals(10L, response.recentTransactions().get(0).transferId());
    }

    @Test
    void getWalletHome_throwsBusinessException_whenWalletNotFound() {
        when(walletMapper.findByMemberId(anyLong())).thenReturn(null);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> walletService.getWalletHome(1L)
        );

        assertEquals(WalletErrorCode.WALLET_NOT_FOUND, exception.getErrorCode());
        verifyNoInteractions(walletLedgerMapper);
    }

    @Test
    void getWalletHome_returnsEmptyList_whenNoRecentTransactions() {
        Wallet wallet = new Wallet(100L, "KRW", BigDecimal.ZERO, "ACTIVE");
        when(walletMapper.findByMemberId(1L)).thenReturn(wallet);
        when(walletLedgerMapper.findRecentByWalletId(100L, 5)).thenReturn(List.of());

        WalletHomeResponse response = walletService.getWalletHome(1L);

        assertTrue(response.recentTransactions().isEmpty());
    }

    @Test
    void getWalletHome_requestsLimitOfFiveRecentEntries() {
        Wallet wallet = new Wallet(100L, "KRW", BigDecimal.ZERO, "ACTIVE");
        when(walletMapper.findByMemberId(1L)).thenReturn(wallet);
        when(walletLedgerMapper.findRecentByWalletId(eq(100L), anyInt())).thenReturn(List.of());

        walletService.getWalletHome(1L);

        verify(walletLedgerMapper).findRecentByWalletId(100L, 5);
    }
}
