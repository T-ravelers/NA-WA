package me.nawa.wallet.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.PaymentIntent;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import me.nawa.common.exception.BusinessException;
import me.nawa.wallet.domain.Wallet;
import me.nawa.wallet.domain.WalletTopup;
import me.nawa.wallet.domain.WalletTransfer;
import me.nawa.wallet.dto.request.StripeIntentCreateRequest;
import me.nawa.wallet.dto.request.TopupPreviewRequest;
import me.nawa.wallet.dto.response.StripeIntentResponse;
import me.nawa.wallet.dto.response.StripeTopupStatusResponse;
import me.nawa.wallet.dto.response.StripeWebhookResponse;
import me.nawa.wallet.dto.response.TopupListResponse;
import me.nawa.wallet.dto.response.TopupMethodsResponse;
import me.nawa.wallet.dto.response.TopupPreviewResponse;
import me.nawa.wallet.exception.WalletErrorCode;
import me.nawa.wallet.external.stripe.StripeClient;
import me.nawa.wallet.external.stripe.StripePaymentIntent;
import me.nawa.wallet.mapper.WalletLedgerMapper;
import me.nawa.wallet.mapper.WalletMapper;
import me.nawa.wallet.mapper.WalletTopupMapper;
import me.nawa.wallet.mapper.WalletTransferMapper;
import me.nawa.wallet.util.TransactionNumberGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TopupServiceImplTest {

    @Mock
    private WalletMapper walletMapper;

    @Mock
    private WalletTopupMapper walletTopupMapper;

    @Mock
    private WalletTransferMapper walletTransferMapper;

    @Mock
    private WalletLedgerMapper walletLedgerMapper;

    @Mock
    private TransactionNumberGenerator transactionNumberGenerator;

    @Mock
    private StripeClient stripeClient;

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

    private WalletTopup topup(long topupId) {
        return new WalletTopup(
            BigDecimal.valueOf(10000), "USD", BigDecimal.valueOf(1350.5), LocalDateTime.now(),
            topupId, "COMPLETED", BigDecimal.valueOf(13505000), LocalDateTime.now(), LocalDateTime.now(),
            100L, "stripe", "pi_test_" + topupId, "succeeded", "idem-test-" + topupId, null
        );
    }

    @Test
    void getTopups_returnsPageWithoutNextCursor_whenTopupsWithinSize() {
        Wallet wallet = new Wallet(100L, "KRW", BigDecimal.valueOf(50000), "ACTIVE");
        when(walletMapper.findByMemberId(1L)).thenReturn(wallet);
        when(walletTopupMapper.findByWalletIdWithCursor(eq(100L), isNull(), eq(21)))
            .thenReturn(List.of(topup(10L), topup(9L)));

        TopupListResponse response = topupService.getTopups(1L, null, null);

        assertEquals(2, response.topups().size());
        assertNull(response.nextCursor());
    }

    @Test
    void getTopups_returnsNextCursor_whenMoreTopupsExistThanSize() {
        Wallet wallet = new Wallet(100L, "KRW", BigDecimal.valueOf(50000), "ACTIVE");
        when(walletMapper.findByMemberId(1L)).thenReturn(wallet);
        when(walletTopupMapper.findByWalletIdWithCursor(eq(100L), isNull(), eq(3)))
            .thenReturn(List.of(topup(12L), topup(11L), topup(10L)));

        TopupListResponse response = topupService.getTopups(1L, null, 2);

        assertEquals(2, response.topups().size());
        assertEquals("11", response.nextCursor());
    }

    @Test
    void getTopups_throwsBusinessException_whenWalletNotFound() {
        when(walletMapper.findByMemberId(1L)).thenReturn(null);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> topupService.getTopups(1L, null, null)
        );

        assertEquals(WalletErrorCode.WALLET_NOT_FOUND, exception.getErrorCode());
        verifyNoInteractions(walletTopupMapper);
    }

    @Test
    void getTopups_appliesDefaultAndMaxSize() {
        Wallet wallet = new Wallet(100L, "KRW", BigDecimal.valueOf(50000), "ACTIVE");
        when(walletMapper.findByMemberId(1L)).thenReturn(wallet);
        when(walletTopupMapper.findByWalletIdWithCursor(any(), any(), anyInt())).thenReturn(List.of());

        topupService.getTopups(1L, null, null);
        verify(walletTopupMapper).findByWalletIdWithCursor(eq(100L), isNull(), eq(21));

        topupService.getTopups(1L, null, 500);
        verify(walletTopupMapper).findByWalletIdWithCursor(eq(100L), isNull(), eq(51));
    }

    // ===== createStripeIntent (Stripe PaymentIntent 생성) =====

    private StripeIntentCreateRequest intentRequest() {
        return new StripeIntentCreateRequest(BigDecimal.valueOf(10000), "KRW");
    }

    @Test
    void createStripeIntent_throwsBusinessException_whenIdempotencyKeyMissing() {
        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> topupService.createStripeIntent(1L, null, intentRequest())
        );

        assertEquals(WalletErrorCode.IDEMPOTENCY_KEY_REQUIRED, exception.getErrorCode());
        verifyNoInteractions(walletMapper);
        verifyNoInteractions(stripeClient);
    }

    @Test
    void createStripeIntent_throwsBusinessException_whenIdempotencyKeyBlank() {
        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> topupService.createStripeIntent(1L, "   ", intentRequest())
        );

        assertEquals(WalletErrorCode.IDEMPOTENCY_KEY_REQUIRED, exception.getErrorCode());
    }

    @Test
    void createStripeIntent_throwsBusinessException_whenAmountInvalid() {
        StripeIntentCreateRequest request = new StripeIntentCreateRequest(BigDecimal.ZERO, "KRW");

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> topupService.createStripeIntent(1L, "key-1", request)
        );

        assertEquals(WalletErrorCode.INVALID_TOPUP_AMOUNT, exception.getErrorCode());
        verifyNoInteractions(walletMapper);
    }

    @Test
    void createStripeIntent_throwsBusinessException_whenCurrencyUnsupported() {
        StripeIntentCreateRequest request = new StripeIntentCreateRequest(BigDecimal.valueOf(10000), "USD");

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> topupService.createStripeIntent(1L, "key-1", request)
        );

        assertEquals(WalletErrorCode.UNSUPPORTED_CURRENCY, exception.getErrorCode());
        verifyNoInteractions(walletMapper);
    }

    @Test
    void createStripeIntent_throwsBusinessException_whenWalletNotFound() {
        when(walletMapper.findByMemberId(1L)).thenReturn(null);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> topupService.createStripeIntent(1L, "key-1", intentRequest())
        );

        assertEquals(WalletErrorCode.WALLET_NOT_FOUND, exception.getErrorCode());
        verifyNoInteractions(stripeClient);
    }

    @Test
    void createStripeIntent_throwsBusinessException_whenWalletNotActive() {
        Wallet wallet = new Wallet(100L, "KRW", BigDecimal.valueOf(50000), "SUSPENDED");
        when(walletMapper.findByMemberId(1L)).thenReturn(wallet);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> topupService.createStripeIntent(1L, "key-1", intentRequest())
        );

        assertEquals(WalletErrorCode.STRIPE_WALLET_NOT_ACTIVE, exception.getErrorCode());
        verifyNoInteractions(stripeClient);
    }

    @Test
    void createStripeIntent_createsNewIntentAndSavesTopup_whenIdempotencyKeyIsNew() throws StripeException {
        Wallet wallet = new Wallet(100L, "KRW", BigDecimal.valueOf(50000), "ACTIVE");
        when(walletMapper.findByMemberId(1L)).thenReturn(wallet);
        when(walletTopupMapper.findByIdempotencyKey("key-1")).thenReturn(null);

        StripePaymentIntent intent = new StripePaymentIntent("pi_123", "pi_123_secret_abc", "requires_payment_method");
        when(stripeClient.createPaymentIntent(eq(BigDecimal.valueOf(10000)), eq("key-1"))).thenReturn(intent);

        StripeIntentResponse response = topupService.createStripeIntent(1L, "key-1", intentRequest());

        assertEquals("pi_123_secret_abc", response.clientSecret());
        assertEquals("pi_123", response.providerPaymentId());
        assertEquals(0, BigDecimal.valueOf(10000).compareTo(response.amount()));
        assertEquals("KRW", response.currency());
        assertEquals("READY", response.status());
        assertEquals("SANDBOX", response.paymentMode());

        ArgumentCaptor<WalletTopup> captor = ArgumentCaptor.forClass(WalletTopup.class);
        verify(walletTopupMapper).insert(captor.capture());
        WalletTopup saved = captor.getValue();
        assertEquals(100L, saved.getWalletId());
        assertEquals("stripe", saved.getProvider());
        assertEquals("pi_123", saved.getProviderPaymentId());
        assertEquals("requires_payment_method", saved.getProviderStatus());
        assertEquals("key-1", saved.getIdempotencyKey());
        assertEquals("QUOTED", saved.getTopupStatus());
        assertNull(saved.getTransferId());
    }

    @Test
    void createStripeIntent_throwsUnavailable_whenStripeCreateFails() throws StripeException {
        Wallet wallet = new Wallet(100L, "KRW", BigDecimal.valueOf(50000), "ACTIVE");
        when(walletMapper.findByMemberId(1L)).thenReturn(wallet);
        when(walletTopupMapper.findByIdempotencyKey("key-1")).thenReturn(null);
        when(stripeClient.createPaymentIntent(any(), any())).thenThrow(mock(StripeException.class));

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> topupService.createStripeIntent(1L, "key-1", intentRequest())
        );

        assertEquals(WalletErrorCode.STRIPE_UNAVAILABLE, exception.getErrorCode());
        verify(walletTopupMapper, never()).insert(any());
    }

    @Test
    void createStripeIntent_returnsExistingResult_whenIdempotencyKeyAlreadyUsedWithSameAmount() throws StripeException {
        Wallet wallet = new Wallet(100L, "KRW", BigDecimal.valueOf(50000), "ACTIVE");
        when(walletMapper.findByMemberId(1L)).thenReturn(wallet);

        WalletTopup existing = topup(55L); // topup() 헬퍼는 sourceAmount = 10000으로 고정됨 (intentRequest()와 동일 금액)
        when(walletTopupMapper.findByIdempotencyKey("key-1")).thenReturn(existing);

        StripePaymentIntent refreshed = new StripePaymentIntent(
            existing.getProviderPaymentId(), "refreshed_secret", existing.getProviderStatus()
        );
        when(stripeClient.retrievePaymentIntent(existing.getProviderPaymentId())).thenReturn(refreshed);

        StripeIntentResponse response = topupService.createStripeIntent(1L, "key-1", intentRequest());

        assertEquals("refreshed_secret", response.clientSecret());
        assertEquals(existing.getProviderPaymentId(), response.providerPaymentId());
        verify(stripeClient, never()).createPaymentIntent(any(), any());
        verify(walletTopupMapper, never()).insert(any());
    }

    @Test
    void createStripeIntent_throwsConflict_whenIdempotencyKeyReusedWithDifferentAmount() {
        Wallet wallet = new Wallet(100L, "KRW", BigDecimal.valueOf(50000), "ACTIVE");
        when(walletMapper.findByMemberId(1L)).thenReturn(wallet);

        WalletTopup existing = topup(55L); // sourceAmount = 10000
        when(walletTopupMapper.findByIdempotencyKey("key-1")).thenReturn(existing);

        StripeIntentCreateRequest differentAmountRequest =
            new StripeIntentCreateRequest(BigDecimal.valueOf(99999), "KRW");

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> topupService.createStripeIntent(1L, "key-1", differentAmountRequest)
        );

        assertEquals(WalletErrorCode.IDEMPOTENCY_KEY_CONFLICT, exception.getErrorCode());
        verifyNoInteractions(stripeClient);
    }

    @Test
    void createStripeIntent_throwsUnavailable_whenRetrievingExistingIntentFails() throws StripeException {
        Wallet wallet = new Wallet(100L, "KRW", BigDecimal.valueOf(50000), "ACTIVE");
        when(walletMapper.findByMemberId(1L)).thenReturn(wallet);

        WalletTopup existing = topup(55L);
        when(walletTopupMapper.findByIdempotencyKey("key-1")).thenReturn(existing);
        when(stripeClient.retrievePaymentIntent(existing.getProviderPaymentId())).thenThrow(mock(StripeException.class));

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> topupService.createStripeIntent(1L, "key-1", intentRequest())
        );

        assertEquals(WalletErrorCode.STRIPE_UNAVAILABLE, exception.getErrorCode());
    }

    // ===== getStripeTopupStatus (Stripe 충전 상태 조회) =====

    private WalletTopup topupWithStatus(
        long topupId, long walletId, String topupStatus, String providerStatus, Long transferId
    ) {
        return new WalletTopup(
            BigDecimal.valueOf(10000), "KRW", BigDecimal.ONE, LocalDateTime.now(),
            topupId, topupStatus, BigDecimal.valueOf(10000), null, LocalDateTime.now(),
            walletId, "stripe", "pi_" + topupId, providerStatus, "idem-" + topupId, transferId
        );
    }

    @Test
    void getStripeTopupStatus_throwsBusinessException_whenWalletNotFound() {
        when(walletMapper.findByMemberId(1L)).thenReturn(null);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> topupService.getStripeTopupStatus(1L, 88L)
        );

        assertEquals(WalletErrorCode.WALLET_NOT_FOUND, exception.getErrorCode());
        verifyNoInteractions(walletTopupMapper);
    }

    @Test
    void getStripeTopupStatus_throwsBusinessException_whenTopupNotFound() {
        Wallet wallet = new Wallet(100L, "KRW", BigDecimal.valueOf(50000), "ACTIVE");
        when(walletMapper.findByMemberId(1L)).thenReturn(wallet);
        when(walletTopupMapper.findByTopupId(88L)).thenReturn(null);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> topupService.getStripeTopupStatus(1L, 88L)
        );

        assertEquals(WalletErrorCode.TOPUP_NOT_FOUND, exception.getErrorCode());
        verifyNoInteractions(stripeClient);
    }

    @Test
    void getStripeTopupStatus_throwsBusinessException_whenNotOwner() {
        Wallet wallet = new Wallet(100L, "KRW", BigDecimal.valueOf(50000), "ACTIVE");
        when(walletMapper.findByMemberId(1L)).thenReturn(wallet);
        WalletTopup topup = topupWithStatus(88L, 999L, "QUOTED", "requires_payment_method", null);
        when(walletTopupMapper.findByTopupId(88L)).thenReturn(topup);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> topupService.getStripeTopupStatus(1L, 88L)
        );

        assertEquals(WalletErrorCode.TOPUP_FORBIDDEN, exception.getErrorCode());
        verifyNoInteractions(stripeClient);
    }

    @Test
    void getStripeTopupStatus_returnsSuccessWithoutCallingStripe_whenAlreadyCompleted() {
        Wallet wallet = new Wallet(100L, "KRW", BigDecimal.valueOf(60000), "ACTIVE");
        when(walletMapper.findByMemberId(1L)).thenReturn(wallet);
        WalletTopup topup = topupWithStatus(88L, 100L, "COMPLETED", "succeeded", 500L);
        when(walletTopupMapper.findByTopupId(88L)).thenReturn(topup);

        StripeTopupStatusResponse response = topupService.getStripeTopupStatus(1L, 88L);

        assertEquals("SUCCESS", response.status());
        assertEquals(500L, response.transactionId());
        assertFalse(response.retryable());
        verifyNoInteractions(stripeClient);
    }

    @Test
    void getStripeTopupStatus_returnsFailedWithRetryableTrue_whenAlreadyFailed() {
        Wallet wallet = new Wallet(100L, "KRW", BigDecimal.valueOf(50000), "ACTIVE");
        when(walletMapper.findByMemberId(1L)).thenReturn(wallet);
        WalletTopup topup = topupWithStatus(88L, 100L, "FAILED", "requires_payment_method", null);
        when(walletTopupMapper.findByTopupId(88L)).thenReturn(topup);

        StripeTopupStatusResponse response = topupService.getStripeTopupStatus(1L, 88L);

        assertEquals("FAILED", response.status());
        assertNull(response.transactionId());
        assertTrue(response.retryable());
        verifyNoInteractions(stripeClient);
    }

    @Test
    void getStripeTopupStatus_creditsWalletAndReturnsSuccess_whenStripeReportsSucceeded() throws StripeException {
        Wallet initialWallet = new Wallet(100L, "KRW", BigDecimal.valueOf(50000), "ACTIVE");
        Wallet lockedWallet = new Wallet(100L, "KRW", BigDecimal.valueOf(50000), "ACTIVE");
        Wallet walletAfterCredit = new Wallet(100L, "KRW", BigDecimal.valueOf(60000), "ACTIVE");
        // getStripeTopupStatus는 크레딧 반영 전/후로 지갑을 두 번 조회한다 (①본인 확인, ⑤최신 잔액 재조회)
        when(walletMapper.findByMemberId(1L)).thenReturn(initialWallet, walletAfterCredit);

        WalletTopup topup = topupWithStatus(88L, 100L, "QUOTED", "requires_payment_method", null);
        WalletTopup completedTopup = topupWithStatus(88L, 100L, "COMPLETED", "succeeded", 999L);
        // findByTopupId도 크레딧 전/후로 두 번 조회한다 (applyProviderUpdate 마지막에 다시 읽어옴)
        when(walletTopupMapper.findByTopupId(88L)).thenReturn(topup, completedTopup);

        when(stripeClient.retrievePaymentIntent("pi_88"))
            .thenReturn(new StripePaymentIntent("pi_88", "secret", "succeeded"));
        when(walletMapper.findByWalletIdForUpdate(100L)).thenReturn(lockedWallet);
        when(transactionNumberGenerator.generate()).thenReturn("TXN-20260806-ABCDEFGH");

        // MyBatis의 useGeneratedKeys를 흉내내서, insert 호출 시 넘어온 WalletTransfer에 PK를 채워준다
        doAnswer(invocation -> {
            WalletTransfer transfer = invocation.getArgument(0);
            transfer.setTransferId(999L);
            return null;
        }).when(walletTransferMapper).insert(any());

        StripeTopupStatusResponse response = topupService.getStripeTopupStatus(1L, 88L);

        assertEquals("SUCCESS", response.status());
        assertEquals(999L, response.transactionId());
        assertEquals(0, BigDecimal.valueOf(60000).compareTo(response.sandboxBalance()));
        assertFalse(response.retryable());

        verify(walletMapper).updateBalance(100L, BigDecimal.valueOf(60000));
        verify(walletLedgerMapper).insert(999L, 100L, "CREDIT", BigDecimal.valueOf(10000), BigDecimal.valueOf(60000));
        verify(walletTopupMapper).markCompleted(eq(88L), eq(999L), eq("succeeded"), any(LocalDateTime.class));
    }

    @Test
    void getStripeTopupStatus_marksCancelled_whenStripeReportsCanceled() throws StripeException {
        Wallet wallet = new Wallet(100L, "KRW", BigDecimal.valueOf(50000), "ACTIVE");
        when(walletMapper.findByMemberId(1L)).thenReturn(wallet);

        WalletTopup topup = topupWithStatus(88L, 100L, "QUOTED", "requires_payment_method", null);
        WalletTopup cancelledTopup = topupWithStatus(88L, 100L, "CANCELLED", "canceled", null);
        when(walletTopupMapper.findByTopupId(88L)).thenReturn(topup, cancelledTopup);

        when(stripeClient.retrievePaymentIntent("pi_88"))
            .thenReturn(new StripePaymentIntent("pi_88", "secret", "canceled"));

        StripeTopupStatusResponse response = topupService.getStripeTopupStatus(1L, 88L);

        assertEquals("CANCELLED", response.status());
        assertNull(response.transactionId());
        verify(walletTopupMapper).markCancelled(88L, "canceled");
        verifyNoInteractions(walletTransferMapper);
        verifyNoInteractions(walletLedgerMapper);
    }

    @Test
    void getStripeTopupStatus_returnsPending_whenStripeStillProcessing() throws StripeException {
        Wallet wallet = new Wallet(100L, "KRW", BigDecimal.valueOf(50000), "ACTIVE");
        when(walletMapper.findByMemberId(1L)).thenReturn(wallet);

        WalletTopup topup = topupWithStatus(88L, 100L, "QUOTED", "requires_payment_method", null);
        WalletTopup processingTopup = topupWithStatus(88L, 100L, "QUOTED", "processing", null);
        when(walletTopupMapper.findByTopupId(88L)).thenReturn(topup, processingTopup);

        when(stripeClient.retrievePaymentIntent("pi_88"))
            .thenReturn(new StripePaymentIntent("pi_88", "secret", "processing"));

        StripeTopupStatusResponse response = topupService.getStripeTopupStatus(1L, 88L);

        assertEquals("PENDING", response.status());
        verify(walletTopupMapper).updateProviderStatus(88L, "processing");
        verifyNoInteractions(walletTransferMapper);
    }

    @Test
    void getStripeTopupStatus_returnsReady_whenStripeAwaitingPaymentMethod() throws StripeException {
        Wallet wallet = new Wallet(100L, "KRW", BigDecimal.valueOf(50000), "ACTIVE");
        when(walletMapper.findByMemberId(1L)).thenReturn(wallet);

        WalletTopup topup = topupWithStatus(88L, 100L, "QUOTED", "requires_payment_method", null);
        when(walletTopupMapper.findByTopupId(88L)).thenReturn(topup, topup);

        when(stripeClient.retrievePaymentIntent("pi_88"))
            .thenReturn(new StripePaymentIntent("pi_88", "secret", "requires_payment_method"));

        StripeTopupStatusResponse response = topupService.getStripeTopupStatus(1L, 88L);

        assertEquals("READY", response.status());
        verify(walletTopupMapper).updateProviderStatus(88L, "requires_payment_method");
    }

    @Test
    void getStripeTopupStatus_throwsUnavailable_whenStripeRetrieveFails() throws StripeException {
        Wallet wallet = new Wallet(100L, "KRW", BigDecimal.valueOf(50000), "ACTIVE");
        when(walletMapper.findByMemberId(1L)).thenReturn(wallet);

        WalletTopup topup = topupWithStatus(88L, 100L, "QUOTED", "requires_payment_method", null);
        when(walletTopupMapper.findByTopupId(88L)).thenReturn(topup);

        when(stripeClient.retrievePaymentIntent("pi_88")).thenThrow(mock(StripeException.class));

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> topupService.getStripeTopupStatus(1L, 88L)
        );

        assertEquals(WalletErrorCode.STRIPE_UNAVAILABLE, exception.getErrorCode());
        verifyNoInteractions(walletTransferMapper);
    }

    // ===== applyStripeWebhookEvent (Stripe Webhook 수신) =====

    // getObject()가 바로 값을 돌려주는 경로만 쓰므로, deserializeUnsafe() 폴백용 event.getId()는 스텁하지 않는다
    // (스텁해두면 안 쓰여서 strict stubbing 에러가 난다).
    private Event mockEvent(String type, PaymentIntent paymentIntent) {
        Event event = mock(Event.class);
        when(event.getType()).thenReturn(type);

        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        when(deserializer.getObject()).thenReturn(Optional.ofNullable(paymentIntent));
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);

        return event;
    }

    private PaymentIntent mockPaymentIntent(String providerPaymentId, String status) {
        PaymentIntent intent = mock(PaymentIntent.class);
        when(intent.getId()).thenReturn(providerPaymentId);
        when(intent.getStatus()).thenReturn(status);
        return intent;
    }

    @Test
    void applyStripeWebhookEvent_throwsBusinessException_whenSignatureInvalid() throws StripeException {
        when(stripeClient.constructWebhookEvent(any(), any()))
            .thenThrow(mock(SignatureVerificationException.class));

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> topupService.applyStripeWebhookEvent("payload", "bad-signature")
        );

        assertEquals(WalletErrorCode.STRIPE_INVALID_SIGNATURE, exception.getErrorCode());
        verifyNoInteractions(walletTopupMapper);
    }

    @Test
    void applyStripeWebhookEvent_returnsReceivedOnly_whenEventTypeIsNotPaymentIntent() throws StripeException {
        // payment_intent.* 타입이 아니면 event.getType()만 확인하고 바로 반환하므로, 그 외엔 아무것도 스텁하지 않는다
        Event event = mock(Event.class);
        when(event.getType()).thenReturn("charge.succeeded");
        when(stripeClient.constructWebhookEvent(any(), any())).thenReturn(event);

        StripeWebhookResponse response = topupService.applyStripeWebhookEvent("payload", "sig");

        assertTrue(response.received());
        assertFalse(response.alreadyProcessed());
        verifyNoInteractions(walletTopupMapper);
    }

    @Test
    void applyStripeWebhookEvent_returnsReceivedOnly_whenPaymentIntentUnknown() throws StripeException {
        // findByProviderPaymentId에서 null이 나오면 바로 반환하므로 getStatus()까진 안 쓰인다 — getId()만 스텁
        PaymentIntent paymentIntent = mock(PaymentIntent.class);
        when(paymentIntent.getId()).thenReturn("pi_unknown");
        Event event = mockEvent("payment_intent.succeeded", paymentIntent);
        when(stripeClient.constructWebhookEvent(any(), any())).thenReturn(event);
        when(walletTopupMapper.findByProviderPaymentId("pi_unknown")).thenReturn(null);

        StripeWebhookResponse response = topupService.applyStripeWebhookEvent("payload", "sig");

        assertTrue(response.received());
        assertFalse(response.alreadyProcessed());
        verifyNoInteractions(walletTransferMapper);
    }

    @Test
    void applyStripeWebhookEvent_returnsAlreadyProcessed_whenTopupAlreadyTerminal() throws StripeException {
        // 이미 최종 상태면 status를 보기도 전에 반환하므로 getStatus()까진 안 쓰인다 — getId()만 스텁
        PaymentIntent paymentIntent = mock(PaymentIntent.class);
        when(paymentIntent.getId()).thenReturn("pi_88");
        Event event = mockEvent("payment_intent.succeeded", paymentIntent);
        when(stripeClient.constructWebhookEvent(any(), any())).thenReturn(event);

        WalletTopup topup = topupWithStatus(88L, 100L, "COMPLETED", "succeeded", 500L);
        when(walletTopupMapper.findByProviderPaymentId("pi_88")).thenReturn(topup);

        StripeWebhookResponse response = topupService.applyStripeWebhookEvent("payload", "sig");

        assertTrue(response.received());
        assertTrue(response.alreadyProcessed());
        verifyNoInteractions(walletTransferMapper);
        verifyNoInteractions(walletLedgerMapper);
    }

    @Test
    void applyStripeWebhookEvent_creditsWallet_whenEventIsSucceeded() throws StripeException {
        PaymentIntent paymentIntent = mockPaymentIntent("pi_88", "succeeded");
        Event event = mockEvent("payment_intent.succeeded", paymentIntent);
        when(stripeClient.constructWebhookEvent(any(), any())).thenReturn(event);

        WalletTopup topup = topupWithStatus(88L, 100L, "QUOTED", "requires_payment_method", null);
        when(walletTopupMapper.findByProviderPaymentId("pi_88")).thenReturn(topup);
        // applyProviderUpdate 마지막에 findByTopupId로 다시 읽는데, 웹훅 응답 자체엔 그 값을 안 쓰므로 아무거나 반환해도 된다
        when(walletTopupMapper.findByTopupId(88L)).thenReturn(topup);

        Wallet lockedWallet = new Wallet(100L, "KRW", BigDecimal.valueOf(50000), "ACTIVE");
        when(walletMapper.findByWalletIdForUpdate(100L)).thenReturn(lockedWallet);
        when(transactionNumberGenerator.generate()).thenReturn("TXN-20260806-ABCDEFGH");
        doAnswer(invocation -> {
            WalletTransfer transfer = invocation.getArgument(0);
            transfer.setTransferId(999L);
            return null;
        }).when(walletTransferMapper).insert(any());

        StripeWebhookResponse response = topupService.applyStripeWebhookEvent("payload", "sig");

        assertTrue(response.received());
        assertFalse(response.alreadyProcessed());
        verify(walletMapper).updateBalance(100L, BigDecimal.valueOf(60000));
        verify(walletLedgerMapper).insert(999L, 100L, "CREDIT", BigDecimal.valueOf(10000), BigDecimal.valueOf(60000));
        verify(walletTopupMapper).markCompleted(eq(88L), eq(999L), eq("succeeded"), any(LocalDateTime.class));
    }

    @Test
    void applyStripeWebhookEvent_marksFailed_whenEventTypeIsPaymentFailed() throws StripeException {
        // Stripe는 실패해도 PaymentIntent 상태를 requires_payment_method로 되돌리는 경우가 많아서,
        // 이벤트 타입(payment_intent.payment_failed)만으로 실패를 판단해야 한다 — status 값 자체는 실패를 안 나타냄.
        PaymentIntent paymentIntent = mockPaymentIntent("pi_88", "requires_payment_method");
        Event event = mockEvent("payment_intent.payment_failed", paymentIntent);
        when(stripeClient.constructWebhookEvent(any(), any())).thenReturn(event);

        WalletTopup topup = topupWithStatus(88L, 100L, "QUOTED", "requires_payment_method", null);
        when(walletTopupMapper.findByProviderPaymentId("pi_88")).thenReturn(topup);
        when(walletTopupMapper.findByTopupId(88L)).thenReturn(topup);

        StripeWebhookResponse response = topupService.applyStripeWebhookEvent("payload", "sig");

        assertTrue(response.received());
        assertFalse(response.alreadyProcessed());
        verify(walletTopupMapper).markFailed(88L, "requires_payment_method");
        verifyNoInteractions(walletTransferMapper);
        verifyNoInteractions(walletLedgerMapper);
    }

    @Test
    void applyStripeWebhookEvent_updatesProviderStatusOnly_whenStillProcessing() throws StripeException {
        PaymentIntent paymentIntent = mockPaymentIntent("pi_88", "processing");
        Event event = mockEvent("payment_intent.processing", paymentIntent);
        when(stripeClient.constructWebhookEvent(any(), any())).thenReturn(event);

        WalletTopup topup = topupWithStatus(88L, 100L, "QUOTED", "requires_payment_method", null);
        when(walletTopupMapper.findByProviderPaymentId("pi_88")).thenReturn(topup);
        when(walletTopupMapper.findByTopupId(88L)).thenReturn(topup);

        StripeWebhookResponse response = topupService.applyStripeWebhookEvent("payload", "sig");

        assertTrue(response.received());
        assertFalse(response.alreadyProcessed());
        verify(walletTopupMapper).updateProviderStatus(88L, "processing");
        verifyNoInteractions(walletTransferMapper);
    }
}
