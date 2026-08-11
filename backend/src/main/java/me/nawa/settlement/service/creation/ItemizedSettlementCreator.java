package me.nawa.settlement.service.creation;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import me.nawa.common.exception.BusinessException;
import me.nawa.settlement.domain.ReceiptAllocationView;
import me.nawa.settlement.domain.ReceiptAnalysis;
import me.nawa.settlement.domain.Settlement;
import me.nawa.settlement.domain.SettlementMember;
import me.nawa.settlement.domain.SettlementSource;
import me.nawa.settlement.dto.request.CreateSettlementRequest;
import me.nawa.settlement.dto.response.SettlementCreateResponse;
import me.nawa.settlement.exception.SettlementErrorCode;
import me.nawa.settlement.mapper.SettlementMapper;
import org.springframework.stereotype.Component;

/** 확정된 영수증 배분을 항목별 정산 스냅샷으로 복제한다. */
@Component
@RequiredArgsConstructor
public class ItemizedSettlementCreator implements SettlementCreationHandler {
    private final SettlementMapper settlementMapper;

    @Override
    public String getType() { return "ITEMIZED"; }

    @Override
    public SettlementCreateResponse create(Long memberId, CreateSettlementRequest request, SettlementSource source) {
        if (request.getReceiptAnalysisId() == null) throw new BusinessException(SettlementErrorCode.SETTLEMENT_CREATE_INVALID);
        ReceiptAnalysis analysis = settlementMapper.findReceiptAnalysisForUpdate(request.getReceiptAnalysisId());
        if (analysis == null || !"ALLOCATED".equals(analysis.getAnalysisStatus()) || !source.getTransferId().equals(analysis.getSourceTransferId())
            || !memberId.equals(analysis.getCreatedByMemberId())) throw new BusinessException(SettlementErrorCode.SETTLEMENT_CREATE_INVALID);
        java.util.Map<Long, BigDecimal> amounts = settlementMapper.findReceiptAllocationViews(analysis.getReceiptAnalysisId()).stream()
            .collect(java.util.stream.Collectors.toMap(ReceiptAllocationView::getMemberId, ReceiptAllocationView::getAllocatedAmount));
        if (!Set.copyOf(request.getParticipantIds()).equals(amounts.keySet()) || amounts.values().stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add).compareTo(analysis.getRecognizedTotal()) != 0)
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_CREATE_INVALID);
        BigDecimal payerShare = amounts.getOrDefault(memberId, BigDecimal.ZERO);
        Settlement settlement = Settlement.builder().appointmentId(source.getAppointmentId()).createdByMemberId(memberId)
            .payerMemberId(source.getPayerMemberId()).sourceTransferId(source.getTransferId()).settlementStatus("REQUESTED")
            .splitMethod(getType()).totalAmount(analysis.getRecognizedTotal()).payerShareAmount(payerShare)
            .receivableAmount(analysis.getRecognizedTotal().subtract(payerShare)).requestedAt(LocalDateTime.now()).build();
        settlementMapper.insertSettlement(settlement);
        List<SettlementMember> members = settlementMapper.findActiveMembers(source.getAppointmentId()).stream()
            .filter(member -> amounts.containsKey(member.getMemberId())).peek(member -> {
                member.setSettlementId(settlement.getSettlementId()); member.setShareAmount(amounts.get(member.getMemberId()));
                member.setRequestStatus(memberId.equals(member.getMemberId()) ? "NOT_REQUESTED" : "PENDING");
            }).toList();
        settlementMapper.insertSettlementMembers(members);
        settlementMapper.copyReceiptItemsToSettlement(analysis.getReceiptAnalysisId(), settlement.getSettlementId());
        settlementMapper.copyReceiptItemSharesToSettlement(analysis.getReceiptAnalysisId(), settlement.getSettlementId());
        settlementMapper.markReceiptUsed(analysis.getReceiptAnalysisId());
        return SettlementCreateResponse.builder().id(settlement.getSettlementId()).build();
    }
}
