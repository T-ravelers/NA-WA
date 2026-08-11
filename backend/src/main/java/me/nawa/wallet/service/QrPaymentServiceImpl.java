package me.nawa.wallet.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import me.nawa.common.exception.BusinessException;
import me.nawa.common.exception.CommonErrorCode;
import me.nawa.wallet.domain.QrPaymentCode;
import me.nawa.wallet.domain.QrPaymentResolveTarget;
import me.nawa.wallet.domain.Wallet;
import me.nawa.wallet.domain.enums.QrPaymentStatus;
import me.nawa.wallet.dto.request.QrPaymentCreateRequest;
import me.nawa.wallet.dto.request.QrPaymentResolveRequest;
import me.nawa.wallet.dto.response.QrPaymentCreateResponse;
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

        //qr_token으로 검색
        QrPaymentResolveTarget target =
            qrPaymentCodeMapper.findResolveTargetByToken(request.qrToken().trim());

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

        // 6. 자기 자신에 대한 결제일 때
        if(payerWallet.getWalletId().equals(target.getPayeeWalletId())){
            throw new BusinessException(WalletErrorCode.QR_SELF_PAYMENT_NOT_ALLOWED);
        }

        // 7. 결제를 받는 사람의 지갑이 유효하지 않을 때
        if(!"ACTIVE".equals(target.getPayeeWalletStatus())){
            throw new BusinessException(WalletErrorCode.QR_PAYEE_WALLET_NOT_ACTIVE);
        }

        // 8. 모든 예외 케이스에서 걸리지 않는다면 정상적으로 응답
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
}
