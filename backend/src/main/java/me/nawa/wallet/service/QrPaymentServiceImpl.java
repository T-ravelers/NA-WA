package me.nawa.wallet.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import me.nawa.common.exception.BusinessException;
import me.nawa.common.exception.CommonErrorCode;
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
import me.nawa.wallet.dto.response.QrPaymentPreviewResponse.AppointmentInfo;
import me.nawa.wallet.dto.response.QrPaymentPreviewResponse.TripInfo;
import me.nawa.wallet.dto.response.QrPaymentResolveResponse;
import me.nawa.wallet.exception.WalletErrorCode;
import me.nawa.wallet.mapper.QrPaymentCodeMapper;
import me.nawa.wallet.mapper.TripExpenseLinkMapper;
import me.nawa.wallet.mapper.WalletLedgerMapper;
import me.nawa.wallet.mapper.WalletMapper;
import me.nawa.wallet.mapper.WalletTransferMapper;
import me.nawa.wallet.util.QrTokenGenerator;
import me.nawa.wallet.util.TransactionNumberGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// QR 생성/검증/결제 미리보기/결제 실행 4개를 담당할 서비스 (아직 미구현).
// Notion "구현 계획" 이슈 4에 설계가 정리되어 있음 — createPaymentQr/resolveQr/previewPayment/executePayment 예정.
@Service
@RequiredArgsConstructor
public class QrPaymentServiceImpl implements QrPaymentService {

    private static final long QR_EXPIRATION_MINUTES = 5L;
    private static final int MAX_MEMO_LENGTH = 255;

    private final WalletMapper walletMapper;
    private final QrPaymentCodeMapper qrPaymentCodeMapper;
    private final QrTokenGenerator qrTokenGenerator;
    private final WalletTransferMapper walletTransferMapper;
    private final WalletLedgerMapper walletLedgerMapper;
    private final TransactionNumberGenerator transactionNumberGenerator;
    private final TripExpenseLinkMapper tripExpenseLinkMapper;

    @Override
    @Transactional
    public QrPaymentCreateResponse createPaymentQr(Long memberId, QrPaymentCreateRequest request) {
        // 1. 요청 검증
        validateRequest(request);

        // 2. 본인 지갑인지 확인
        Wallet wallet = walletMapper.findByMemberId(memberId);

        if(wallet == null){
            throw new BusinessException(WalletErrorCode.WALLET_NOT_FOUND);
        }

        if(!"ACTIVE".equals(wallet.getWalletStatus())){
            throw new BusinessException(WalletErrorCode.WALLET_NOT_ACTIVE);
        }

        String memo = normalizeMemo(request.memo());
        String qrToken = qrTokenGenerator.generate();
        LocalDateTime expiresAt = LocalDateTime.now()
            .plusMinutes(QR_EXPIRATION_MINUTES);

        QrPaymentCode qrPaymentCode = new QrPaymentCode(
            null,
            wallet.getWalletId(),
            null,
            qrToken,
            request.amount(),
            memo,
            QrPaymentStatus.ACTIVE,
            expiresAt,
            null,
            null,
            null
        );

        qrPaymentCodeMapper.insert(qrPaymentCode);

        return new QrPaymentCreateResponse(
            qrPaymentCode.getQrPaymentCodeId(),
            qrPaymentCode.getQrToken(),
            qrPaymentCode.getAmount(),
            qrPaymentCode.getMemo(),
            qrPaymentCode.getPaymentStatus().name(),
            wallet.getCurrencyCode(),
            qrPaymentCode.getExpiresAt()
        );
    }

    @Override
    @Transactional
    public QrPaymentResolveResponse resolvePaymentQr(Long memberId, QrPaymentResolveRequest request) {
        validateQrToken(request);

        //결제하려는 사람의 지갑: 자기 자신 결제를 막는데 사용
        Wallet payerWallet = walletMapper.findByMemberId(memberId);
        if(payerWallet == null){
            throw new BusinessException(WalletErrorCode.WALLET_NOT_FOUND);
        }

        // QR 검증, 검증 객체 return
        QrPaymentResolveTarget target =
            findAndValidateActiveQr(payerWallet, request.qrToken());

        return new QrPaymentResolveResponse(
            target.getQrPaymentCodeId(),
            target.getPayeeName(),
            target.getAmount(),
            target.getAmount() == null,
            target.getMemo(),
            target.getPaymentStatus().name(),
            target.getCurrencyCode(),
            target.getExpiresAt()
        );
    }

    @Override
    @Transactional
    public QrPaymentPreviewResponse previewPayment(Long memberId, QrPaymentPreviewRequest request) {
        // 1. QR 토큰, 소비 범위, 약속 id의 형태를 먼저 검증
        validatePreviewRequest(request);

        // 2. 결제자(로그인 사용자)의 지갑 조회
        Wallet payerWallet = walletMapper.findByMemberId(memberId);

        if(payerWallet == null){
            throw new BusinessException(WalletErrorCode.WALLET_NOT_FOUND);
        }

        // 3. QR 존재/만료/완료/취소/자기 결재/수취 지갑 상태를 검증
        QrPaymentResolveTarget qrPayment =
            findAndValidateActiveQr(payerWallet, request.qrToken());

        // 4. 실제 결제 금액 확정
        // - 고정 QR: QR DB의 amount 사용
        // - 금액 입력 QR: 요청 amount 사용
        BigDecimal paymentAmount = resolvePaymentAmount(
            qrPayment.getAmount(),
            request.amount()
        );

        // 5. 공동 소비일 때만 로그인 사용자의 약속 활성 멤버십을 확인
        QrPaymentPreviewResponse.TripInfo trip = null;
        QrPaymentPreviewResponse.AppointmentInfo appointment = null;

        if(request.spendingScope() == SpendingScope.SHARED){
            QrPaymentAppointmentMembership membership =
                qrPaymentCodeMapper.findActiveAppointmentMembership(
                    memberId,
                    request.appointmentId()
                );

            // 공동 소비인데 membership이 null이면
            if(membership == null){
                throw new BusinessException(
                    WalletErrorCode.QR_APPOINTMENT_MEMBERSHIP_NOT_FOUND
                );
            }

            // 공동 소비는 나중에 trip_expense_links에 연결해야 하므로
            // 해당 약속 멤버십에 여행이 연결되어 있어야 함
            if(membership.getTripId() == null
            || membership.getTripTitle() == null){
                throw new BusinessException(
                    WalletErrorCode.QR_APPOINTMENT_TRIP_NOT_LINKED
                );
            }

            trip = new TripInfo(
                membership.getTripId(),
                membership.getTripTitle()
            );

            appointment = new AppointmentInfo(
                membership.getAppointmentId(),
                membership.getAppointmentName()
            );
        }

        // 6. 미리보기용 예상 잔액 계산
        BigDecimal currentBalance = payerWallet.getAvailableBalance();
        BigDecimal balanceAfter= currentBalance.subtract(paymentAmount);

        // 잔액 부족은 예외가 아니라 결제 불가 상태로 응답
        boolean canPay = balanceAfter.compareTo(BigDecimal.ZERO) >= 0;

        // 7. 화면에 표시할 미리보기 응답 반환
        return new QrPaymentPreviewResponse(
            qrPayment.getQrPaymentCodeId(),
            qrPayment.getPayeeName(),
            paymentAmount,
            currentBalance,
            balanceAfter,
            qrPayment.getCurrencyCode(),
            request.spendingScope(),
            trip,
            appointment,
            canPay,
            qrPayment.getExpiresAt()
        );
    }

    @Override
    @Transactional
    public QrPaymentExecuteResponse executePayment(
        Long memberId,
        String idempotencyKey,
        QrPaymentExecuteRequest request
    ) {
        validateExecuteRequest(idempotencyKey, request);

        // 1. 이미 완료된 동일 요청이면 기존 결과 반환
        WalletTransfer existing =
            walletTransferMapper.findByIdempotencyKey(idempotencyKey);

        if (existing != null) {
            return getIdempotentResult(memberId, request, existing);
        }

        // 결제자 지갑 ID를 확인하기 위한 일반 조회
        Wallet payerSnapshot = walletMapper.findByMemberId(memberId);
        if (payerSnapshot == null) {
            throw new BusinessException(WalletErrorCode.WALLET_NOT_FOUND);
        }

        // 2. QR을 먼저 잠근다.
        QrPaymentCode qrPayment =
            qrPaymentCodeMapper.findByQrTokenForUpdate(request.qrToken().trim());

        if (qrPayment == null) {
            throw new BusinessException(WalletErrorCode.QR_PAYMENT_NOT_FOUND);
        }

        // QR을 기다리는 동안 동일 Idempotency-Key 요청이 끝났을 수 있으므로 재확인
        existing = walletTransferMapper.findByIdempotencyKey(idempotencyKey);
        if (existing != null) {
            return getIdempotentResult(memberId, request, existing);
        }

        LocalDateTime now = LocalDateTime.now();

        validateExecutableQr(qrPayment, payerSnapshot, now);

        BigDecimal paymentAmount = resolvePaymentAmount(
            qrPayment.getAmount(),
            request.amount()
        );

        // 3. 두 지갑을 항상 walletId 오름차순으로 잠근다.
        // 반대 방향 동시 결제에서 데드락이 나는 것을 줄인다.
        Long payerWalletId = payerSnapshot.getWalletId();
        Long payeeWalletId = qrPayment.getPayeeWalletId();

        Long firstWalletId = payerWalletId < payeeWalletId
            ? payerWalletId
            : payeeWalletId;

        Long secondWalletId = payerWalletId < payeeWalletId
            ? payeeWalletId
            : payerWalletId;

        Wallet firstLockedWallet =
            walletMapper.findByWalletIdForUpdate(firstWalletId);
        Wallet secondLockedWallet =
            walletMapper.findByWalletIdForUpdate(secondWalletId);

        if (firstLockedWallet == null || secondLockedWallet == null) {
            throw new BusinessException(WalletErrorCode.WALLET_NOT_FOUND);
        }

        Wallet payerWallet = payerWalletId.equals(firstLockedWallet.getWalletId())
            ? firstLockedWallet
            : secondLockedWallet;

        Wallet payeeWallet = payeeWalletId.equals(firstLockedWallet.getWalletId())
            ? firstLockedWallet
            : secondLockedWallet;

        if (!"ACTIVE".equals(payerWallet.getWalletStatus())) {
            throw new BusinessException(WalletErrorCode.WALLET_NOT_ACTIVE);
        }

        if (!"ACTIVE".equals(payeeWallet.getWalletStatus())) {
            throw new BusinessException(WalletErrorCode.QR_PAYEE_WALLET_NOT_ACTIVE);
        }

        if (!payerWallet.getCurrencyCode().equals(payeeWallet.getCurrencyCode())) {
            throw new BusinessException(WalletErrorCode.UNSUPPORTED_CURRENCY);
        }

        BigDecimal payerBalanceAfter =
            payerWallet.getAvailableBalance().subtract(paymentAmount);

        if (payerBalanceAfter.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(
                WalletErrorCode.QR_PAYMENT_INSUFFICIENT_BALANCE
            );
        }

        BigDecimal payeeBalanceAfter =
            payeeWallet.getAvailableBalance().add(paymentAmount);

        // 4. 공동 소비라면 실행 시점에도 활성 멤버십을 잠금 조회
        QrPaymentAppointmentMembership membership = null;

        if (request.spendingScope() == SpendingScope.SHARED) {
            membership =
                qrPaymentCodeMapper.findActiveAppointmentMembershipForUpdate(
                    memberId,
                    request.appointmentId()
                );

            if (membership == null) {
                throw new BusinessException(
                    WalletErrorCode.QR_APPOINTMENT_MEMBERSHIP_NOT_FOUND
                );
            }

            if (membership.getTripId() == null) {
                throw new BusinessException(
                    WalletErrorCode.QR_APPOINTMENT_TRIP_NOT_LINKED
                );
            }
        }

        // 5. 이체 생성
        WalletTransfer transfer = new WalletTransfer(
            null,
            transactionNumberGenerator.generate(),
            "QR_PAYMENT",
            "COMPLETED",
            paymentAmount,
            qrPayment.getMemo(),
            null,
            now,
            now,
            memberId,
            idempotencyKey
        );

        walletTransferMapper.insert(transfer);

        // 6. 양쪽 원장 생성
        walletLedgerMapper.insert(
            transfer.getTransferId(),
            payerWallet.getWalletId(),
            "DEBIT",
            paymentAmount,
            payerBalanceAfter
        );

        walletLedgerMapper.insert(
            transfer.getTransferId(),
            payeeWallet.getWalletId(),
            "CREDIT",
            paymentAmount,
            payeeBalanceAfter
        );

        // 7. 양쪽 지갑 잔액 반영
        walletMapper.updateBalance(
            payerWallet.getWalletId(),
            payerBalanceAfter
        );

        walletMapper.updateBalance(
            payeeWallet.getWalletId(),
            payeeBalanceAfter
        );

        // 8. QR 소진. 결과가 1이 아니면 상태가 달라졌다는 뜻이므로 롤백
        int updated = qrPaymentCodeMapper.markCompleted(
            qrPayment.getQrPaymentCodeId(),
            transfer.getTransferId(),
            now,
            now
        );

        if (updated != 1) {
            throw new BusinessException(WalletErrorCode.QR_PAYMENT_NOT_ACTIVE);
        }

        // 9. 공동 소비는 결제자 DEBIT 원장만 여행 비용으로 연결
        if (membership != null) {
            WalletLedgerEntry debitEntry =
                walletLedgerMapper.findByTransferIdAndWalletId(
                    transfer.getTransferId(),
                    payerWallet.getWalletId()
                );

            tripExpenseLinkMapper.insert(
                membership.getTripId(),
                debitEntry.getLedgerEntryId(),
                membership.getAppointmentMemberId()
            );
        }

        return new QrPaymentExecuteResponse(
            transfer.getTransferId(),
            qrPayment.getQrPaymentCodeId(),
            "COMPLETED",
            paymentAmount,
            payerBalanceAfter,
            payerWallet.getCurrencyCode(),
            now
        );
    }

    private void validateRequest(QrPaymentCreateRequest request){
        if(request == null){
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }

        // null은 결제자가 금액을 입력하는 QR이므로 허용
        if(request.amount() != null
            && request.amount().compareTo(BigDecimal.ZERO) <= 0){
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }

        if(request.memo() != null
            && request.memo().trim().length() > MAX_MEMO_LENGTH){
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
    }

    private String normalizeMemo(String memo){
        if(memo == null){
            return null;
        }

        String normalizedMemo = memo.trim();
        return normalizedMemo.isEmpty() ? null : normalizedMemo;
    }

    private void validateQrToken(QrPaymentResolveRequest request) {
        if (request == null
            || request.qrToken() == null
            || request.qrToken().isBlank()
            || request.qrToken().trim().length() > 255) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
    }

    // QR에 저장된 금액과 욘청 금액을 분기하는 함수
    private BigDecimal resolvePaymentAmount(
        // db에 저장되어 있었던 금액 정보
        BigDecimal qrAmount,
        // 이번 요청의 금액 정보
        BigDecimal requestAmount
    ){
        // 고정 금액: QR: 프론트가 보낸 amount는 신뢰하지 않고 DB 값을 사용
        if(qrAmount != null){
            return qrAmount;
        }

        //금액 입력 QR: 결재자가 0보다 큰 금액을 반드시 입력
        if(requestAmount == null
        || requestAmount.compareTo(BigDecimal.ZERO) <= 0){
            throw new BusinessException(
                WalletErrorCode.QR_PAYMENT_AMOUNT_REQUIRED
            );
        }

        return requestAmount;
    }

    // 요청 형식 검증
    private void validatePreviewRequest(QrPaymentPreviewRequest request) {
        if (request == null
            || request.qrToken() == null
            || request.qrToken().isBlank()
            || request.spendingScope() == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }

        // 개인 소비에는 약속을 연결하면 안 됨
        if (request.spendingScope() == SpendingScope.PERSONAL
            && request.appointmentId() != null) {
            throw new BusinessException(
                WalletErrorCode.QR_PERSONAL_APPOINTMENT_NOT_ALLOWED
            );
        }

        // 공동 소비에는 약속이 필수
        if (request.spendingScope() == SpendingScope.SHARED
            && request.appointmentId() == null) {
            throw new BusinessException(WalletErrorCode.QR_SHARED_APPOINTMENT_REQUIRED);
        }
    }

    // resolve, preview가 사용할 QR 검증 함수
    private QrPaymentResolveTarget findAndValidateActiveQr(
        Wallet payerWallet,
        String qrToken
    ){
        //qr_token으로 검색
        QrPaymentResolveTarget target =
            qrPaymentCodeMapper.findResolveTargetByToken(qrToken.trim());

        //1. 생성되었었던 qr인가
        if(target == null){
            throw new BusinessException(WalletErrorCode.QR_PAYMENT_NOT_FOUND);
        }

        // 2. 완료된 QR은 만료 여부보다 먼저 안내
        if(target.getCompletedTransferId() != null
            || target.getPaymentStatus() == QrPaymentStatus.COMPLETED) {
            throw new BusinessException(WalletErrorCode.QR_PAYMENT_ALREADY_COMPLETED);
        }

        // 3. 만료된 QR인지
        if(target.getPaymentStatus() == QrPaymentStatus.EXPIRED){
            throw new BusinessException(WalletErrorCode.QR_PAYMENT_EXPIRED);
        }

        // 4. 비활성된 QR인지
        if(target.getPaymentStatus() != QrPaymentStatus.ACTIVE){
            throw new BusinessException(WalletErrorCode.QR_PAYMENT_NOT_ACTIVE);
        }

        LocalDateTime now = LocalDateTime.now();

        // 5. ACTIVE로 남아 있어도 시간상 만료되었다면 EXPIRED로 변경
        if(target.getExpiresAt() == null || !now.isBefore(target.getExpiresAt())){
            qrPaymentCodeMapper.markExpiredIfActive(
                target.getQrPaymentCodeId(),
                now
            );

            throw new BusinessException(WalletErrorCode.QR_PAYMENT_EXPIRED);
        }

        // 6. 결제자 자신의 지갑이 유효하지 않을 때
        if(!"ACTIVE".equals(payerWallet.getWalletStatus())){
            throw new BusinessException(WalletErrorCode.WALLET_NOT_ACTIVE);
        }

        // 7. 자기 자신에 대한 결제일 때
        if(payerWallet.getWalletId().equals(target.getPayeeWalletId())){
            throw new BusinessException(WalletErrorCode.QR_SELF_PAYMENT_NOT_ALLOWED);
        }

        // 8. 결제를 받는 사람의 지갑이 유효하지 않을 때
        if(!"ACTIVE".equals(target.getPayeeWalletStatus())){
            throw new BusinessException(WalletErrorCode.QR_PAYEE_WALLET_NOT_ACTIVE);
        }

        return target;
    }

    // 멱등성 키 검증
    private void validateExecuteRequest(
        String idempotencyKey,
        QrPaymentExecuteRequest request
    ){
        if (idempotencyKey == null
            || idempotencyKey.isBlank()
            || idempotencyKey.length() > 100) {
            throw new BusinessException(
                WalletErrorCode.IDEMPOTENCY_KEY_REQUIRED
            );
        }

        // preview와 같은 소비 범위/약속 검증
        validatePreviewRequest(
            new QrPaymentPreviewRequest(
                request.qrToken(),
                request.amount(),
                request.spendingScope(),
                request.appointmentId()
            )
        );
    }

    // QR 잠금 후 QR 상태를 검증하는 함수
    private void validateExecutableQr(
        QrPaymentCode qrPayment,
        Wallet payerWallet,
        LocalDateTime now
    ) {
        // 1. 해당 qr 결제 요청이 유효한지
        if (qrPayment.getCompletedTransferId() != null
            || qrPayment.getPaymentStatus() == QrPaymentStatus.COMPLETED) {
            throw new BusinessException(
                WalletErrorCode.QR_PAYMENT_ALREADY_COMPLETED
            );
        }

        if (qrPayment.getPaymentStatus() != QrPaymentStatus.ACTIVE) {
            throw new BusinessException(WalletErrorCode.QR_PAYMENT_NOT_ACTIVE);
        }

        if (!now.isBefore(qrPayment.getExpiresAt())) {
            qrPaymentCodeMapper.markExpiredIfActive(
                qrPayment.getQrPaymentCodeId(),
                now
            );

            throw new BusinessException(WalletErrorCode.QR_PAYMENT_EXPIRED);
        }

        // 2. 셀프 결제를 막기 위한 조건
        if (payerWallet.getWalletId().equals(qrPayment.getPayeeWalletId())) {
            throw new BusinessException(
                WalletErrorCode.QR_SELF_PAYMENT_NOT_ALLOWED
            );
        }
    }

    // 같은 idempotecny Key로 결제 요청이 다시 들어왔을 때,
    // 새 결제를 만들지 않고 최초 결제 결과를 그대로 돌려줌
    private QrPaymentExecuteResponse getIdempotentResult(
        Long memberId,
        QrPaymentExecuteRequest request,
        WalletTransfer transfer
    )
    {
         // 1. 해당 키가 현재 사용자의 "완료된 QR 결제"에 사용된 키인지 확인
        if (!"QR_PAYMENT".equals(transfer.getTransferType())
            || !memberId.equals(transfer.getInitiatorMemberId())
            || !"COMPLETED".equals(transfer.getTransferStatus())) {
            throw new BusinessException(WalletErrorCode.IDEMPOTENCY_KEY_CONFLICT);
        }

        // 2. 기존 거래와 연결된 QR을 조회
        QrPaymentCode qrPayment =
            qrPaymentCodeMapper.findByCompletedTransferId(
                transfer.getTransferId()
            );

        // 3. 기존 거래의 QR과 이번 요청의 QR token이 같은지 확인
        // 같은 키를 다른 QR 결제에 재사용하는 실수를 막는다
        if(qrPayment == null
            || !qrPayment.getQrToken().equals(request.qrToken().trim())){
            throw  new BusinessException(WalletErrorCode.IDEMPOTENCY_KEY_CONFLICT);
        }

        // 4. 이번 요청의 최종 결제 금액을 다시 계산
        // 고정 금액 QR은 DB의 QR 금액을 사용하고,
        // 금액 입력 QR은 이번 요청의 amount를 사용한다
        BigDecimal requestedAmount = resolvePaymentAmount(
            qrPayment.getAmount(),
            request.amount()
        );

        // 5. 최초 결제 금액과 재요청 금액이 같은지 확인
        // 같은 키로 다른 금액 결제를 시도하면 기존 결과를 반환하면 안 된다
        if(transfer.getAmount().compareTo(requestedAmount) != 0){
            throw new BusinessException(WalletErrorCode.IDEMPOTENCY_KEY_CONFLICT);
        }

        // 6. 결제자의 지갑을 다시 조회
        // 응답의 currencyCode와, 최초 결제 직후 잔액을 조회할 때 사용
        Wallet payerWallet = walletMapper.findByMemberId(memberId);

        // 7. 최초 결제 딩기 생성된 결제자 DEBIT 원장을 조회한다.
        // 현재 잔액이 아니라, 결제 직후의 balancedAfter를 응답해야
        // 최초 요청의 결제 결과와 동일한 응답을 돌려줄 수 있아
        WalletLedgerEntry debitEntry =
            walletLedgerMapper.findByTransferIdAndWalletId(
                transfer.getTransferId(),
                payerWallet.getWalletId()
            );

        // 8. 새 이체, 원장, 잔액 변경 없이, 기존 완료 겲과를 그대로 반환
        return new QrPaymentExecuteResponse(
            transfer.getTransferId(),
            qrPayment.getQrPaymentCodeId(),
            transfer.getTransferStatus(),
            transfer.getAmount(),
            debitEntry.getBalanceAfter(),
            payerWallet.getCurrencyCode(),
            transfer.getCompletedAt()
        );
    }
}
