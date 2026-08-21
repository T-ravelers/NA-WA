package me.nawa.settlement.mapper;

import java.time.LocalDateTime;
import java.util.List;
import me.nawa.settlement.domain.Settlement;
import me.nawa.settlement.domain.SettlementCollectionMember;
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

    List<SettlementCollectionMember> findCollectionMembers(
        @Param("settlementId") Long settlementId
    );

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

    // 완료 시각은 DB에게 "지금 몇 시냐"고 묻지 않고 애플리케이션이 넘긴 값을 쓴다.
    //
    // 운영 DB의 시계는 docker-compose.yml에서 앱과 같은 +09:00으로 이미 맞춰 뒀다.
    // 그런데도 DB 시계에 기대지 않는 이유는, 그 맞춤이 설정 한 줄에 달려 있고 CI는
    // 이런 의존을 드러내려고 MySQL을 일부러 UTC로 띄우기 때문이다. 이 값은 화면에
    // "언제 끝났는지"로 그대로 보이고 기간 필터가 날짜를 가르는 기준이라, 두 시계가
    // 한 번 어긋나면 경계 근처의 정산이 통째로 다른 날짜로 묶인다.
    //
    // 지갑 이체·충전·QR 결제의 completed_at도 같은 이유로 애플리케이션 시각을 받는다.
    int completeSettlementIfNoPendingPayments(
        @Param("settlementId") Long settlementId,
        @Param("completedAt") LocalDateTime completedAt
    );
}
