package me.nawa.settlement.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import me.nawa.common.exception.BusinessException;
import me.nawa.settlement.domain.SettlementDetail;
import me.nawa.settlement.domain.SettlementParticipant;
import me.nawa.settlement.domain.SettlementSummary;
import me.nawa.settlement.domain.SettlementViewerContext;
import me.nawa.settlement.dto.response.SettlementCandidateResponse;
import me.nawa.settlement.dto.response.SettlementDetailResponse;
import me.nawa.settlement.dto.response.SettlementListResponse;
import me.nawa.settlement.dto.response.SettlementParticipantResponse;
import me.nawa.settlement.dto.response.SettlementSummaryResponse;
import me.nawa.settlement.dto.response.SettlementViewerItemResponse;
import me.nawa.settlement.exception.SettlementErrorCode;
import me.nawa.settlement.mapper.SettlementMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 정산 목록·생성 후보·상세 화면에 필요한 읽기 모델을 조립한다. */
@Service
@RequiredArgsConstructor
public class SettlementQueryServiceImpl implements SettlementQueryService {
    private final SettlementMapper settlementMapper;
    private final SettlementViewerPolicy viewerPolicy;

    @Override @Transactional(readOnly = true)
    public SettlementListResponse getSettlements(Long memberId) {
        return SettlementListResponse.builder()
            .received(toSummaryResponses(settlementMapper.findReceivedSummaries(memberId), memberId))
            .sent(toSummaryResponses(settlementMapper.findSentSummaries(memberId), memberId)).build();
    }

    @Override @Transactional(readOnly = true)
    public List<SettlementCandidateResponse> getCandidates(Long memberId) {
        return settlementMapper.findCandidateSources(memberId).stream().map(source ->
            SettlementCandidateResponse.builder().transferId(source.getTransferId())
                .appointmentId(source.getAppointmentId())
                .payerAppointmentMemberId(source.getPayerAppointmentMemberId())
                .journeyName(source.getJourneyName())
                .gatheringName(source.getGatheringName()).merchantName(source.getMerchantName()).amount(source.getAmount())
                .paidAt(source.getPaidAt()).payerName(source.getPayerName())
                .participants(toParticipantResponses(settlementMapper.findParticipants(source.getAppointmentId()))).build()
        ).toList();
    }

    @Override @Transactional(readOnly = true)
    public SettlementDetailResponse getSettlement(Long memberId, Long settlementId) {
        SettlementDetail detail = settlementMapper.findDetail(settlementId, memberId);
        if (detail == null) throw new BusinessException(SettlementErrorCode.SETTLEMENT_NOT_FOUND);
        String role = memberId.equals(detail.getCreatedByMemberId()) ? "CREATOR" : "PARTICIPANT";
        List<SettlementViewerItemResponse> viewerItems = "ITEMIZED".equals(detail.getSplitMethod())
            ? settlementMapper.findViewerItems(settlementId, memberId).stream()
                .map(item -> SettlementViewerItemResponse.builder()
                    .settlementItemId(item.getSettlementItemId())
                    .name(item.getName())
                    .allocatedQuantity(item.getAllocatedQuantity())
                    .allocatedAmount(item.getAllocatedAmount())
                    .build())
                .toList()
            : List.of();
        return SettlementDetailResponse.builder().id(detail.getSettlementId()).type(detail.getSplitMethod())
            .totalAmount(detail.getTotalAmount()).status(detail.getSettlementStatus()).requestedBy(detail.getRequestedBy())
            .gatheringName(detail.getGatheringName()).merchantName(detail.getMerchantName())
            .viewerItems(viewerItems).transactionId(detail.getTransactionNumber())
            .paidBy(detail.getPaidBy()).viewer(viewerPolicy.resolve(SettlementViewerContext.builder()
                .role(role)
                .shareAmount(detail.getViewerShareAmount())
                .requestStatus(detail.getViewerRequestStatus())
                .settlementStatus(detail.getSettlementStatus())
                .build())).build();
    }

    private List<SettlementSummaryResponse> toSummaryResponses(
        List<SettlementSummary> summaries,
        Long memberId
    ) {
        return summaries.stream().map(summary -> SettlementSummaryResponse.builder().id(summary.getSettlementId())
            .title(summary.getTitle()).totalAmount(summary.getTotalAmount())
            .receivableAmount(summary.getReceivableAmount()).type(summary.getSplitMethod())
            .status(summary.getSettlementStatus()).viewer(viewerPolicy.resolve(SettlementViewerContext.builder()
                .role(memberId.equals(summary.getCreatedByMemberId()) ? "CREATOR" : "PARTICIPANT")
                .shareAmount(summary.getViewerShareAmount())
                .requestStatus(summary.getViewerRequestStatus())
                .settlementStatus(summary.getSettlementStatus())
                .build())).build()).toList();
    }

    private List<SettlementParticipantResponse> toParticipantResponses(List<SettlementParticipant> participants) {
        return participants.stream().map(participant -> SettlementParticipantResponse.builder()
            .id(participant.getAppointmentMemberId())
            .name(participant.getDisplayName()).initials(participant.getDisplayName() == null || participant.getDisplayName().isBlank()
                ? "?" : participant.getDisplayName().substring(0, 1).toUpperCase()).build()).toList();
    }
}
