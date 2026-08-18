package me.nawa.wallet.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
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
import me.nawa.wallet.dto.response.QrPaymentStatusResponse;
import me.nawa.wallet.exception.WalletErrorCode;
import me.nawa.wallet.mapper.QrPaymentCodeMapper;
import me.nawa.wallet.mapper.TripExpenseLinkMapper;
import me.nawa.wallet.mapper.WalletLedgerMapper;
import me.nawa.wallet.mapper.WalletMapper;
import me.nawa.wallet.mapper.WalletTransferMapper;
import me.nawa.wallet.util.QrTokenGenerator;
import me.nawa.wallet.util.TransactionNumberGenerator;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

// QR 생성/검증/결제 미리보기/결제 실행 4개를 담당할 서비스 (아직 미구현).
// Notion "구현 계획" 이슈 4에 설계가 정리되어 있음 — createPaymentQr/resolveQr/previewPayment/executePayment 예정.
@Service
@RequiredArgsConstructor
public class QrPaymentServiceImpl implements QrPaymentService {

    private static final long QR_EXPIRATION_MINUTES = 1L;
    private static final int MAX_MEMO_LENGTH = 255;
    private static final int AMOUNT_SCALE = 4;
    private static final int MAX_AMOUNT_INTEGER_DIGITS = 15;

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

        BigDecimal amount = normalizeOptionalAmount(request.amount());

        QrPaymentCode qrPaymentCode = new QrPaymentCode(
            null,
            wallet.getWalletId(),
            null,
            qrToken,
            amount,
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

    // 아직 만료되지 않고 결제도 되지 않아 다시 조회할 수 있는 내 QR 목록. My QR 화면에서 쓴다.
    @Override
    public List<QrPaymentCreateResponse> listActivePaymentQrs(Long memberId) {
        Wallet wallet = walletMapper.findByMemberId(memberId);

        if (wallet == null) {
            throw new BusinessException(WalletErrorCode.WALLET_NOT_FOUND);
        }

        List<QrPaymentCode> activeQrPayments =
            qrPaymentCodeMapper.findActiveByPayeeWalletId(wallet.getWalletId(), LocalDateTime.now());

        return activeQrPayments.stream()
            .map(qrPaymentCode -> new QrPaymentCreateResponse(
                qrPaymentCode.getQrPaymentCodeId(),
                qrPaymentCode.getQrToken(),
                qrPaymentCode.getAmount(),
                qrPaymentCode.getMemo(),
                qrPaymentCode.getPaymentStatus().name(),
                wallet.getCurrencyCode(),
                qrPaymentCode.getExpiresAt()
            ))
            .toList();
    }

    @Override
    @Transactional
    public QrPaymentResolveResponse resolvePaymentQr(Long memberId, QrPaymentResolveRequest request) {
        validateQrToken(request);
        assertCanPay(memberId);

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
        assertCanPay(memberId);

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

    // 이 트랜잭션은 REPEATABLE READ(기본값) 대신 READ COMMITTED로 실행한다.
    //
    // REPEATABLE READ에서는 트랜잭션의 첫 조회가 만든 스냅샷이 커밋 전까지 고정돼, 이후의
    // 일반 SELECT는 그 사이 다른 트랜잭션이 커밋한 행을 못 볼 수 있다. 이 메서드는 QR/지갑
    // 잠금을 기다리는 동안 다른 요청이 같은 idempotency key로 먼저 결제를 끝낼 수 있어서,
    // 그 결과를 곧바로 봐야 한다 — READ COMMITTED는 매 statement마다 최신 커밋을 다시 읽으므로
    // 별도의 FOR UPDATE 없이 일반 SELECT만으로 이 문제가 해결된다.
    //
    // 그리고 idempotency_key는 NULL을 허용하는 유니크 컬럼이라, InnoDB가 "유니크 인덱스 검색이
    // 정확히 한 행을 특정하면 갭 락 없이 레코드 락만 건다"는 최적화를 이 컬럼에는 적용하지
    // 못한다 — 그래서 이 컬럼을 FOR UPDATE로 잠금 조회하면 격리 수준과 무관하게 존재하지 않는
    // 키에 갭 락이 걸릴 수 있고, 서로 다른 QR을 같은 key로 동시 실행하면 그 갭 락과 지갑
    // FOR UPDATE 락이 얽혀 교착 상태에 빠질 수 있다(QrPaymentConcurrencyIntegrationTest로
    // 재현됨). 그래서 idempotency_key는 절대 FOR UPDATE로 조회하지 않는다 — 승자 판정은
    // 전부 아래 insert의 유니크 제약(DuplicateKeyException)에 맡긴다.
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public QrPaymentExecuteResponse executePayment(
        Long memberId,
        String idempotencyKey,
        QrPaymentExecuteRequest request
    ) {
        validateExecuteRequest(idempotencyKey, request);
        assertCanPay(memberId);

        // 1. 이미 완료된 동일 요청이면 기존 결과 반환 (빠른 경로, 잠금 없음).
        // 최종 판단은 아래 insert 시도 결과로 내린다.
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

        // QR 잠금을 기다리는 동안 같은 QR·같은 key로 다른 요청이 결제를 끝냈을 수 있다.
        // idempotency_key를 다시 조회하는 대신, 방금 최신값으로 읽은 QR 행 자체의
        // completed_transfer_id(FK, NULL 허용 아님)로 판단한다. 일반 SELECT로 충분하다 —
        // READ COMMITTED라 이 statement가 실행되는 시점의 최신 커밋을 그대로 본다.
        if (qrPayment.getCompletedTransferId() != null) {
            WalletTransfer completedTransfer =
                walletTransferMapper.findByTransferId(qrPayment.getCompletedTransferId());

            if (completedTransfer != null
                && idempotencyKey.equals(completedTransfer.getIdempotencyKey())) {
                return getIdempotentResult(memberId, request, completedTransfer);
            }
            // 다른 key로 이미 완료된 QR을 다시 쓰려는 경우 — 아래 validateExecutableQr가
            // QR_PAYMENT_ALREADY_COMPLETED로 정확히 거절한다.
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
            request.spendingScope().name(),
            now,
            now,
            memberId,
            idempotencyKey
        );

        try {
            walletTransferMapper.insert(transfer);
        } catch (DuplicateKeyException exception) {
            // 여기 도달했다는 건 경쟁한 트랜잭션이 이미 커밋했다는 뜻이라 일반 SELECT로
            // 충분하다(READ COMMITTED). idempotency_key는 FOR UPDATE로 조회하지 않는다.
            WalletTransfer concurrent =
                walletTransferMapper.findByIdempotencyKey(idempotencyKey);

            if (concurrent != null) {
                return getIdempotentResult(memberId, request, concurrent);
            }

            throw new BusinessException(
                WalletErrorCode.IDEMPOTENCY_KEY_CONFLICT,
                exception
            );
        }

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

    @Override
    public QrPaymentStatusResponse getPaymentStatus(Long memberId, Long transferId) {
        // 1. PathVariable 기본 검증
        if(transferId == null || transferId <= 0){
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }

        // 2. 로그인 사용자의 지갑 조회
        Wallet payerWallet = walletMapper.findByMemberId(memberId);

        if(payerWallet == null){
            throw new BusinessException(WalletErrorCode.WALLET_NOT_FOUND);
        }

        // 3. 거래 자체 존재 여부 확인
        WalletTransfer transfer =
            walletTransferMapper.findByTransferId(transferId);

        if(transfer == null){
            throw new BusinessException(WalletErrorCode.TRANSACTION_NOT_FOUND);
        }

        // 4. QR 결제 거래만 이 API에서 조회할 수 있음
        if(!"QR_PAYMENT".equals(transfer.getTransferType())){
            throw new BusinessException(WalletErrorCode.TRANSACTION_NOT_FOUND);
        }

        // 5. 로그인 사용자의 원장을 확인한다.
        // QR 결제에서 결제자는 DEBIT, 수취인은 CREDIT 원장을 가진다.
        WalletLedgerEntry debitEntry =
            walletLedgerMapper.findByTransferIdAndWalletId(
                transferId,
                payerWallet.getWalletId()
            );

        // 원장이 없거나 CREDIT라면 결제자가 아닌 것이므로 조회를 막는다.
        if (debitEntry == null
            || !"DEBIT".equals(debitEntry.getEntryType())) {
            throw new BusinessException(WalletErrorCode.TRANSACTION_FORBIDDEN);
        }

        // 6. 결제 당시 DEBIT 원장의 balanceAfter를 반환한다.
        // 현재 지갑 잔액은 이후 거래로 바뀌었을 수 있으므로 사용하면 안 된다.
        return new QrPaymentStatusResponse(
            transfer.getTransferId(),
            transfer.getTransferStatus(),
            transfer.getAmount(),
            debitEntry.getBalanceAfter(),
            payerWallet.getCurrencyCode(),
            transfer.getCompletedAt()
        );
    }

    private void validateRequest(QrPaymentCreateRequest request){
        if(request == null){
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }

        // null은 결제자가 금액을 입력하는 QR이므로 허용
        normalizeOptionalAmount(request.amount());

        if(request.memo() != null
            && request.memo().trim().length() > MAX_MEMO_LENGTH){
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
    }

    // 가맹점은 QR 생성과 매출 조회만 한다. 결제자는 방한 외국인(TRAVELER)뿐이다.
    private void assertCanPay(Long memberId) {
        if ("MERCHANT".equals(qrPaymentCodeMapper.findAccountTypeByMemberId(memberId))) {
            throw new BusinessException(WalletErrorCode.MERCHANT_CANNOT_PAY);
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
            return normalizeAmount(qrAmount);
        }

        //금액 입력 QR: 결재자가 0보다 큰 금액을 반드시 입력
        if(requestAmount == null
        || requestAmount.compareTo(BigDecimal.ZERO) <= 0){
            throw new BusinessException(
                WalletErrorCode.QR_PAYMENT_AMOUNT_REQUIRED
            );
        }

        return normalizeAmount(requestAmount);
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

        // 5. ACTIVE로 남아 있어도 시간상 만료되었다면 결제를 차단한다.
        // 만료 상태 갱신은 별도 배치에서 처리한다.
        if(target.getExpiresAt() == null || !now.isBefore(target.getExpiresAt())){
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

        // 6. 소비 범위와 약속도 최초 요청과 같아야 한다.
        // scope는 transfer에 저장하고, 공동 소비의 appointmentId는 비용 연결에서 확인한다.
        if (!request.spendingScope().name().equals(transfer.getSpendingType())) {
            throw new BusinessException(WalletErrorCode.IDEMPOTENCY_KEY_CONFLICT);
        }

        // 7. 결제자의 지갑을 다시 조회
        // 응답의 currencyCode와, 최초 결제 직후 잔액을 조회할 때 사용
        Wallet payerWallet = walletMapper.findByMemberId(memberId);

        if (payerWallet == null) {
            throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }

        // 8. 최초 결제 때 생성된 결제자 DEBIT 원장을 조회한다.
        // 현재 잔액이 아니라, 결제 직후의 balancedAfter를 응답해야
        // 최초 요청의 결제 결과와 동일한 응답을 돌려줄 수 있아
        // 일반 SELECT로 충분하다 — READ COMMITTED라 다른 트랜잭션이 방금 커밋한 원장 행도
        // 그대로 보인다(executePayment의 @Transactional(isolation) 주석 참고).
        WalletLedgerEntry debitEntry =
            walletLedgerMapper.findByTransferIdAndWalletId(
                transfer.getTransferId(),
                payerWallet.getWalletId()
            );

        if (debitEntry == null) {
            throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }

        if (request.spendingScope() == SpendingScope.SHARED) {
            Long originalAppointmentId =
                tripExpenseLinkMapper.findAppointmentIdByLedgerEntryId(
                    debitEntry.getLedgerEntryId()
                );

            if (!request.appointmentId().equals(originalAppointmentId)) {
                throw new BusinessException(WalletErrorCode.IDEMPOTENCY_KEY_CONFLICT);
            }
        }

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

    private BigDecimal normalizeOptionalAmount(BigDecimal amount) {
        return amount == null ? null : normalizeAmount(amount);
    }

    // DECIMAL(19,4)와 동일한 정밀도로 검증·정규화한 값만 계산과 저장에 사용한다.
    private BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0
            || amount.scale() > AMOUNT_SCALE
            || amount.precision() - amount.scale() > MAX_AMOUNT_INTEGER_DIGITS) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }

        return amount.setScale(AMOUNT_SCALE, RoundingMode.UNNECESSARY);
    }
}
