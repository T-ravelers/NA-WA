package me.nawa.report.mapper;

import java.time.LocalDate;
import java.util.List;
import me.nawa.report.domain.Report;
import me.nawa.report.domain.ReportCohortSnapshot;
import me.nawa.report.domain.ReportComparisonMember;
import me.nawa.report.domain.ReportComparisonSpending;
import me.nawa.report.domain.ReportJourney;
import me.nawa.report.domain.ReportTimelineItem;
import me.nawa.report.domain.ReportExpense;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ReportMapper {

    ReportJourney findJourneyForUpdate(@Param("tripId") Long tripId);

    ReportJourney findJourneyById(@Param("tripId") Long tripId);

    Report findActiveReportByTripId(@Param("tripId") Long tripId);

    List<ReportTimelineItem> findTimelineItemsByTripId(
        @Param("tripId") Long tripId
    );

    List<ReportExpense> findExpenseCandidates(
        @Param("tripId") Long tripId,
        @Param("memberId") Long memberId
    );

    List<ReportExpense> findEligibleExpensesForUpdate(
        @Param("tripId") Long tripId,
        @Param("memberId") Long memberId,
        @Param("transferIds") List<Long> transferIds
    );

    List<Long> findLinkedLedgerEntryIdsByTripId(@Param("tripId") Long tripId);

    Long findLinkedTripIdByLedgerEntryId(
        @Param("ledgerEntryId") Long ledgerEntryId
    );

    void insertTripExpenseLink(
        @Param("tripId") Long tripId,
        @Param("ledgerEntryId") Long ledgerEntryId
    );

    void insertReport(Report report);

    Report findReportById(@Param("reportId") Long reportId);

    List<Report> findReportsByMemberId(@Param("memberId") Long memberId);

    ReportComparisonMember findComparisonMember(@Param("memberId") Long memberId);

    List<ReportComparisonMember> findComparisonPeerMembers(
        @Param("tripId") Long tripId,
        @Param("memberId") Long memberId
    );

    List<ReportComparisonSpending> findComparisonSpending(
        @Param("memberIds") List<Long> memberIds,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    List<ReportCohortSnapshot> findSimilarCohortAnalytics(
        @Param("nationalityCode") String nationalityCode,
        @Param("memberId") Long memberId,
        @Param("limit") int limit
    );
}
