package me.nawa.settlement.mapper;

import java.util.List;
import me.nawa.settlement.domain.Settlement;
import me.nawa.settlement.domain.SettlementDetail;
import me.nawa.settlement.domain.SettlementItem;
import me.nawa.settlement.domain.SettlementItemShare;
import me.nawa.settlement.domain.SettlementMember;
import me.nawa.settlement.domain.SettlementParticipant;
import me.nawa.settlement.domain.SettlementSource;
import me.nawa.settlement.domain.SettlementSummary;
import me.nawa.settlement.domain.SettlementViewerItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** V9 축소 정산 스키마의 조회·생성·지급 영속성 계약이다. */
@Mapper
public interface SettlementMapper {
    SettlementDetail findDetail(
        @Param("settlementId") Long settlementId,
        @Param("memberId") Long memberId
    );

    List<SettlementViewerItem> findViewerItems(
        @Param("settlementId") Long settlementId,
        @Param("memberId") Long memberId
    );

    List<SettlementSource> findCandidateSources(@Param("memberId") Long memberId);

    List<SettlementParticipant> findParticipants(@Param("appointmentId") Long appointmentId);

    List<SettlementSummary> findReceivedSummaries(@Param("memberId") Long memberId);

    List<SettlementSummary> findSentSummaries(@Param("memberId") Long memberId);

    SettlementSource findSourceForCreate(
        @Param("sourceTransferId") Long sourceTransferId,
        @Param("memberId") Long memberId
    );

    List<SettlementMember> findActiveMembers(@Param("appointmentId") Long appointmentId);

    Settlement findByIdForUpdate(@Param("settlementId") Long settlementId);

    Settlement findById(@Param("settlementId") Long settlementId);

    Settlement findByCreatorAndIdempotencyKey(
        @Param("createdByMemberId") Long createdByMemberId,
        @Param("idempotencyKey") String idempotencyKey
    );

    Settlement findBySourceTransferId(@Param("sourceTransferId") Long sourceTransferId);

    List<SettlementMember> findMembersBySettlementIdForUpdate(
        @Param("settlementId") Long settlementId
    );

    SettlementMember findMemberBySettlementAndMember(
        @Param("settlementId") Long settlementId,
        @Param("memberId") Long memberId
    );

    SettlementMember findMemberBySettlementAndMemberForUpdate(
        @Param("settlementId") Long settlementId,
        @Param("memberId") Long memberId
    );

    boolean existsPaidMember(@Param("settlementId") Long settlementId);

    void insertSettlement(Settlement settlement);

    void insertSettlementMembers(@Param("members") List<SettlementMember> members);

    void insertSettlementItem(SettlementItem item);

    void insertSettlementItemShares(
        @Param("settlementId") Long settlementId,
        @Param("shares") List<SettlementItemShare> shares
    );

    int markSettlementMemberPaid(
        @Param("settlementMemberId") Long settlementMemberId,
        @Param("transferId") Long transferId,
        @Param("idempotencyKey") String idempotencyKey
    );

    int completeSettlementIfNoPendingPayments(@Param("settlementId") Long settlementId);
}
