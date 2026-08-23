package me.nawa.settlement.service;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import me.nawa.common.exception.BusinessException;
import me.nawa.settlement.domain.Settlement;
import me.nawa.settlement.domain.SettlementMember;
import me.nawa.settlement.domain.SettlementViewerContext;
import me.nawa.settlement.dto.response.SettlementMutationResponse;
import me.nawa.settlement.event.SettlementCompletedEvent;
import me.nawa.settlement.event.SettlementPaidEvent;
import me.nawa.settlement.exception.SettlementErrorCode;
import me.nawa.settlement.mapper.SettlementMapper;
import me.nawa.wallet.service.WalletTransferService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 정산 상세 화면의 결제 상태 전이를 지갑 이체와 함께 처리한다. */
@Service
@RequiredArgsConstructor
public class SettlementPaymentServiceImpl implements SettlementPaymentService {
    private final SettlementMapper settlementMapper;
    private final WalletTransferService walletTransferService;
    private final SettlementViewerPolicy viewerPolicy;
    private final ApplicationEventPublisher eventPublisher;

    @Override @Transactional
    public SettlementMutationResponse paySettlement(
        Long memberId,
        Long settlementId,
        String idempotencyKey
    ) {
        String normalizedKey = validateIdempotencyKey(idempotencyKey);
        Settlement settlement = settlementMapper.findByIdForUpdate(settlementId);
        if (settlement == null) throw new BusinessException(SettlementErrorCode.SETTLEMENT_NOT_FOUND);
        SettlementMember payment = settlementMapper.findMemberBySettlementAndMemberForUpdate(
            settlementId, memberId
        );
        if (payment == null) {
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_PAYMENT_NOT_FOUND);
        }
        if ("PAID".equals(payment.getRequestStatus())) {
            if (!normalizedKey.equals(payment.getPaymentIdempotencyKey())) {
                throw new BusinessException(
                    SettlementErrorCode.SETTLEMENT_PAYMENT_IDEMPOTENCY_CONFLICT
                );
            }
            return paymentResponse(settlement, payment);
        }
        if (!"REQUESTED".equals(settlement.getSettlementStatus())) throw new BusinessException(SettlementErrorCode.SETTLEMENT_PAYMENT_NOT_ALLOWED);
        if (!"PENDING".equals(payment.getRequestStatus()) || payment.getShareAmount() == null || payment.getShareAmount().signum() <= 0)
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_PAYMENT_NOT_ALLOWED);
        long transferId = walletTransferService.transfer(memberId, memberId, settlement.getPayerMemberId(), payment.getShareAmount(),
            "Settlement #" + settlementId);
        if (settlementMapper.markSettlementMemberPaid(
            payment.getSettlementMemberId(), transferId, normalizedKey
        ) != 1)
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_PAYMENT_NOT_ALLOWED);
        eventPublisher.publishEvent(new SettlementPaidEvent(settlementId, memberId));
        // 이 UPDATE는 아직 REQUESTED인 정산만 바꾼다. 마지막 지급이 동시에 여러 번 들어와도
        // 한 번만 한 줄을 바꾸므로, 반환값이 1인 그 한 번이 곧 완료 알림 1회를 보장한다.
        if (settlementMapper.completeSettlementIfNoPendingPayments(
            settlementId, LocalDateTime.now()
        ) == 1) {
            eventPublisher.publishEvent(new SettlementCompletedEvent(settlementId));
        }
        payment.setRequestStatus("PAID");
        payment.setPaidTransferId(transferId);
        payment.setPaymentIdempotencyKey(normalizedKey);
        Settlement current = settlementMapper.findById(settlementId);
        return paymentResponse(current == null ? settlement : current, payment);
    }

    private SettlementMutationResponse paymentResponse(
        Settlement settlement,
        SettlementMember payment
    ) {
        return SettlementMutationResponse.builder()
            .settlementId(settlement.getSettlementId())
            .settlementStatus(settlement.getSettlementStatus())
            .transferId(payment.getPaidTransferId())
            .viewer(viewerPolicy.resolve(SettlementViewerContext.builder()
                .role("PARTICIPANT")
                .shareAmount(payment.getShareAmount())
                .requestStatus(payment.getRequestStatus())
                .settlementStatus(settlement.getSettlementStatus())
                .build()))
            .build();
    }

    private String validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 100) {
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_IDEMPOTENCY_KEY_INVALID);
        }
        return idempotencyKey.trim();
    }

}
