package me.nawa.wallet.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import me.nawa.common.exception.BusinessException;
import me.nawa.wallet.domain.Wallet;
import me.nawa.wallet.dto.request.TopupPreviewRequest;
import me.nawa.wallet.dto.response.TopupMethodsResponse;
import me.nawa.wallet.dto.response.TopupPreviewResponse;
import me.nawa.wallet.exception.WalletErrorCode;
import me.nawa.wallet.mapper.WalletMapper;
import me.nawa.wallet.mapper.WalletTopupMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TopupServiceImplTest {

    @Mock
    private WalletMapper walletMapper;

    @Mock
    private WalletTopupMapper walletTopupMapper;

    @InjectMocks
    private TopupServiceImpl topupService;

    @Test
    void getAvailableTopupMethods_returnsEnabledMethodsAndGuideMessage() {
        TopupMethodsResponse response = topupService.getAvailableTopupMethods();

        assertEquals(1, response.methods().size());
        assertEquals("STRIPE_CARD", response.methods().get(0).type());
        assertTrue(response.methods().get(0).enabled());
        assertNotNull(response.guideMessage());
    }

    @Test
    void previewTopup_returnsPreview_whenWalletActive() {
        Wallet wallet = new Wallet(100L, "KRW", BigDecimal.valueOf(50000), "ACTIVE");
        when(walletMapper.findByMemberId(1L)).thenReturn(wallet);

        TopupPreviewRequest request = new TopupPreviewRequest(BigDecimal.valueOf(10000), "STRIPE_CARD", "KRW");

        TopupPreviewResponse response = topupService.previewTopup(1L, request);

        assertEquals(0, BigDecimal.valueOf(10000).compareTo(response.amount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(response.fee()));
        assertEquals("KRW", response.currency());
        assertEquals(0, BigDecimal.valueOf(50000).compareTo(response.sandboxBalance()));
        assertEquals(0, BigDecimal.valueOf(60000).compareTo(response.expectedSandboxBalance()));
        assertNull(response.warning());
    }

    @Test
    void previewTopup_includesWarning_whenWalletNotActive() {
        Wallet wallet = new Wallet(100L, "KRW", BigDecimal.valueOf(50000), "SUSPENDED");
        when(walletMapper.findByMemberId(1L)).thenReturn(wallet);

        TopupPreviewRequest request = new TopupPreviewRequest(BigDecimal.valueOf(10000), "STRIPE_CARD", "KRW");

        TopupPreviewResponse response = topupService.previewTopup(1L, request);

        assertNotNull(response.warning());
        assertTrue(response.warning().contains("SUSPENDED"));
    }

    @Test
    void previewTopup_throwsBusinessException_whenWalletNotFound() {
        when(walletMapper.findByMemberId(1L)).thenReturn(null);

        TopupPreviewRequest request = new TopupPreviewRequest(BigDecimal.valueOf(10000), "STRIPE_CARD", "KRW");

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> topupService.previewTopup(1L, request)
        );

        assertEquals(WalletErrorCode.WALLET_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void previewTopup_throwsBusinessException_whenMethodUnknown() {
        TopupPreviewRequest request = new TopupPreviewRequest(BigDecimal.valueOf(10000), "UNKNOWN_METHOD", "KRW");

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> topupService.previewTopup(1L, request)
        );

        assertEquals(WalletErrorCode.TOPUP_METHOD_NOT_SUPPORTED, exception.getErrorCode());
        verifyNoInteractions(walletMapper);
    }

    @Test
    void previewTopup_throwsBusinessException_whenCurrencyUnsupported() {
        TopupPreviewRequest request = new TopupPreviewRequest(BigDecimal.valueOf(10000), "STRIPE_CARD", "USD");

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> topupService.previewTopup(1L, request)
        );

        assertEquals(WalletErrorCode.UNSUPPORTED_CURRENCY, exception.getErrorCode());
        verifyNoInteractions(walletMapper);
    }

    @Test
    void previewTopup_throwsBusinessException_whenAmountIsNull() {
        TopupPreviewRequest request = new TopupPreviewRequest(null, "STRIPE_CARD", "KRW");

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> topupService.previewTopup(1L, request)
        );

        assertEquals(WalletErrorCode.INVALID_TOPUP_AMOUNT, exception.getErrorCode());
        verifyNoInteractions(walletMapper);
    }

    @Test
    void previewTopup_throwsBusinessException_whenAmountIsZeroOrNegative() {
        TopupPreviewRequest zeroRequest = new TopupPreviewRequest(BigDecimal.ZERO, "STRIPE_CARD", "KRW");
        TopupPreviewRequest negativeRequest =
            new TopupPreviewRequest(BigDecimal.valueOf(-1000), "STRIPE_CARD", "KRW");

        BusinessException zeroException = assertThrows(
            BusinessException.class,
            () -> topupService.previewTopup(1L, zeroRequest)
        );
        BusinessException negativeException = assertThrows(
            BusinessException.class,
            () -> topupService.previewTopup(1L, negativeRequest)
        );

        assertEquals(WalletErrorCode.INVALID_TOPUP_AMOUNT, zeroException.getErrorCode());
        assertEquals(WalletErrorCode.INVALID_TOPUP_AMOUNT, negativeException.getErrorCode());
        verifyNoInteractions(walletMapper);
    }

    @Test
    void previewTopup_throwsBusinessException_whenMethodIsNull() {
        TopupPreviewRequest request = new TopupPreviewRequest(BigDecimal.valueOf(10000), null, "KRW");

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> topupService.previewTopup(1L, request)
        );

        assertEquals(WalletErrorCode.TOPUP_METHOD_NOT_SUPPORTED, exception.getErrorCode());
        verifyNoInteractions(walletMapper);
    }
}
