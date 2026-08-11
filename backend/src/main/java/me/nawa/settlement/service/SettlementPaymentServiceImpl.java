package me.nawa.settlement.service;

import lombok.RequiredArgsConstructor;
import me.nawa.common.exception.BusinessException;
import me.nawa.settlement.domain.Settlement;
import me.nawa.settlement.domain.SettlementMember;
import me.nawa.settlement.exception.SettlementErrorCode;
import me.nawa.settlement.mapper.SettlementMapper;
import me.nawa.wallet.service.WalletTransferService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 정산 상세 화면의 결제와 취소 상태 전이를 지갑 이체와 함께 처리한다. */
@Service
@RequiredArgsConstructor
public class SettlementPaymentServiceImpl implements SettlementPaymentService {
    private final SettlementMapper settlementMapper;
    private final WalletTransferService walletTransferService;

    @Override @Transactional
    public void paySettlement(Long memberId, Long settlementId) {
        Settlement settlement = settlementMapper.findByIdForUpdate(settlementId);
        if (settlement == null) throw new BusinessException(SettlementErrorCode.SETTLEMENT_NOT_FOUND);
        if (!"REQUESTED".equals(settlement.getSettlementStatus())) throw new BusinessException(SettlementErrorCode.SETTLEMENT_PAYMENT_NOT_ALLOWED);
        SettlementMember payment = settlementMapper.findMembersBySettlementIdForUpdate(settlementId).stream()
            .filter(member -> memberId.equals(member.getMemberId())).findFirst()
            .orElseThrow(() -> new BusinessException(SettlementErrorCode.SETTLEMENT_PAYMENT_NOT_FOUND));
        if (!"PENDING".equals(payment.getRequestStatus()) || payment.getShareAmount() == null || payment.getShareAmount().signum() <= 0)
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_PAYMENT_NOT_ALLOWED);
        long transferId = walletTransferService.transfer(memberId, memberId, settlement.getPayerMemberId(), payment.getShareAmount(),
            "Settlement #" + settlementId);
        if (settlementMapper.markSettlementMemberPaid(payment.getSettlementMemberId(), transferId) != 1)
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_PAYMENT_NOT_ALLOWED);
        settlementMapper.completeSettlementIfNoPendingPayments(settlementId);
    }

    @Override @Transactional
    public void cancelSettlement(Long memberId, Long settlementId) {
        Settlement settlement = settlementMapper.findByIdForUpdate(settlementId);
        if (settlement == null) throw new BusinessException(SettlementErrorCode.SETTLEMENT_NOT_FOUND);
        if (!memberId.equals(settlement.getCreatedByMemberId())
            || !("DRAFT".equals(settlement.getSettlementStatus()) || "REQUESTED".equals(settlement.getSettlementStatus()))
            || settlementMapper.cancelSettlement(settlementId, memberId) != 1)
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_CANCEL_NOT_ALLOWED);
        if ("GAME".equals(settlement.getSplitMethod())
            && settlementMapper.cancelGame(settlementId) != 1) {
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_CANCEL_NOT_ALLOWED);
        }
    }
}
