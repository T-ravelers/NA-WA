package me.nawa.report.mapper;

import java.util.List;
import me.nawa.report.domain.Report;
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
}
