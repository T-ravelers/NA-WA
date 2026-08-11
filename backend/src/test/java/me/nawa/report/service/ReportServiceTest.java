package me.nawa.report.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import me.nawa.common.exception.BusinessException;
import me.nawa.report.domain.Report;
import me.nawa.report.domain.ReportJourney;
import me.nawa.report.domain.ReportTimelineItem;
import me.nawa.report.dto.request.ReportCreateRequest;
import me.nawa.report.dto.response.ReportDetailResponse;
import me.nawa.report.exception.ReportErrorCode;
import me.nawa.report.mapper.ReportMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private ReportMapper reportMapper;

    private ReportService reportService;

    @BeforeEach
    void setUp() {
        reportService = new ReportService(reportMapper);
    }

    @Test
    void createReport_storesCompletedSnapshotWithDefaultLocale() {
        when(reportMapper.findJourneyForUpdate(1L)).thenReturn(completedJourney());
        when(reportMapper.findActiveReportByTripId(1L)).thenReturn(null);
        when(reportMapper.findTimelineItemsByTripId(1L)).thenReturn(List.of(
            ReportTimelineItem.builder()
                .tripItemId(10L)
                .itemId(990001L)
                .visitDate(LocalDate.of(2026, 8, 2))
                .itemType("EVENT")
                .title("Example event")
                .status("ADDED")
                .build()
        ));
        doAnswer(invocation -> {
            Report report = invocation.getArgument(0);
            report.setReportId(100L);
            return null;
        }).when(reportMapper).insertReport(any(Report.class));
        when(reportMapper.findReportById(100L)).thenReturn(savedReport());

        ReportDetailResponse result = reportService.createReport(
            1L,
            1L,
            null
        );

        ArgumentCaptor<Report> reportCaptor = ArgumentCaptor.forClass(
            Report.class
        );
        verify(reportMapper).insertReport(reportCaptor.capture());
        Report inserted = reportCaptor.getValue();
        assertEquals("COMPLETED", inserted.getGenerationStatus());
        assertEquals("en", inserted.getLocale());
        assertEquals(
            "Seoul Foodie Week",
            result.getReportContent().getJourney().getTitle()
        );
        assertEquals(1, result.getReportContent().getDays().size());
        assertEquals(
            990001L,
            result.getReportContent().getDays().get(0).getItems().get(0)
                .getItemId()
        );
    }

    @Test
    void createReport_storesEmptyDaysWhenTimelineIsEmpty() {
        when(reportMapper.findJourneyForUpdate(1L)).thenReturn(completedJourney());
        when(reportMapper.findActiveReportByTripId(1L)).thenReturn(null);
        when(reportMapper.findTimelineItemsByTripId(1L)).thenReturn(List.of());
        doAnswer(invocation -> {
            Report report = invocation.getArgument(0);
            report.setReportId(101L);
            return null;
        }).when(reportMapper).insertReport(any(Report.class));
        Report emptySavedReport = savedReport(false);
        emptySavedReport.setLocale("ja");
        when(reportMapper.findReportById(101L)).thenReturn(emptySavedReport);

        ReportDetailResponse result = reportService.createReport(
            1L,
            1L,
            new ReportCreateRequest("ja")
        );

        assertEquals("ja", result.getLocale());
        assertEquals(List.of(), result.getReportContent().getDays());
    }

    @Test
    void createReport_rejectsUnsupportedLocaleBeforeReadingJourney() {
        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> reportService.createReport(
                1L,
                1L,
                new ReportCreateRequest("ko")
            )
        );

        assertEquals(ReportErrorCode.INVALID_REPORT_INPUT, exception.getErrorCode());
        verify(reportMapper, never()).findJourneyForUpdate(1L);
    }

    @Test
    void createReport_rejectsDuplicateSelectedTransferIdsBeforeReadingJourney() {
        ReportCreateRequest request = new ReportCreateRequest();
        request.setTransferIds(List.of(10L, 10L));

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> reportService.createReport(1L, 1L, request)
        );

        assertEquals(
            ReportErrorCode.INVALID_REPORT_EXPENSE,
            exception.getErrorCode()
        );
        verify(reportMapper, never()).findJourneyForUpdate(1L);
    }

    @Test
    void createReport_rejectsJourneyThatHasNotEndedInKorea() {
        ReportJourney journey = ReportJourney.builder()
            .tripId(1L)
            .memberId(1L)
            .title("Future Journey")
            .startDate(LocalDate.now(ZoneId.of("Asia/Seoul")))
            .endDate(LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(1))
            .build();
        when(reportMapper.findJourneyForUpdate(1L)).thenReturn(journey);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> reportService.createReport(1L, 1L, null)
        );

        assertEquals(ReportErrorCode.JOURNEY_NOT_COMPLETED, exception.getErrorCode());
        verify(reportMapper, never()).findActiveReportByTripId(1L);
    }

    @Test
    void createReport_rejectsDuplicateActiveReport() {
        when(reportMapper.findJourneyForUpdate(1L)).thenReturn(completedJourney());
        when(reportMapper.findActiveReportByTripId(1L)).thenReturn(
            Report.builder().reportId(90L).tripId(1L).build()
        );

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> reportService.createReport(1L, 1L, null)
        );

        assertEquals(ReportErrorCode.REPORT_ALREADY_EXISTS, exception.getErrorCode());
        verify(reportMapper, never()).insertReport(any(Report.class));
    }

    @Test
    void createReport_rejectsJourneyOwnedByAnotherMember() {
        when(reportMapper.findJourneyForUpdate(1L)).thenReturn(
            ReportJourney.builder()
                .tripId(1L)
                .memberId(2L)
                .endDate(LocalDate.of(2026, 8, 1))
                .build()
        );

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> reportService.createReport(1L, 1L, null)
        );

        assertEquals(
            ReportErrorCode.REPORT_JOURNEY_FORBIDDEN,
            exception.getErrorCode()
        );
        verify(reportMapper, never()).findActiveReportByTripId(1L);
    }

    @Test
    void getReport_returnsStoredSnapshotAsTypedResponse() {
        when(reportMapper.findReportById(100L)).thenReturn(savedReport());

        ReportDetailResponse result = reportService.getReport(1L, 100L);

        assertEquals(100L, result.getReportId());
        assertEquals("COMPLETED", result.getGenerationStatus());
        assertFalse(result.getReportContent().getDays().isEmpty());
        assertEquals(
            LocalDate.of(2026, 8, 2),
            result.getReportContent().getDays().get(0).getVisitDate()
        );
    }

    @Test
    void getReport_rejectsAnotherMembersReportAsForbidden() {
        Report report = savedReport();
        report.setMemberId(2L);
        when(reportMapper.findReportById(100L)).thenReturn(report);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> reportService.getReport(1L, 100L)
        );

        assertEquals(
            ReportErrorCode.REPORT_JOURNEY_FORBIDDEN,
            exception.getErrorCode()
        );
    }

    @Test
    void getReport_rejectsMissingReportAsNotFound() {
        when(reportMapper.findReportById(404L)).thenReturn(null);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> reportService.getReport(1L, 404L)
        );

        assertEquals(ReportErrorCode.REPORT_NOT_FOUND, exception.getErrorCode());
    }

    private ReportJourney completedJourney() {
        return ReportJourney.builder()
            .tripId(1L)
            .memberId(1L)
            .title("Seoul Foodie Week")
            .startDate(LocalDate.of(2026, 8, 1))
            .endDate(LocalDate.of(2026, 8, 5))
            .build();
    }

    private Report savedReport() {
        return savedReport(true);
    }

    private Report savedReport(boolean withItems) {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonContent content = new JsonContent(objectMapper, withItems);
        return Report.builder()
            .reportId(100L)
            .tripId(1L)
            .memberId(1L)
            .title("Seoul Foodie Week")
            .startDate(LocalDate.of(2026, 8, 1))
            .endDate(LocalDate.of(2026, 8, 5))
            .generationStatus("COMPLETED")
            .locale("en")
            .reportContent(content.node())
            .generatedAt(LocalDateTime.of(2026, 8, 9, 12, 0))
            .createdAt(LocalDateTime.of(2026, 8, 9, 12, 0))
            .build();
    }

    private static final class JsonContent {
        private final com.fasterxml.jackson.databind.node.ObjectNode node;

        private JsonContent(ObjectMapper objectMapper, boolean withItems) {
            node = objectMapper.createObjectNode();
            node.putObject("journey")
                .put("tripId", 1L)
                .put("title", "Seoul Foodie Week")
                .put("startDate", "2026-08-01")
                .put("endDate", "2026-08-05");
            if (withItems) {
                node.putArray("days")
                    .addObject()
                    .put("visitDate", "2026-08-02")
                    .putArray("items")
                    .addObject()
                    .put("tripItemId", 10L)
                    .put("itemId", 990001L)
                    .put("itemType", "EVENT")
                    .put("title", "Example event")
                    .put("status", "ADDED");
            } else {
                node.putArray("days");
            }
        }

        private com.fasterxml.jackson.databind.JsonNode node() {
            return node;
        }
    }
}
