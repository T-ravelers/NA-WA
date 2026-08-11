package me.nawa.settlement.mapper;

import java.util.List;
import me.nawa.settlement.domain.ReceiptAnalysis;
import me.nawa.settlement.domain.Settlement;
import me.nawa.settlement.domain.SettlementGame;
import me.nawa.settlement.domain.SettlementGameMember;
import me.nawa.settlement.domain.SettlementItem;
import me.nawa.settlement.domain.SettlementMember;
import me.nawa.settlement.domain.SettlementSource;
import me.nawa.settlement.domain.SettlementSummary;
import me.nawa.settlement.domain.SettlementParticipant;
import me.nawa.settlement.domain.SettlementDetail;
import me.nawa.settlement.domain.ReceiptAnalysisItem;
import me.nawa.settlement.domain.ReceiptItemAllocation;
import me.nawa.settlement.domain.ReceiptAllocationView;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 정산 영속성 매퍼
 *
 * 정산 도메인 데이터를 조회하고 저장하는 MyBatis 매퍼를 정의합니다.
 */
@Mapper
public interface SettlementMapper {
    SettlementDetail findDetail(
        @Param("settlementId") Long settlementId,
        @Param("memberId") Long memberId
    );

    List<String> findItemNames(@Param("settlementId") Long settlementId);

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

    List<SettlementMember> findMembersBySettlementIdForUpdate(
        @Param("settlementId") Long settlementId
    );

    void insertSettlement(Settlement settlement);

    void insertSettlementMembers(@Param("members") List<SettlementMember> members);

    void insertSettlementItems(@Param("items") List<SettlementItem> items);

    ReceiptAnalysis findReceiptAnalysisForUpdate(@Param("receiptAnalysisId") Long receiptAnalysisId);

    void insertReceiptAnalysis(ReceiptAnalysis receiptAnalysis);

    void deleteReceiptItems(@Param("receiptAnalysisId") Long receiptAnalysisId);

    void insertReceiptItems(@Param("items") List<ReceiptAnalysisItem> items);

    void updateReceiptTotal(
        @Param("receiptAnalysisId") Long receiptAnalysisId,
        @Param("recognizedTotal") java.math.BigDecimal recognizedTotal
    );

    List<ReceiptAnalysisItem> findReceiptItemsForUpdate(@Param("receiptAnalysisId") Long receiptAnalysisId);

    void deleteReceiptAllocations(@Param("receiptAnalysisId") Long receiptAnalysisId);

    void insertReceiptAllocations(@Param("allocations") List<ReceiptItemAllocation> allocations);

    void markReceiptAllocated(@Param("receiptAnalysisId") Long receiptAnalysisId);

    List<ReceiptAllocationView> findReceiptAllocationViews(@Param("receiptAnalysisId") Long receiptAnalysisId);

    void copyReceiptItemsToSettlement(
        @Param("receiptAnalysisId") Long receiptAnalysisId,
        @Param("settlementId") Long settlementId
    );

    void copyReceiptItemSharesToSettlement(
        @Param("receiptAnalysisId") Long receiptAnalysisId,
        @Param("settlementId") Long settlementId
    );

    void markReceiptUsed(@Param("receiptAnalysisId") Long receiptAnalysisId);

    void insertSettlementGame(SettlementGame settlementGame);

    void insertSettlementGameMembers(@Param("members") List<SettlementGameMember> members);

    SettlementGame findSettlementGameForUpdate(@Param("settlementId") Long settlementId);

    SettlementGame findSettlementGame(@Param("settlementId") Long settlementId);

    int updateGameConsent(
        @Param("settlementId") Long settlementId,
        @Param("memberId") Long memberId,
        @Param("consentStatus") String consentStatus
    );

    List<SettlementGameMember> findGameMembersForUpdate(@Param("settlementId") Long settlementId);

    List<SettlementGameMember> findGameMembers(@Param("settlementId") Long settlementId);

    void assignGameLiables(
        @Param("settlementId") Long settlementId,
        @Param("appointmentMemberIds") List<Long> appointmentMemberIds
    );

    void activateGameSettlement(
        @Param("settlementId") Long settlementId,
        @Param("payerShareAmount") java.math.BigDecimal payerShareAmount,
        @Param("receivableAmount") java.math.BigDecimal receivableAmount
    );

    void completeGame(@Param("settlementId") Long settlementId, @Param("randomSeed") String randomSeed);

    int markSettlementMemberPaid(
        @Param("settlementMemberId") Long settlementMemberId,
        @Param("transferId") Long transferId
    );

    int completeSettlementIfNoPendingPayments(@Param("settlementId") Long settlementId);

    int cancelSettlement(
        @Param("settlementId") Long settlementId,
        @Param("memberId") Long memberId
    );
}
