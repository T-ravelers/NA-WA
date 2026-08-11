package me.nawa.settlement.service.creation;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
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
    public SettlementCreateResponse create(
        Long memberId,
        CreateSettlementRequest request,
        SettlementSource source,
        String idempotencyKey,
        String requestFingerprint
    ) {
        if (request.getReceiptAnalysisId() == null) throw new BusinessException(SettlementErrorCode.SETTLEMENT_CREATE_INVALID);
        ReceiptAnalysis analysis = settlementMapper.findReceiptAnalysisForUpdate(request.getReceiptAnalysisId());
        if (analysis == null || !"ALLOCATED".equals(analysis.getAnalysisStatus()) || !source.getTransferId().equals(analysis.getSourceTransferId())
            || !memberId.equals(analysis.getCreatedByMemberId())) throw new BusinessException(SettlementErrorCode.SETTLEMENT_CREATE_INVALID);
        List<ReceiptAllocationView> allocationViews = settlementMapper.findReceiptAllocationViews(
            analysis.getReceiptAnalysisId()
        );
        Map<Long, BigDecimal> amounts = allocationViews.stream().collect(java.util.stream.Collectors.toMap(
            ReceiptAllocationView::getAppointmentMemberId,
            ReceiptAllocationView::getAllocatedAmount
        ));
        BigDecimal allocationTotal = amounts.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal lineTotal = settlementMapper.sumReceiptItemLineTotals(analysis.getReceiptAnalysisId());
        if (!Set.copyOf(request.getParticipantAppointmentMemberIds()).equals(amounts.keySet())
            || source.getAmount().compareTo(analysis.getRecognizedTotal()) != 0
            || lineTotal == null || lineTotal.compareTo(analysis.getRecognizedTotal()) != 0
            || allocationTotal.compareTo(analysis.getRecognizedTotal()) != 0) {
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_CREATE_INVALID);
        }
        BigDecimal payerShare = allocationViews.stream()
            .filter(view -> memberId.equals(view.getMemberId()))
            .map(ReceiptAllocationView::getAllocatedAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        Settlement settlement = Settlement.builder().appointmentId(source.getAppointmentId()).createdByMemberId(memberId)
            .payerMemberId(source.getPayerMemberId()).sourceTransferId(source.getTransferId())
            .idempotencyKey(idempotencyKey).requestFingerprint(requestFingerprint).settlementStatus("DRAFT")
            .splitMethod(getType()).totalAmount(source.getAmount()).payerShareAmount(payerShare)
            .receivableAmount(source.getAmount().subtract(payerShare)).requestedAt(null).build();
        settlementMapper.insertSettlement(settlement);
        List<SettlementMember> members = settlementMapper.findActiveMembers(source.getAppointmentId()).stream()
            .filter(member -> amounts.containsKey(member.getAppointmentMemberId())).peek(member -> {
                member.setSettlementId(settlement.getSettlementId());
                member.setShareAmount(amounts.get(member.getAppointmentMemberId()));
                member.setRequestStatus("NOT_REQUESTED");
            }).toList();
        if (members.size() != amounts.size()) {
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_CREATE_INVALID);
        }
        settlementMapper.insertSettlementMembers(members);
        settlementMapper.copyReceiptItemsToSettlement(analysis.getReceiptAnalysisId(), settlement.getSettlementId());
        settlementMapper.copyReceiptItemSharesToSettlement(analysis.getReceiptAnalysisId(), settlement.getSettlementId());
        settlementMapper.markReceiptUsed(analysis.getReceiptAnalysisId());
        return SettlementCreateResponse.builder().id(settlement.getSettlementId()).build();
    }
}
