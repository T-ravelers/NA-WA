package me.nawa.wallet.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import me.nawa.common.exception.BusinessException;
import me.nawa.wallet.domain.QrPaymentAppointmentMembership;
import me.nawa.wallet.domain.QrPaymentCode;
import me.nawa.wallet.domain.QrPaymentResolveTarget;
import me.nawa.wallet.domain.Wallet;
import me.nawa.wallet.domain.WalletLedgerEntry;
import me.nawa.wallet.domain.WalletTransfer;
import me.nawa.wallet.domain.enums.QrPaymentStatus;
import me.nawa.wallet.domain.enums.SpendingScope;
import me.nawa.wallet.dto.request.QrPaymentCreateRequest;
import me.nawa.wallet.dto.request.QrPaymentExecuteRequest;
import me.nawa.wallet.dto.request.QrPaymentPreviewRequest;
import me.nawa.wallet.dto.request.QrPaymentResolveRequest;
import me.nawa.wallet.dto.response.QrPaymentCreateResponse;
import me.nawa.wallet.dto.response.QrPaymentExecuteResponse;
import me.nawa.wallet.dto.response.QrPaymentPreviewResponse;
import me.nawa.wallet.dto.response.QrPaymentResolveResponse;
import me.nawa.wallet.dto.response.QrPaymentStatusResponse;
import me.nawa.wallet.exception.WalletErrorCode;
import me.nawa.wallet.mapper.QrPaymentCodeMapper;
import me.nawa.wallet.mapper.TripExpenseLinkMapper;
import me.nawa.wallet.mapper.WalletLedgerMapper;
import me.nawa.wallet.mapper.WalletMapper;
import me.nawa.wallet.mapper.WalletTransferMapper;
import me.nawa.wallet.util.QrTokenGenerator;
import me.nawa.wallet.util.TransactionNumberGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QrPaymentServiceImplTest {

    @Mock
    private WalletMapper walletMapper;

    @Mock
    private QrPaymentCodeMapper qrPaymentCodeMapper;

    @Mock
    private QrTokenGenerator qrTokenGenerator;

    @Mock
    private WalletTransferMapper walletTransferMapper;

    @Mock
    private WalletLedgerMapper walletLedgerMapper;

    @Mock
    private TransactionNumberGenerator transactionNumberGenerator;

    @Mock
    private TripExpenseLinkMapper tripExpenseLinkMapper;

    @InjectMocks
    private QrPaymentServiceImpl qrPaymentService;

    private static final long MEMBER_ID = 1L;
    private static final long PAYER_WALLET_ID = 100L;
    private static final long PAYEE_WALLET_ID = 200L;

    private Wallet wallet(long walletId, BigDecimal balance, String status) {
        return new Wallet(walletId, "KRW", balance, status);
    }

    private QrPaymentCode qrPaymentCode(
        long qrPaymentCodeId, long payeeWalletId, Long completedTransferId, String qrToken,
        BigDecimal amount, String memo, QrPaymentStatus status, LocalDateTime expiresAt
    ) {
        return new QrPaymentCode(
            qrPaymentCodeId, payeeWalletId, completedTransferId, qrToken, amount, memo,
            status, expiresAt, null, null, null
        );
    }

    private QrPaymentResolveTarget resolveTarget(
        long qrPaymentCodeId, long payeeWalletId, Long completedTransferId, BigDecimal amount,
        QrPaymentStatus status, LocalDateTime expiresAt, String payeeWalletStatus
    ) {
        return new QrPaymentResolveTarget(
            qrPaymentCodeId, payeeWalletId, completedTransferId, amount, "야경 투어", status,
            expiresAt, "가이드", "KRW", payeeWalletStatus
        );
    }

    private WalletTransfer transfer(
        Long transferId, String transferType, String transferStatus, BigDecimal amount,
        Long initiatorMemberId, String idempotencyKey
    ) {
        return new WalletTransfer(
            transferId, "TXN-1", transferType, transferStatus, amount, "야경 투어", null, "PERSONAL",
            LocalDateTime.now(), LocalDateTime.now(), initiatorMemberId, idempotencyKey
        );
    }

    private WalletLedgerEntry ledgerEntry(BigDecimal balanceAfter, String entryType) {
        return new WalletLedgerEntry(
            500L, "QR_PAYMENT", entryType, BigDecimal.valueOf(18500), balanceAfter,
            "야경 투어", LocalDateTime.now(), 700L
        );
    }

    private QrPaymentAppointmentMembership membership(
        Long appointmentMemberId, Long appointmentId, Long tripId, String tripTitle
    ) {
        return new QrPaymentAppointmentMembership(
            appointmentMemberId, appointmentId, tripId, "서울 야경 투어 모임", tripTitle
        );
    }

    // ===== createPaymentQr =====

    @Test
    void createPaymentQr_createsActiveQr_whenFixedAmount() {
        Wallet wallet = wallet(PAYER_WALLET_ID, BigDecimal.valueOf(50000), "ACTIVE");
        when(walletMapper.findByMemberId(MEMBER_ID)).thenReturn(wallet);
        when(qrTokenGenerator.generate()).thenReturn("token-abc");

        doAnswer(invocation -> {
            QrPaymentCode code = invocation.getArgument(0);
            code.setQrPaymentCodeId(10L);
            return null;
        }).when(qrPaymentCodeMapper).insert(any());

        QrPaymentCreateRequest request =
            new QrPaymentCreateRequest(BigDecimal.valueOf(18500), "서울 야경 투어");

        QrPaymentCreateResponse response = qrPaymentService.createPaymentQr(MEMBER_ID, request);

        assertEquals(10L, response.qrPaymentCodeId());
        assertEquals("token-abc", response.qrToken());
        assertEquals(0, BigDecimal.valueOf(18500).compareTo(response.amount()));
        assertEquals("서울 야경 투어", response.memo());
        assertEquals("ACTIVE", response.status());
        assertEquals("KRW", response.currencyCode());
        assertNotNull(response.expiresAt());

        ArgumentCaptor<QrPaymentCode> captor = ArgumentCaptor.forClass(QrPaymentCode.class);
        verify(qrPaymentCodeMapper).insert(captor.capture());
        assertEquals(PAYER_WALLET_ID, captor.getValue().getPayeeWalletId());
        assertNull(captor.getValue().getCompletedTransferId());
    }

    @Test
    void createPaymentQr_allowsNullAmount_forPayerEntersAmountQr() {
        Wallet wallet = wallet(PAYER_WALLET_ID, BigDecimal.valueOf(50000), "ACTIVE");
        when(walletMapper.findByMemberId(MEMBER_ID)).thenReturn(wallet);
        when(qrTokenGenerator.generate()).thenReturn("token-abc");

        QrPaymentCreateRequest request = new QrPaymentCreateRequest(null, null);

        QrPaymentCreateResponse response = qrPaymentService.createPaymentQr(MEMBER_ID, request);

        assertNull(response.amount());
        assertNull(response.memo());
    }

    @Test
    void createPaymentQr_throwsInvalidInput_whenAmountIsZeroOrNegative() {
        QrPaymentCreateRequest zeroRequest = new QrPaymentCreateRequest(BigDecimal.ZERO, null);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> qrPaymentService.createPaymentQr(MEMBER_ID, zeroRequest)
        );

        assertEquals(me.nawa.common.exception.CommonErrorCode.INVALID_INPUT, exception.getErrorCode());
        verifyNoInteractions(walletMapper);
    }

    @Test
    void createPaymentQr_throwsInvalidInput_whenAmountExceedsDecimalScale() {
        QrPaymentCreateRequest request =
            new QrPaymentCreateRequest(new BigDecimal("1.00001"), null);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> qrPaymentService.createPaymentQr(MEMBER_ID, request)
        );

        assertEquals(me.nawa.common.exception.CommonErrorCode.INVALID_INPUT, exception.getErrorCode());
        verifyNoInteractions(walletMapper);
    }

    @Test
    void createPaymentQr_throwsInvalidInput_whenAmountExceedsIntegerDigits() {
        QrPaymentCreateRequest request =
            new QrPaymentCreateRequest(new BigDecimal("1000000000000000"), null);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> qrPaymentService.createPaymentQr(MEMBER_ID, request)
        );

        assertEquals(me.nawa.common.exception.CommonErrorCode.INVALID_INPUT, exception.getErrorCode());
        verifyNoInteractions(walletMapper);
    }

    @Test
    void previewPayment_throwsInvalidInput_whenPayerEnteredAmountExceedsDecimalScale() {
        when(walletMapper.findByMemberId(MEMBER_ID))
            .thenReturn(wallet(PAYER_WALLET_ID, BigDecimal.valueOf(50000), "ACTIVE"));
        QrPaymentResolveTarget target = resolveTarget(
            10L, PAYEE_WALLET_ID, null, null,
            QrPaymentStatus.ACTIVE, LocalDateTime.now().plusMinutes(5), "ACTIVE"
        );
        when(qrPaymentCodeMapper.findResolveTargetByToken("token-abc")).thenReturn(target);

        QrPaymentPreviewRequest request = new QrPaymentPreviewRequest(
            "token-abc", new BigDecimal("1.00001"), SpendingScope.PERSONAL, null
        );

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> qrPaymentService.previewPayment(MEMBER_ID, request)
        );

        assertEquals(me.nawa.common.exception.CommonErrorCode.INVALID_INPUT, exception.getErrorCode());
    }

    @Test
    void createPaymentQr_throwsInvalidInput_whenMemoTooLong() {
        QrPaymentCreateRequest request =
            new QrPaymentCreateRequest(BigDecimal.valueOf(1000), "가".repeat(256));

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> qrPaymentService.createPaymentQr(MEMBER_ID, request)
        );

        assertEquals(me.nawa.common.exception.CommonErrorCode.INVALID_INPUT, exception.getErrorCode());
        verifyNoInteractions(walletMapper);
    }

    @Test
    void createPaymentQr_throwsWalletNotFound_whenNoWallet() {
        when(walletMapper.findByMemberId(MEMBER_ID)).thenReturn(null);

        QrPaymentCreateRequest request = new QrPaymentCreateRequest(BigDecimal.valueOf(1000), null);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> qrPaymentService.createPaymentQr(MEMBER_ID, request)
        );

        assertEquals(WalletErrorCode.WALLET_NOT_FOUND, exception.getErrorCode());
        verifyNoInteractions(qrPaymentCodeMapper);
    }

    @Test
    void createPaymentQr_throwsWalletNotActive_whenWalletSuspended() {
        Wallet wallet = wallet(PAYER_WALLET_ID, BigDecimal.valueOf(50000), "SUSPENDED");
        when(walletMapper.findByMemberId(MEMBER_ID)).thenReturn(wallet);

        QrPaymentCreateRequest request = new QrPaymentCreateRequest(BigDecimal.valueOf(1000), null);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> qrPaymentService.createPaymentQr(MEMBER_ID, request)
        );

        assertEquals(WalletErrorCode.WALLET_NOT_ACTIVE, exception.getErrorCode());
        verifyNoInteractions(qrPaymentCodeMapper);
    }

    // ===== listActivePaymentQrs =====

    @Test
    void listActivePaymentQrs_returnsActiveQrs_mappedToCreateResponse() {
        Wallet wallet = wallet(PAYER_WALLET_ID, BigDecimal.valueOf(50000), "ACTIVE");
        when(walletMapper.findByMemberId(MEMBER_ID)).thenReturn(wallet);

        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(1);
        QrPaymentCode active = qrPaymentCode(
            10L, PAYER_WALLET_ID, null, "token-abc",
            BigDecimal.valueOf(18500), "서울 야경 투어", QrPaymentStatus.ACTIVE, expiresAt
        );
        when(qrPaymentCodeMapper.findActiveByPayeeWalletId(eq(PAYER_WALLET_ID), any()))
            .thenReturn(List.of(active));

        List<QrPaymentCreateResponse> responses =
            qrPaymentService.listActivePaymentQrs(MEMBER_ID);

        assertEquals(1, responses.size());
        QrPaymentCreateResponse response = responses.get(0);
        assertEquals(10L, response.qrPaymentCodeId());
        assertEquals("token-abc", response.qrToken());
        assertEquals(0, BigDecimal.valueOf(18500).compareTo(response.amount()));
        assertEquals("서울 야경 투어", response.memo());
        assertEquals("ACTIVE", response.status());
        assertEquals("KRW", response.currencyCode());
        assertEquals(expiresAt, response.expiresAt());
    }

    @Test
    void listActivePaymentQrs_returnsEmptyList_whenNoneActive() {
        Wallet wallet = wallet(PAYER_WALLET_ID, BigDecimal.valueOf(50000), "ACTIVE");
        when(walletMapper.findByMemberId(MEMBER_ID)).thenReturn(wallet);
        when(qrPaymentCodeMapper.findActiveByPayeeWalletId(eq(PAYER_WALLET_ID), any()))
            .thenReturn(List.of());

        List<QrPaymentCreateResponse> responses =
            qrPaymentService.listActivePaymentQrs(MEMBER_ID);

        assertTrue(responses.isEmpty());
    }

    @Test
    void listActivePaymentQrs_throwsWalletNotFound_whenNoWallet() {
        when(walletMapper.findByMemberId(MEMBER_ID)).thenReturn(null);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> qrPaymentService.listActivePaymentQrs(MEMBER_ID)
        );

        assertEquals(WalletErrorCode.WALLET_NOT_FOUND, exception.getErrorCode());
        verifyNoInteractions(qrPaymentCodeMapper);
    }

    // ===== resolvePaymentQr =====

    private QrPaymentResolveRequest resolveRequest(String qrToken) {
        return new QrPaymentResolveRequest(qrToken);
    }

    @Test
    void resolvePaymentQr_returnsQrInfo_whenActiveAndFixedAmount() {
        Wallet payerWallet = wallet(PAYER_WALLET_ID, BigDecimal.valueOf(50000), "ACTIVE");
        when(walletMapper.findByMemberId(MEMBER_ID)).thenReturn(payerWallet);

        QrPaymentResolveTarget target = resolveTarget(
            10L, PAYEE_WALLET_ID, null, BigDecimal.valueOf(18500),
            QrPaymentStatus.ACTIVE, LocalDateTime.now().plusMinutes(5), "ACTIVE"
        );
        when(qrPaymentCodeMapper.findResolveTargetByToken("token-abc")).thenReturn(target);

        QrPaymentResolveResponse response =
            qrPaymentService.resolvePaymentQr(MEMBER_ID, resolveRequest("token-abc"));

        assertEquals(10L, response.qrPaymentId());
        assertEquals("가이드", response.payeeName());
        assertEquals(0, BigDecimal.valueOf(18500).compareTo(response.amount()));
        assertFalse(response.amountInputRequired());
        assertEquals("ACTIVE", response.status());
    }

    @Test
    void resolvePaymentQr_marksAmountInputRequired_whenQrAmountIsNull() {
        Wallet payerWallet = wallet(PAYER_WALLET_ID, BigDecimal.valueOf(50000), "ACTIVE");
        when(walletMapper.findByMemberId(MEMBER_ID)).thenReturn(payerWallet);

        QrPaymentResolveTarget target = resolveTarget(
            10L, PAYEE_WALLET_ID, null, null,
            QrPaymentStatus.ACTIVE, LocalDateTime.now().plusMinutes(5), "ACTIVE"
        );
        when(qrPaymentCodeMapper.findResolveTargetByToken("token-abc")).thenReturn(target);

        QrPaymentResolveResponse response =
            qrPaymentService.resolvePaymentQr(MEMBER_ID, resolveRequest("token-abc"));

        assertTrue(response.amountInputRequired());
    }

    @Test
    void resolvePaymentQr_throwsInvalidInput_whenTokenBlank() {
        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> qrPaymentService.resolvePaymentQr(MEMBER_ID, resolveRequest("   "))
        );

        assertEquals(me.nawa.common.exception.CommonErrorCode.INVALID_INPUT, exception.getErrorCode());
        verifyNoInteractions(walletMapper);
    }

    @Test
    void resolvePaymentQr_throwsWalletNotFound_whenPayerWalletMissing() {
        when(walletMapper.findByMemberId(MEMBER_ID)).thenReturn(null);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> qrPaymentService.resolvePaymentQr(MEMBER_ID, resolveRequest("token-abc"))
        );

        assertEquals(WalletErrorCode.WALLET_NOT_FOUND, exception.getErrorCode());
        verifyNoInteractions(qrPaymentCodeMapper);
    }

    @Test
    void resolvePaymentQr_throwsQrPaymentNotFound_whenTokenUnknown() {
        when(walletMapper.findByMemberId(MEMBER_ID))
            .thenReturn(wallet(PAYER_WALLET_ID, BigDecimal.valueOf(50000), "ACTIVE"));
        when(qrPaymentCodeMapper.findResolveTargetByToken("token-abc")).thenReturn(null);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> qrPaymentService.resolvePaymentQr(MEMBER_ID, resolveRequest("token-abc"))
        );

        assertEquals(WalletErrorCode.QR_PAYMENT_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void resolvePaymentQr_throwsAlreadyCompleted_whenCompletedTransferIdSet() {
        when(walletMapper.findByMemberId(MEMBER_ID))
            .thenReturn(wallet(PAYER_WALLET_ID, BigDecimal.valueOf(50000), "ACTIVE"));

        QrPaymentResolveTarget target = resolveTarget(
            10L, PAYEE_WALLET_ID, 900L, BigDecimal.valueOf(18500),
            QrPaymentStatus.COMPLETED, LocalDateTime.now().plusMinutes(5), "ACTIVE"
        );
        when(qrPaymentCodeMapper.findResolveTargetByToken("token-abc")).thenReturn(target);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> qrPaymentService.resolvePaymentQr(MEMBER_ID, resolveRequest("token-abc"))
        );

        assertEquals(WalletErrorCode.QR_PAYMENT_ALREADY_COMPLETED, exception.getErrorCode());
    }

    @Test
    void resolvePaymentQr_throwsExpired_whenStatusExpired() {
        when(walletMapper.findByMemberId(MEMBER_ID))
            .thenReturn(wallet(PAYER_WALLET_ID, BigDecimal.valueOf(50000), "ACTIVE"));

        QrPaymentResolveTarget target = resolveTarget(
            10L, PAYEE_WALLET_ID, null, BigDecimal.valueOf(18500),
            QrPaymentStatus.EXPIRED, LocalDateTime.now().minusMinutes(1), "ACTIVE"
        );
        when(qrPaymentCodeMapper.findResolveTargetByToken("token-abc")).thenReturn(target);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> qrPaymentService.resolvePaymentQr(MEMBER_ID, resolveRequest("token-abc"))
        );

        assertEquals(WalletErrorCode.QR_PAYMENT_EXPIRED, exception.getErrorCode());
    }

    @Test
    void resolvePaymentQr_throwsNotActive_whenStatusCancelled() {
        when(walletMapper.findByMemberId(MEMBER_ID))
            .thenReturn(wallet(PAYER_WALLET_ID, BigDecimal.valueOf(50000), "ACTIVE"));

        QrPaymentResolveTarget target = resolveTarget(
            10L, PAYEE_WALLET_ID, null, BigDecimal.valueOf(18500),
            QrPaymentStatus.CANCELLED, LocalDateTime.now().plusMinutes(5), "ACTIVE"
        );
        when(qrPaymentCodeMapper.findResolveTargetByToken("token-abc")).thenReturn(target);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> qrPaymentService.resolvePaymentQr(MEMBER_ID, resolveRequest("token-abc"))
        );

        assertEquals(WalletErrorCode.QR_PAYMENT_NOT_ACTIVE, exception.getErrorCode());
    }

    @Test
    void resolvePaymentQr_throwsExpired_whenActiveButPastDeadline() {
        when(walletMapper.findByMemberId(MEMBER_ID))
            .thenReturn(wallet(PAYER_WALLET_ID, BigDecimal.valueOf(50000), "ACTIVE"));

        QrPaymentResolveTarget target = resolveTarget(
            10L, PAYEE_WALLET_ID, null, BigDecimal.valueOf(18500),
            QrPaymentStatus.ACTIVE, LocalDateTime.now().minusSeconds(1), "ACTIVE"
        );
        when(qrPaymentCodeMapper.findResolveTargetByToken("token-abc")).thenReturn(target);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> qrPaymentService.resolvePaymentQr(MEMBER_ID, resolveRequest("token-abc"))
        );

        assertEquals(WalletErrorCode.QR_PAYMENT_EXPIRED, exception.getErrorCode());
    }

    @Test
    void resolvePaymentQr_throwsWalletNotActive_whenPayerWalletSuspended() {
        when(walletMapper.findByMemberId(MEMBER_ID))
            .thenReturn(wallet(PAYER_WALLET_ID, BigDecimal.valueOf(50000), "SUSPENDED"));

        QrPaymentResolveTarget target = resolveTarget(
            10L, PAYEE_WALLET_ID, null, BigDecimal.valueOf(18500),
            QrPaymentStatus.ACTIVE, LocalDateTime.now().plusMinutes(5), "ACTIVE"
        );
        when(qrPaymentCodeMapper.findResolveTargetByToken("token-abc")).thenReturn(target);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> qrPaymentService.resolvePaymentQr(MEMBER_ID, resolveRequest("token-abc"))
        );

        assertEquals(WalletErrorCode.WALLET_NOT_ACTIVE, exception.getErrorCode());
    }

    @Test
    void resolvePaymentQr_throwsSelfPaymentNotAllowed_whenPayerIsPayee() {
        when(walletMapper.findByMemberId(MEMBER_ID))
            .thenReturn(wallet(PAYER_WALLET_ID, BigDecimal.valueOf(50000), "ACTIVE"));

        // 결제자 지갑(PAYER_WALLET_ID)과 QR의 수취인 지갑을 동일하게 설정 — 자기 자신의 QR을 스캔한 상황
        QrPaymentResolveTarget target = resolveTarget(
            10L, PAYER_WALLET_ID, null, BigDecimal.valueOf(18500),
            QrPaymentStatus.ACTIVE, LocalDateTime.now().plusMinutes(5), "ACTIVE"
        );
        when(qrPaymentCodeMapper.findResolveTargetByToken("token-abc")).thenReturn(target);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> qrPaymentService.resolvePaymentQr(MEMBER_ID, resolveRequest("token-abc"))
        );

        assertEquals(WalletErrorCode.QR_SELF_PAYMENT_NOT_ALLOWED, exception.getErrorCode());
    }

    @Test
    void resolvePaymentQr_throwsPayeeWalletNotActive_whenPayeeWalletSuspended() {
        when(walletMapper.findByMemberId(MEMBER_ID))
            .thenReturn(wallet(PAYER_WALLET_ID, BigDecimal.valueOf(50000), "ACTIVE"));

        QrPaymentResolveTarget target = resolveTarget(
            10L, PAYEE_WALLET_ID, null, BigDecimal.valueOf(18500),
            QrPaymentStatus.ACTIVE, LocalDateTime.now().plusMinutes(5), "SUSPENDED"
        );
        when(qrPaymentCodeMapper.findResolveTargetByToken("token-abc")).thenReturn(target);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> qrPaymentService.resolvePaymentQr(MEMBER_ID, resolveRequest("token-abc"))
        );

        assertEquals(WalletErrorCode.QR_PAYEE_WALLET_NOT_ACTIVE, exception.getErrorCode());
    }

    // ===== previewPayment =====

    private void stubActiveTarget(BigDecimal amount) {
        when(walletMapper.findByMemberId(MEMBER_ID))
            .thenReturn(wallet(PAYER_WALLET_ID, BigDecimal.valueOf(50000), "ACTIVE"));

        QrPaymentResolveTarget target = resolveTarget(
            10L, PAYEE_WALLET_ID, null, amount,
            QrPaymentStatus.ACTIVE, LocalDateTime.now().plusMinutes(5), "ACTIVE"
        );
        when(qrPaymentCodeMapper.findResolveTargetByToken("token-abc")).thenReturn(target);
    }

    @Test
    void previewPayment_returnsPreview_whenPersonalAndCanPay() {
        stubActiveTarget(BigDecimal.valueOf(18500));

        QrPaymentPreviewRequest request =
            new QrPaymentPreviewRequest("token-abc", null, SpendingScope.PERSONAL, null);

        QrPaymentPreviewResponse response = qrPaymentService.previewPayment(MEMBER_ID, request);

        assertEquals(10L, response.qrPaymentId());
        assertEquals(0, BigDecimal.valueOf(18500).compareTo(response.amount()));
        assertEquals(0, BigDecimal.valueOf(50000).compareTo(response.currentBalance()));
        assertEquals(0, BigDecimal.valueOf(31500).compareTo(response.balanceAfter()));
        assertTrue(response.canPay());
        assertNull(response.trip());
        assertNull(response.appointment());
        verifyNoInteractions(tripExpenseLinkMapper);
    }

    @Test
    void previewPayment_returnsCanPayFalse_whenBalanceInsufficient() {
        stubActiveTarget(BigDecimal.valueOf(999999));

        QrPaymentPreviewRequest request =
            new QrPaymentPreviewRequest("token-abc", null, SpendingScope.PERSONAL, null);

        QrPaymentPreviewResponse response = qrPaymentService.previewPayment(MEMBER_ID, request);

        assertFalse(response.canPay());
    }

    @Test
    void previewPayment_includesTripAndAppointment_whenSharedAndMembershipValid() {
        stubActiveTarget(BigDecimal.valueOf(18500));
        when(qrPaymentCodeMapper.findActiveAppointmentMembership(MEMBER_ID, 55L))
            .thenReturn(membership(1L, 55L, 77L, "서울 야경 투어"));

        QrPaymentPreviewRequest request =
            new QrPaymentPreviewRequest("token-abc", null, SpendingScope.SHARED, 55L);

        QrPaymentPreviewResponse response = qrPaymentService.previewPayment(MEMBER_ID, request);

        assertNotNull(response.trip());
        assertEquals(77L, response.trip().tripId());
        assertEquals("서울 야경 투어", response.trip().title());
        assertNotNull(response.appointment());
        assertEquals(55L, response.appointment().appointmentId());
    }

    @Test
    void previewPayment_throwsInvalidInput_whenSpendingScopeMissing() {
        QrPaymentPreviewRequest request = new QrPaymentPreviewRequest("token-abc", null, null, null);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> qrPaymentService.previewPayment(MEMBER_ID, request)
        );

        assertEquals(me.nawa.common.exception.CommonErrorCode.INVALID_INPUT, exception.getErrorCode());
        verifyNoInteractions(walletMapper);
    }

    @Test
    void previewPayment_throwsPersonalAppointmentNotAllowed_whenPersonalHasAppointmentId() {
        QrPaymentPreviewRequest request =
            new QrPaymentPreviewRequest("token-abc", null, SpendingScope.PERSONAL, 55L);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> qrPaymentService.previewPayment(MEMBER_ID, request)
        );

        assertEquals(WalletErrorCode.QR_PERSONAL_APPOINTMENT_NOT_ALLOWED, exception.getErrorCode());
        verifyNoInteractions(walletMapper);
    }

    @Test
    void previewPayment_throwsSharedAppointmentRequired_whenSharedMissingAppointmentId() {
        QrPaymentPreviewRequest request =
            new QrPaymentPreviewRequest("token-abc", null, SpendingScope.SHARED, null);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> qrPaymentService.previewPayment(MEMBER_ID, request)
        );

        assertEquals(WalletErrorCode.QR_SHARED_APPOINTMENT_REQUIRED, exception.getErrorCode());
        verifyNoInteractions(walletMapper);
    }

    @Test
    void previewPayment_throwsMembershipNotFound_whenSharedAndNoActiveMembership() {
        stubActiveTarget(BigDecimal.valueOf(18500));
        when(qrPaymentCodeMapper.findActiveAppointmentMembership(MEMBER_ID, 55L)).thenReturn(null);

        QrPaymentPreviewRequest request =
            new QrPaymentPreviewRequest("token-abc", null, SpendingScope.SHARED, 55L);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> qrPaymentService.previewPayment(MEMBER_ID, request)
        );

        assertEquals(WalletErrorCode.QR_APPOINTMENT_MEMBERSHIP_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void previewPayment_throwsTripNotLinked_whenMembershipHasNoTrip() {
        stubActiveTarget(BigDecimal.valueOf(18500));
        when(qrPaymentCodeMapper.findActiveAppointmentMembership(MEMBER_ID, 55L))
            .thenReturn(membership(1L, 55L, null, null));

        QrPaymentPreviewRequest request =
            new QrPaymentPreviewRequest("token-abc", null, SpendingScope.SHARED, 55L);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> qrPaymentService.previewPayment(MEMBER_ID, request)
        );

        assertEquals(WalletErrorCode.QR_APPOINTMENT_TRIP_NOT_LINKED, exception.getErrorCode());
    }

    @Test
    void previewPayment_throwsAmountRequired_whenQrAmountNullAndRequestAmountMissing() {
        stubActiveTarget(null);

        QrPaymentPreviewRequest request =
            new QrPaymentPreviewRequest("token-abc", null, SpendingScope.PERSONAL, null);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> qrPaymentService.previewPayment(MEMBER_ID, request)
        );

        assertEquals(WalletErrorCode.QR_PAYMENT_AMOUNT_REQUIRED, exception.getErrorCode());
    }

    @Test
    void previewPayment_usesRequestAmount_whenQrAmountIsNull() {
        stubActiveTarget(null);

        QrPaymentPreviewRequest request =
            new QrPaymentPreviewRequest("token-abc", BigDecimal.valueOf(5000), SpendingScope.PERSONAL, null);

        QrPaymentPreviewResponse response = qrPaymentService.previewPayment(MEMBER_ID, request);

        assertEquals(0, BigDecimal.valueOf(5000).compareTo(response.amount()));
    }

    // ===== executePayment =====

    private QrPaymentExecuteRequest executeRequest(String qrToken, SpendingScope scope, Long appointmentId) {
        return new QrPaymentExecuteRequest(qrToken, null, scope, appointmentId);
    }

    private void stubLockableWallets(String payerStatus, String payeeStatus) {
        when(walletMapper.findByMemberId(MEMBER_ID))
            .thenReturn(wallet(PAYER_WALLET_ID, BigDecimal.valueOf(50000), payerStatus));
        when(walletMapper.findByWalletIdForUpdate(PAYER_WALLET_ID))
            .thenReturn(wallet(PAYER_WALLET_ID, BigDecimal.valueOf(50000), payerStatus));
        when(walletMapper.findByWalletIdForUpdate(PAYEE_WALLET_ID))
            .thenReturn(wallet(PAYEE_WALLET_ID, BigDecimal.valueOf(2500), payeeStatus));
    }

    private void stubLockableQr(QrPaymentStatus status, LocalDateTime expiresAt, BigDecimal amount) {
        QrPaymentCode qr = qrPaymentCode(
            10L, PAYEE_WALLET_ID, null, "token-abc", amount, "야경 투어", status, expiresAt
        );
        when(qrPaymentCodeMapper.findByQrTokenForUpdate("token-abc")).thenReturn(qr);
    }

    @Test
    void executePayment_completesTransfer_whenPersonalAndSufficientBalance() {
        stubLockableWallets("ACTIVE", "ACTIVE");
        stubLockableQr(QrPaymentStatus.ACTIVE, LocalDateTime.now().plusMinutes(5), BigDecimal.valueOf(18500));
        when(transactionNumberGenerator.generate()).thenReturn("TXN-20260812-ABCDEFGH");

        doAnswer(invocation -> {
            WalletTransfer transfer = invocation.getArgument(0);
            transfer.setTransferId(999L);
            return null;
        }).when(walletTransferMapper).insert(any());

        when(qrPaymentCodeMapper.markCompleted(eq(10L), eq(999L), any(), any())).thenReturn(1);

        QrPaymentExecuteResponse response = qrPaymentService.executePayment(
            MEMBER_ID, "idem-1", executeRequest("token-abc", SpendingScope.PERSONAL, null)
        );

        assertEquals(999L, response.transferId());
        assertEquals(10L, response.qrPaymentId());
        assertEquals("COMPLETED", response.status());
        assertEquals(0, BigDecimal.valueOf(18500).compareTo(response.amount()));
        assertEquals(0, BigDecimal.valueOf(31500).compareTo(response.balanceAfter()));

        // normalizeAmount가 DECIMAL(19,4)에 맞춰 scale=4로 정규화하므로, 비교값도 같은 scale로 맞춘다
        // (BigDecimal.equals()는 compareTo()와 달리 scale까지 비교한다).
        verify(walletLedgerMapper).insert(999L, PAYER_WALLET_ID, "DEBIT", new BigDecimal("18500.0000"), new BigDecimal("31500.0000"));
        verify(walletLedgerMapper).insert(999L, PAYEE_WALLET_ID, "CREDIT", new BigDecimal("18500.0000"), new BigDecimal("21000.0000"));
        verify(walletMapper).updateBalance(PAYER_WALLET_ID, new BigDecimal("31500.0000"));
        verify(walletMapper).updateBalance(PAYEE_WALLET_ID, new BigDecimal("21000.0000"));
        verifyNoInteractions(tripExpenseLinkMapper);
    }

    @Test
    void executePayment_linksTripExpense_whenSharedAndMembershipValid() {
        stubLockableWallets("ACTIVE", "ACTIVE");
        stubLockableQr(QrPaymentStatus.ACTIVE, LocalDateTime.now().plusMinutes(5), BigDecimal.valueOf(18500));
        when(transactionNumberGenerator.generate()).thenReturn("TXN-20260812-ABCDEFGH");

        doAnswer(invocation -> {
            WalletTransfer transfer = invocation.getArgument(0);
            transfer.setTransferId(999L);
            return null;
        }).when(walletTransferMapper).insert(any());

        when(qrPaymentCodeMapper.markCompleted(eq(10L), eq(999L), any(), any())).thenReturn(1);
        when(qrPaymentCodeMapper.findActiveAppointmentMembershipForUpdate(MEMBER_ID, 55L))
            .thenReturn(membership(3L, 55L, 77L, "서울 야경 투어"));
        when(walletLedgerMapper.findByTransferIdAndWalletId(999L, PAYER_WALLET_ID))
            .thenReturn(ledgerEntry(BigDecimal.valueOf(31500), "DEBIT"));

        qrPaymentService.executePayment(
            MEMBER_ID, "idem-1", executeRequest("token-abc", SpendingScope.SHARED, 55L)
        );

        verify(tripExpenseLinkMapper).insert(77L, 700L, 3L);
    }

    @Test
    void executePayment_throwsIdempotencyKeyRequired_whenKeyMissing() {
        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> qrPaymentService.executePayment(
                MEMBER_ID, null, executeRequest("token-abc", SpendingScope.PERSONAL, null)
            )
        );

        assertEquals(WalletErrorCode.IDEMPOTENCY_KEY_REQUIRED, exception.getErrorCode());
        verifyNoInteractions(walletTransferMapper);
    }

    @Test
    void executePayment_throwsSharedAppointmentRequired_whenSharedMissingAppointmentId() {
        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> qrPaymentService.executePayment(
                MEMBER_ID, "idem-1", executeRequest("token-abc", SpendingScope.SHARED, null)
            )
        );

        assertEquals(WalletErrorCode.QR_SHARED_APPOINTMENT_REQUIRED, exception.getErrorCode());
        verifyNoInteractions(walletTransferMapper);
    }

    @Test
    void executePayment_throwsWalletNotFound_whenPayerWalletMissing() {
        when(walletTransferMapper.findByIdempotencyKey("idem-1")).thenReturn(null);
        when(walletMapper.findByMemberId(MEMBER_ID)).thenReturn(null);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> qrPaymentService.executePayment(
                MEMBER_ID, "idem-1", executeRequest("token-abc", SpendingScope.PERSONAL, null)
            )
        );

        assertEquals(WalletErrorCode.WALLET_NOT_FOUND, exception.getErrorCode());
        verifyNoInteractions(qrPaymentCodeMapper);
    }

    @Test
    void executePayment_throwsQrPaymentNotFound_whenTokenUnknown() {
        when(walletTransferMapper.findByIdempotencyKey("idem-1")).thenReturn(null);
        when(walletMapper.findByMemberId(MEMBER_ID))
            .thenReturn(wallet(PAYER_WALLET_ID, BigDecimal.valueOf(50000), "ACTIVE"));
        when(qrPaymentCodeMapper.findByQrTokenForUpdate("token-abc")).thenReturn(null);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> qrPaymentService.executePayment(
                MEMBER_ID, "idem-1", executeRequest("token-abc", SpendingScope.PERSONAL, null)
            )
        );

        assertEquals(WalletErrorCode.QR_PAYMENT_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void executePayment_throwsAlreadyCompleted_whenQrCompletedTransferIdSet() {
        when(walletMapper.findByMemberId(MEMBER_ID))
            .thenReturn(wallet(PAYER_WALLET_ID, BigDecimal.valueOf(50000), "ACTIVE"));

        QrPaymentCode qr = qrPaymentCode(
            10L, PAYEE_WALLET_ID, 900L, "token-abc", BigDecimal.valueOf(18500),
            "야경 투어", QrPaymentStatus.COMPLETED, LocalDateTime.now().plusMinutes(5)
        );
        when(qrPaymentCodeMapper.findByQrTokenForUpdate("token-abc")).thenReturn(qr);
        // findByTransferId(900L)는 스텁하지 않는다 — 다른 key로 이미 완료된 QR을
        // 다시 쓰는 상황이라, 조회 결과와 무관하게(여기서는 기본값 null) 아래 검증에서
        // ALREADY_COMPLETED로 거절돼야 한다.

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> qrPaymentService.executePayment(
                MEMBER_ID, "idem-1", executeRequest("token-abc", SpendingScope.PERSONAL, null)
            )
        );

        assertEquals(WalletErrorCode.QR_PAYMENT_ALREADY_COMPLETED, exception.getErrorCode());
    }

    @Test
    void executePayment_returnsOriginalResult_whenSameQrAndSameKeyRetriedAfterQrLockWait() {
        // QR 잠금을 기다리는 동안 동일 QR·동일 key로 다른 요청이 결제를 끝낸 상황.
        // 빠른 경로(findByIdempotencyKey)는 아직 그 커밋을 못 봤지만, QR 잠금을 잡고 나서
        // 읽은 최신 QR 행에는 completed_transfer_id가 채워져 있어야 한다.
        when(walletMapper.findByMemberId(MEMBER_ID))
            .thenReturn(wallet(PAYER_WALLET_ID, BigDecimal.valueOf(50000), "ACTIVE"));

        QrPaymentCode qr = qrPaymentCode(
            10L, PAYEE_WALLET_ID, 999L, "token-abc", BigDecimal.valueOf(18500),
            "야경 투어", QrPaymentStatus.COMPLETED, LocalDateTime.now().plusMinutes(5)
        );
        when(qrPaymentCodeMapper.findByQrTokenForUpdate("token-abc")).thenReturn(qr);

        WalletTransfer existing =
            transfer(999L, "QR_PAYMENT", "COMPLETED", BigDecimal.valueOf(18500), MEMBER_ID, "idem-1");
        when(walletTransferMapper.findByTransferId(999L)).thenReturn(existing);
        when(qrPaymentCodeMapper.findByCompletedTransferId(999L)).thenReturn(qr);
        when(walletLedgerMapper.findByTransferIdAndWalletId(999L, PAYER_WALLET_ID))
            .thenReturn(ledgerEntry(BigDecimal.valueOf(31500), "DEBIT"));

        QrPaymentExecuteResponse response = qrPaymentService.executePayment(
            MEMBER_ID, "idem-1", executeRequest("token-abc", SpendingScope.PERSONAL, null)
        );

        assertEquals(999L, response.transferId());
        verify(walletTransferMapper, never()).insert(any());
    }

    @Test
    void executePayment_throwsExpired_whenActiveButPastDeadline() {
        when(walletMapper.findByMemberId(MEMBER_ID))
            .thenReturn(wallet(PAYER_WALLET_ID, BigDecimal.valueOf(50000), "ACTIVE"));
        stubLockableQr(QrPaymentStatus.ACTIVE, LocalDateTime.now().minusSeconds(1), BigDecimal.valueOf(18500));

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> qrPaymentService.executePayment(
                MEMBER_ID, "idem-1", executeRequest("token-abc", SpendingScope.PERSONAL, null)
            )
        );

        assertEquals(WalletErrorCode.QR_PAYMENT_EXPIRED, exception.getErrorCode());
    }

    @Test
    void executePayment_throwsSelfPaymentNotAllowed_whenPayerIsPayee() {
        when(walletMapper.findByMemberId(MEMBER_ID))
            .thenReturn(wallet(PAYER_WALLET_ID, BigDecimal.valueOf(50000), "ACTIVE"));

        QrPaymentCode qr = qrPaymentCode(
            10L, PAYER_WALLET_ID, null, "token-abc", BigDecimal.valueOf(18500),
            "야경 투어", QrPaymentStatus.ACTIVE, LocalDateTime.now().plusMinutes(5)
        );
        when(qrPaymentCodeMapper.findByQrTokenForUpdate("token-abc")).thenReturn(qr);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> qrPaymentService.executePayment(
                MEMBER_ID, "idem-1", executeRequest("token-abc", SpendingScope.PERSONAL, null)
            )
        );

        assertEquals(WalletErrorCode.QR_SELF_PAYMENT_NOT_ALLOWED, exception.getErrorCode());
    }

    @Test
    void executePayment_throwsWalletNotActive_whenPayerWalletSuspendedAfterLock() {
        stubLockableWallets("SUSPENDED", "ACTIVE");
        stubLockableQr(QrPaymentStatus.ACTIVE, LocalDateTime.now().plusMinutes(5), BigDecimal.valueOf(18500));

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> qrPaymentService.executePayment(
                MEMBER_ID, "idem-1", executeRequest("token-abc", SpendingScope.PERSONAL, null)
            )
        );

        assertEquals(WalletErrorCode.WALLET_NOT_ACTIVE, exception.getErrorCode());
        verifyNoInteractions(walletLedgerMapper);
    }

    @Test
    void executePayment_throwsPayeeWalletNotActive_whenPayeeWalletSuspended() {
        stubLockableWallets("ACTIVE", "SUSPENDED");
        stubLockableQr(QrPaymentStatus.ACTIVE, LocalDateTime.now().plusMinutes(5), BigDecimal.valueOf(18500));

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> qrPaymentService.executePayment(
                MEMBER_ID, "idem-1", executeRequest("token-abc", SpendingScope.PERSONAL, null)
            )
        );

        assertEquals(WalletErrorCode.QR_PAYEE_WALLET_NOT_ACTIVE, exception.getErrorCode());
    }

    @Test
    void executePayment_throwsInsufficientBalance_whenPayerBalanceTooLow() {
        when(walletMapper.findByMemberId(MEMBER_ID))
            .thenReturn(wallet(PAYER_WALLET_ID, BigDecimal.valueOf(1000), "ACTIVE"));
        when(walletMapper.findByWalletIdForUpdate(PAYER_WALLET_ID))
            .thenReturn(wallet(PAYER_WALLET_ID, BigDecimal.valueOf(1000), "ACTIVE"));
        when(walletMapper.findByWalletIdForUpdate(PAYEE_WALLET_ID))
            .thenReturn(wallet(PAYEE_WALLET_ID, BigDecimal.valueOf(2500), "ACTIVE"));
        stubLockableQr(QrPaymentStatus.ACTIVE, LocalDateTime.now().plusMinutes(5), BigDecimal.valueOf(18500));

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> qrPaymentService.executePayment(
                MEMBER_ID, "idem-1", executeRequest("token-abc", SpendingScope.PERSONAL, null)
            )
        );

        assertEquals(WalletErrorCode.QR_PAYMENT_INSUFFICIENT_BALANCE, exception.getErrorCode());
        verify(walletTransferMapper, never()).insert(any());
    }

    @Test
    void executePayment_throwsMembershipNotFound_whenSharedAndNoActiveMembership() {
        stubLockableWallets("ACTIVE", "ACTIVE");
        stubLockableQr(QrPaymentStatus.ACTIVE, LocalDateTime.now().plusMinutes(5), BigDecimal.valueOf(18500));
        when(qrPaymentCodeMapper.findActiveAppointmentMembershipForUpdate(MEMBER_ID, 55L)).thenReturn(null);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> qrPaymentService.executePayment(
                MEMBER_ID, "idem-1", executeRequest("token-abc", SpendingScope.SHARED, 55L)
            )
        );

        assertEquals(WalletErrorCode.QR_APPOINTMENT_MEMBERSHIP_NOT_FOUND, exception.getErrorCode());
        verify(walletTransferMapper, never()).insert(any());
    }

    @Test
    void executePayment_throwsQrPaymentNotActive_whenMarkCompletedAffectsNoRows() {
        stubLockableWallets("ACTIVE", "ACTIVE");
        stubLockableQr(QrPaymentStatus.ACTIVE, LocalDateTime.now().plusMinutes(5), BigDecimal.valueOf(18500));
        when(transactionNumberGenerator.generate()).thenReturn("TXN-20260812-ABCDEFGH");

        doAnswer(invocation -> {
            WalletTransfer transfer = invocation.getArgument(0);
            transfer.setTransferId(999L);
            return null;
        }).when(walletTransferMapper).insert(any());

        // 잠금 사이 다른 요청이 먼저 이 QR을 완료 처리해 0건이 갱신된 상황을 흉내낸다
        when(qrPaymentCodeMapper.markCompleted(eq(10L), eq(999L), any(), any())).thenReturn(0);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> qrPaymentService.executePayment(
                MEMBER_ID, "idem-1", executeRequest("token-abc", SpendingScope.PERSONAL, null)
            )
        );

        assertEquals(WalletErrorCode.QR_PAYMENT_NOT_ACTIVE, exception.getErrorCode());
    }

    // ----- executePayment: idempotency 재요청 -----

    @Test
    void executePayment_returnsOriginalResult_whenIdempotencyKeyAlreadyUsed() {
        WalletTransfer existing =
            transfer(999L, "QR_PAYMENT", "COMPLETED", BigDecimal.valueOf(18500), MEMBER_ID, "idem-1");
        when(walletTransferMapper.findByIdempotencyKey("idem-1")).thenReturn(existing);

        QrPaymentCode qr = qrPaymentCode(
            10L, PAYEE_WALLET_ID, 999L, "token-abc", BigDecimal.valueOf(18500),
            "야경 투어", QrPaymentStatus.COMPLETED, LocalDateTime.now().plusMinutes(5)
        );
        when(qrPaymentCodeMapper.findByCompletedTransferId(999L)).thenReturn(qr);
        when(walletMapper.findByMemberId(MEMBER_ID))
            .thenReturn(wallet(PAYER_WALLET_ID, BigDecimal.valueOf(31500), "ACTIVE"));
        when(walletLedgerMapper.findByTransferIdAndWalletId(999L, PAYER_WALLET_ID))
            .thenReturn(ledgerEntry(BigDecimal.valueOf(31500), "DEBIT"));

        QrPaymentExecuteResponse response = qrPaymentService.executePayment(
            MEMBER_ID, "idem-1", executeRequest("token-abc", SpendingScope.PERSONAL, null)
        );

        assertEquals(999L, response.transferId());
        assertEquals(0, BigDecimal.valueOf(18500).compareTo(response.amount()));
        assertEquals(0, BigDecimal.valueOf(31500).compareTo(response.balanceAfter()));
        verify(walletTransferMapper, never()).insert(any());
        verify(walletMapper, never()).updateBalance(anyLong(), any());
    }

    @Test
    void executePayment_throwsIdempotencyKeyConflict_whenExistingTransferIsNotQrPayment() {
        WalletTransfer existing =
            transfer(999L, "TOPUP", "COMPLETED", BigDecimal.valueOf(18500), MEMBER_ID, "idem-1");
        when(walletTransferMapper.findByIdempotencyKey("idem-1")).thenReturn(existing);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> qrPaymentService.executePayment(
                MEMBER_ID, "idem-1", executeRequest("token-abc", SpendingScope.PERSONAL, null)
            )
        );

        assertEquals(WalletErrorCode.IDEMPOTENCY_KEY_CONFLICT, exception.getErrorCode());
    }

    @Test
    void executePayment_throwsIdempotencyKeyConflict_whenExistingTransferBelongsToOtherMember() {
        WalletTransfer existing =
            transfer(999L, "QR_PAYMENT", "COMPLETED", BigDecimal.valueOf(18500), 2L, "idem-1");
        when(walletTransferMapper.findByIdempotencyKey("idem-1")).thenReturn(existing);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> qrPaymentService.executePayment(
                MEMBER_ID, "idem-1", executeRequest("token-abc", SpendingScope.PERSONAL, null)
            )
        );

        assertEquals(WalletErrorCode.IDEMPOTENCY_KEY_CONFLICT, exception.getErrorCode());
    }

    @Test
    void executePayment_throwsIdempotencyKeyConflict_whenQrTokenDoesNotMatch() {
        WalletTransfer existing =
            transfer(999L, "QR_PAYMENT", "COMPLETED", BigDecimal.valueOf(18500), MEMBER_ID, "idem-1");
        when(walletTransferMapper.findByIdempotencyKey("idem-1")).thenReturn(existing);

        QrPaymentCode qr = qrPaymentCode(
            10L, PAYEE_WALLET_ID, 999L, "token-different", BigDecimal.valueOf(18500),
            "야경 투어", QrPaymentStatus.COMPLETED, LocalDateTime.now().plusMinutes(5)
        );
        when(qrPaymentCodeMapper.findByCompletedTransferId(999L)).thenReturn(qr);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> qrPaymentService.executePayment(
                MEMBER_ID, "idem-1", executeRequest("token-abc", SpendingScope.PERSONAL, null)
            )
        );

        assertEquals(WalletErrorCode.IDEMPOTENCY_KEY_CONFLICT, exception.getErrorCode());
    }

    @Test
    void executePayment_throwsIdempotencyKeyConflict_whenAmountDoesNotMatch() {
        WalletTransfer existing =
            transfer(999L, "QR_PAYMENT", "COMPLETED", BigDecimal.valueOf(18500), MEMBER_ID, "idem-1");
        when(walletTransferMapper.findByIdempotencyKey("idem-1")).thenReturn(existing);

        // amount-input QR — 최초 결제 때와 이번 요청의 amount가 다르면 같은 키를 재사용해선 안 된다
        QrPaymentCode qr = qrPaymentCode(
            10L, PAYEE_WALLET_ID, 999L, "token-abc", null,
            "야경 투어", QrPaymentStatus.COMPLETED, LocalDateTime.now().plusMinutes(5)
        );
        when(qrPaymentCodeMapper.findByCompletedTransferId(999L)).thenReturn(qr);

        QrPaymentExecuteRequest differentAmountRequest =
            new QrPaymentExecuteRequest("token-abc", BigDecimal.valueOf(9999), SpendingScope.PERSONAL, null);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> qrPaymentService.executePayment(MEMBER_ID, "idem-1", differentAmountRequest)
        );

        assertEquals(WalletErrorCode.IDEMPOTENCY_KEY_CONFLICT, exception.getErrorCode());
    }

    @Test
    void executePayment_throwsIdempotencyKeyConflict_whenSpendingScopeDoesNotMatch() {
        WalletTransfer existing =
            transfer(999L, "QR_PAYMENT", "COMPLETED", BigDecimal.valueOf(18500), MEMBER_ID, "idem-1");
        when(walletTransferMapper.findByIdempotencyKey("idem-1")).thenReturn(existing);

        QrPaymentCode qr = qrPaymentCode(
            10L, PAYEE_WALLET_ID, 999L, "token-abc", BigDecimal.valueOf(18500),
            "야경 투어", QrPaymentStatus.COMPLETED, LocalDateTime.now().plusMinutes(5)
        );
        when(qrPaymentCodeMapper.findByCompletedTransferId(999L)).thenReturn(qr);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> qrPaymentService.executePayment(
                MEMBER_ID, "idem-1", executeRequest("token-abc", SpendingScope.SHARED, 55L)
            )
        );

        assertEquals(WalletErrorCode.IDEMPOTENCY_KEY_CONFLICT, exception.getErrorCode());
    }

    @Test
    void executePayment_returnsOriginalResult_whenTransferInsertRacesOnIdempotencyKey() {
        WalletTransfer existing =
            transfer(999L, "QR_PAYMENT", "COMPLETED", BigDecimal.valueOf(18500), MEMBER_ID, "idem-1");
        // 빠른 경로(1번째 호출)는 아직 아무것도 못 봤고, insert가 유니크 제약 충돌로 실패한
        // 뒤(catch 블록, 2번째 호출)에야 동시 트랜잭션이 커밋한 결과를 본다.
        when(walletTransferMapper.findByIdempotencyKey("idem-1"))
            .thenReturn(null, existing);
        stubLockableWallets("ACTIVE", "ACTIVE");
        stubLockableQr(QrPaymentStatus.ACTIVE, LocalDateTime.now().plusMinutes(5), BigDecimal.valueOf(18500));
        when(transactionNumberGenerator.generate()).thenReturn("TXN-20260812-ABCDEFGH");
        doThrow(new org.springframework.dao.DuplicateKeyException("duplicate"))
            .when(walletTransferMapper).insert(any());

        QrPaymentCode completedQr = qrPaymentCode(
            10L, PAYEE_WALLET_ID, 999L, "token-abc", BigDecimal.valueOf(18500),
            "야경 투어", QrPaymentStatus.COMPLETED, LocalDateTime.now().plusMinutes(5)
        );
        when(qrPaymentCodeMapper.findByCompletedTransferId(999L)).thenReturn(completedQr);
        when(walletLedgerMapper.findByTransferIdAndWalletId(999L, PAYER_WALLET_ID))
            .thenReturn(ledgerEntry(BigDecimal.valueOf(31500), "DEBIT"));

        QrPaymentExecuteResponse response = qrPaymentService.executePayment(
            MEMBER_ID, "idem-1", executeRequest("token-abc", SpendingScope.PERSONAL, null)
        );

        assertEquals(999L, response.transferId());
        verify(walletTransferMapper).insert(any());
    }

    @Test
    void executePayment_throwsIdempotencyKeyConflict_whenSharedAppointmentDoesNotMatch() {
        WalletTransfer existing =
            transfer(999L, "QR_PAYMENT", "COMPLETED", BigDecimal.valueOf(18500), MEMBER_ID, "idem-1");
        existing.setSpendingType("SHARED");
        when(walletTransferMapper.findByIdempotencyKey("idem-1")).thenReturn(existing);

        QrPaymentCode qr = qrPaymentCode(
            10L, PAYEE_WALLET_ID, 999L, "token-abc", BigDecimal.valueOf(18500),
            "야경 투어", QrPaymentStatus.COMPLETED, LocalDateTime.now().plusMinutes(5)
        );
        when(qrPaymentCodeMapper.findByCompletedTransferId(999L)).thenReturn(qr);
        when(walletMapper.findByMemberId(MEMBER_ID))
            .thenReturn(wallet(PAYER_WALLET_ID, BigDecimal.valueOf(31500), "ACTIVE"));
        when(walletLedgerMapper.findByTransferIdAndWalletId(999L, PAYER_WALLET_ID))
            .thenReturn(ledgerEntry(BigDecimal.valueOf(31500), "DEBIT"));
        when(tripExpenseLinkMapper.findAppointmentIdByLedgerEntryId(700L)).thenReturn(66L);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> qrPaymentService.executePayment(
                MEMBER_ID, "idem-1", executeRequest("token-abc", SpendingScope.SHARED, 55L)
            )
        );

        assertEquals(WalletErrorCode.IDEMPOTENCY_KEY_CONFLICT, exception.getErrorCode());
    }

    // ===== getPaymentStatus =====

    @Test
    void getPaymentStatus_returnsStatus_whenPayerOwnsDebitEntry() {
        when(walletMapper.findByMemberId(MEMBER_ID))
            .thenReturn(wallet(PAYER_WALLET_ID, BigDecimal.valueOf(31500), "ACTIVE"));

        WalletTransfer transfer =
            transfer(999L, "QR_PAYMENT", "COMPLETED", BigDecimal.valueOf(18500), MEMBER_ID, "idem-1");
        when(walletTransferMapper.findByTransferId(999L)).thenReturn(transfer);
        when(walletLedgerMapper.findByTransferIdAndWalletId(999L, PAYER_WALLET_ID))
            .thenReturn(ledgerEntry(BigDecimal.valueOf(31500), "DEBIT"));

        QrPaymentStatusResponse response = qrPaymentService.getPaymentStatus(MEMBER_ID, 999L);

        assertEquals(999L, response.transferId());
        assertEquals("COMPLETED", response.status());
        assertEquals(0, BigDecimal.valueOf(18500).compareTo(response.amount()));
        assertEquals(0, BigDecimal.valueOf(31500).compareTo(response.balanceAfter()));
        assertEquals("KRW", response.currencyCode());
    }

    @Test
    void getPaymentStatus_throwsInvalidInput_whenTransferIdNotPositive() {
        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> qrPaymentService.getPaymentStatus(MEMBER_ID, 0L)
        );

        assertEquals(me.nawa.common.exception.CommonErrorCode.INVALID_INPUT, exception.getErrorCode());
        verifyNoInteractions(walletMapper);
    }

    @Test
    void getPaymentStatus_throwsWalletNotFound_whenNoWallet() {
        when(walletMapper.findByMemberId(MEMBER_ID)).thenReturn(null);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> qrPaymentService.getPaymentStatus(MEMBER_ID, 999L)
        );

        assertEquals(WalletErrorCode.WALLET_NOT_FOUND, exception.getErrorCode());
        verifyNoInteractions(walletTransferMapper);
    }

    @Test
    void getPaymentStatus_throwsTransactionNotFound_whenTransferMissing() {
        when(walletMapper.findByMemberId(MEMBER_ID))
            .thenReturn(wallet(PAYER_WALLET_ID, BigDecimal.valueOf(31500), "ACTIVE"));
        when(walletTransferMapper.findByTransferId(999L)).thenReturn(null);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> qrPaymentService.getPaymentStatus(MEMBER_ID, 999L)
        );

        assertEquals(WalletErrorCode.TRANSACTION_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void getPaymentStatus_throwsTransactionNotFound_whenTransferIsNotQrPayment() {
        when(walletMapper.findByMemberId(MEMBER_ID))
            .thenReturn(wallet(PAYER_WALLET_ID, BigDecimal.valueOf(31500), "ACTIVE"));

        WalletTransfer topupTransfer =
            transfer(999L, "TOPUP", "COMPLETED", BigDecimal.valueOf(18500), MEMBER_ID, "idem-1");
        when(walletTransferMapper.findByTransferId(999L)).thenReturn(topupTransfer);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> qrPaymentService.getPaymentStatus(MEMBER_ID, 999L)
        );

        assertEquals(WalletErrorCode.TRANSACTION_NOT_FOUND, exception.getErrorCode());
        verifyNoInteractions(walletLedgerMapper);
    }

    @Test
    void getPaymentStatus_throwsForbidden_whenCallerHasNoDebitEntry() {
        when(walletMapper.findByMemberId(MEMBER_ID))
            .thenReturn(wallet(PAYER_WALLET_ID, BigDecimal.valueOf(31500), "ACTIVE"));

        WalletTransfer transfer =
            transfer(999L, "QR_PAYMENT", "COMPLETED", BigDecimal.valueOf(18500), 2L, "idem-1");
        when(walletTransferMapper.findByTransferId(999L)).thenReturn(transfer);
        when(walletLedgerMapper.findByTransferIdAndWalletId(999L, PAYER_WALLET_ID)).thenReturn(null);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> qrPaymentService.getPaymentStatus(MEMBER_ID, 999L)
        );

        assertEquals(WalletErrorCode.TRANSACTION_FORBIDDEN, exception.getErrorCode());
    }

    @Test
    void getPaymentStatus_throwsForbidden_whenCallerIsPayeeNotPayer() {
        when(walletMapper.findByMemberId(MEMBER_ID))
            .thenReturn(wallet(PAYEE_WALLET_ID, BigDecimal.valueOf(21000), "ACTIVE"));

        WalletTransfer transfer =
            transfer(999L, "QR_PAYMENT", "COMPLETED", BigDecimal.valueOf(18500), 2L, "idem-1");
        when(walletTransferMapper.findByTransferId(999L)).thenReturn(transfer);
        when(walletLedgerMapper.findByTransferIdAndWalletId(999L, PAYEE_WALLET_ID))
            .thenReturn(ledgerEntry(BigDecimal.valueOf(21000), "CREDIT"));

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> qrPaymentService.getPaymentStatus(MEMBER_ID, 999L)
        );

        assertEquals(WalletErrorCode.TRANSACTION_FORBIDDEN, exception.getErrorCode());
    }
}
