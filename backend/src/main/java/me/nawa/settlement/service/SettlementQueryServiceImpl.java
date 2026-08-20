package me.nawa.settlement.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import me.nawa.common.exception.BusinessException;
import me.nawa.settlement.domain.SettlementCollectionMember;
import me.nawa.settlement.domain.SettlementDetail;
import me.nawa.settlement.domain.SettlementParticipant;
import me.nawa.settlement.domain.SettlementSummary;
import me.nawa.settlement.domain.SettlementViewerContext;
import me.nawa.settlement.dto.response.SettlementCandidateResponse;
import me.nawa.settlement.dto.response.SettlementCollectionParticipantResponse;
import me.nawa.settlement.dto.response.SettlementCollectionResponse;
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
        SettlementCollectionResponse collection = "CREATOR".equals(role)
            ? toCollectionResponse(settlementMapper.findCollectionMembers(settlementId)) : null;
        return SettlementDetailResponse.builder().id(detail.getSettlementId()).type(detail.getSplitMethod())
            .totalAmount(detail.getTotalAmount()).status(detail.getSettlementStatus()).requestedBy(detail.getRequestedBy())
            .gatheringName(detail.getGatheringName()).merchantName(detail.getMerchantName())
            .viewerItems(viewerItems).transactionId(detail.getTransactionNumber())
            .paidBy(detail.getPaidBy()).viewer(viewerPolicy.resolve(SettlementViewerContext.builder()
                .role(role)
                .shareAmount(detail.getViewerShareAmount())
                .requestStatus(detail.getViewerRequestStatus())
                .settlementStatus(detail.getSettlementStatus())
                .build()))
            .collection(collection).build();
    }

    /**
     * 돈을 받을 사람에게만 보여 줄 납부 현황을 만든다.
     *
     * 몇 명이 냈는지는 여기서 센다. 조회는 있는 그대로 가져오는 일이고, 그 숫자가
     * 무슨 뜻인지 정하는 것은 서비스의 몫이다.
     */
    private SettlementCollectionResponse toCollectionResponse(List<SettlementCollectionMember> members) {
        return SettlementCollectionResponse.builder().totalCount(members.size())
            .paidCount((int) members.stream().filter(member -> "PAID".equals(member.getRequestStatus())).count())
            .participants(members.stream().map(member -> SettlementCollectionParticipantResponse.builder()
                .id(member.getAppointmentMemberId()).name(member.getDisplayName())
                .initials(initialsOf(member.getDisplayName())).shareAmount(member.getShareAmount())
                .requestStatus(member.getRequestStatus()).build()).toList()).build();
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
                .build()))
            .createdAt(summary.getCreatedAt())
            .completedAt(summary.getCompletedAt()).build()).toList();
    }

    private List<SettlementParticipantResponse> toParticipantResponses(List<SettlementParticipant> participants) {
        return participants.stream().map(participant -> SettlementParticipantResponse.builder()
            .id(participant.getAppointmentMemberId())
            .name(participant.getDisplayName()).initials(initialsOf(participant.getDisplayName())).build()).toList();
    }

    /**
     * 사진이 없는 자리에 대신 넣을 이름 첫 글자다. 이름을 알 수 없으면 물음표로 둔다.
     *
     * 앞뒤 공백을 먼저 털어낸다. 그러지 않으면 " Alex"에서 공백 한 칸을 잘라 와 빈 동그라미가 된다.
     * 그리고 한 글자는 자리 하나가 아닐 수 있다. 이모지처럼 두 자리를 차지하는 글자를 한 자리만
     * 잘라내면 글자의 반쪽만 남아 깨져 보인다.
     */
    private String initialsOf(String displayName) {
        if (displayName == null || displayName.isBlank()) return "?";
        String trimmed = displayName.strip();
        return new String(Character.toChars(trimmed.codePointAt(0))).toUpperCase();
    }
}
