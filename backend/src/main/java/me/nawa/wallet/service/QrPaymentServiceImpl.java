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
import me.nawa.wallet.domain.enums.QrPaymentStatus;
import me.nawa.wallet.domain.enums.SpendingScope;
import me.nawa.wallet.dto.request.QrPaymentCreateRequest;
import me.nawa.wallet.dto.request.QrPaymentPreviewRequest;
import me.nawa.wallet.dto.request.QrPaymentResolveRequest;
import me.nawa.wallet.dto.response.QrPaymentCreateResponse;
import me.nawa.wallet.dto.response.QrPaymentPreviewResponse;
import me.nawa.wallet.dto.response.QrPaymentPreviewResponse.AppointmentInfo;
import me.nawa.wallet.dto.response.QrPaymentPreviewResponse.TripInfo;
import me.nawa.wallet.dto.response.QrPaymentResolveResponse;
import me.nawa.wallet.exception.WalletErrorCode;
import me.nawa.wallet.mapper.QrPaymentCodeMapper;
import me.nawa.wallet.mapper.WalletMapper;
import me.nawa.wallet.util.QrTokenGenerator;
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

}
