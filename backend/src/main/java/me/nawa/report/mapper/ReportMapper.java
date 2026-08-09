package me.nawa.report.mapper;

import java.util.List;
import me.nawa.report.domain.Report;
import me.nawa.report.domain.ReportJourney;
import me.nawa.report.domain.ReportTimelineItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ReportMapper {

    ReportJourney findJourneyForUpdate(@Param("tripId") Long tripId);

    Report findActiveReportByTripId(@Param("tripId") Long tripId);

    List<ReportTimelineItem> findTimelineItemsByTripId(
        @Param("tripId") Long tripId
    );

    void insertReport(Report report);

    Report findReportById(@Param("reportId") Long reportId);

    List<Report> findReportsByMemberId(@Param("memberId") Long memberId);
}
