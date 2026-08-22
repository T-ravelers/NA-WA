package me.nawa.report.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import me.nawa.common.exception.BusinessException;
import me.nawa.report.domain.Report;
import me.nawa.report.domain.ReportCohortSnapshot;
import me.nawa.report.domain.ReportComparisonBasis;
import me.nawa.report.domain.ReportComparisonMember;
import me.nawa.report.domain.ReportComparisonScope;
import me.nawa.report.domain.ReportComparisonSpending;
import me.nawa.report.dto.response.ReportComparisonRankResponse;
import me.nawa.report.dto.response.ReportComparisonResponse;
import me.nawa.report.exception.ReportErrorCode;
import me.nawa.report.mapper.ReportMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReportComparisonServiceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final LocalDate START = LocalDate.of(2026, 8, 1);
    private static final LocalDate END = LocalDate.of(2026, 8, 5);

    @Mock
    private ReportMapper reportMapper;

    private ReportService reportService;

    @BeforeEach
    void setUp() {
        reportService = new ReportService(reportMapper);
    }

    @Test
    void getComparison_group_sumsLiveSpendingForEveryoneAndRanksMyCategories() {
        when(reportMapper.findReportById(100L)).thenReturn(ownedReport(null));
        when(reportMapper.findComparisonMember(1L)).thenReturn(member(1L, "Me", "KR"));
        when(reportMapper.findComparisonPeerMembers(7L, 1L, START, END)).thenReturn(List.of(
            member(2L, "Mina", "KR"),
            member(3L, "Jae", null)
        ));
        when(reportMapper.findComparisonSpending(List.of(1L, 2L, 3L), START, END))
            .thenReturn(List.of(
                spending(1L, "FOOD", "30000.0000"),
                spending(1L, "SHOPPING", "10000.0000"),
                spending(2L, "FOOD", "50000.0000"),
                spending(3L, "", "5000.0000")
            ));

        ReportComparisonResponse response = reportService.getComparison(
            1L, 100L, ReportComparisonScope.GROUP
        );

        assertEquals(ReportComparisonScope.GROUP, response.getScope());
        assertEquals(ReportComparisonBasis.LIVE, response.getBasis());
        assertEquals(new BigDecimal("40000.0000"), response.getMe().getTotalSpent());
        assertEquals(new BigDecimal("8000.00"), response.getMe().getDailyAverage());
        assertEquals("FOOD", response.getMe().getCategoryBreakdown().get(0).getCategory());
        assertEquals(
            new BigDecimal("75.00"),
            response.getMe().getCategoryBreakdown().get(0).getPercentage()
        );

        assertEquals(2, response.getPeers().size());
        assertEquals("Mina", response.getPeers().get(0).getDisplayName());
        assertEquals(new BigDecimal("50000.0000"), response.getPeers().get(0).getTotalSpent());
        // 빈 카테고리는 OTHER로 접는다 — 스냅샷과 같은 규칙.
        assertEquals("OTHER", response.getPeers().get(1).getCategoryBreakdown().get(0).getCategory());

        assertEquals(2, response.getCohort().getSize());
        assertEquals(new BigDecimal("27500.00"), response.getCohort().getAvgTotalSpent());
        assertEquals(new BigDecimal("5500.00"), response.getCohort().getAvgDailyAverage());
        assertEquals("FOOD", response.getCohort().getCategoryBreakdown().get(0).getCategory());
        assertEquals(
            new BigDecimal("25000.00"),
            response.getCohort().getCategoryBreakdown().get(0).getAmount()
        );

        List<ReportComparisonRankResponse> ranks = response.getRanks();
        assertEquals(List.of("FOOD", "SHOPPING"), ranks.stream()
            .map(ReportComparisonRankResponse::getCategory).toList());
        assertEquals(2, ranks.get(0).getRank());
        assertEquals(3, ranks.get(0).getOf());
        assertEquals(1, ranks.get(1).getRank());
    }

    @Test
    void getComparison_group_withoutPeersReturnsEmptyCohortAndNoRanks() {
        when(reportMapper.findReportById(100L)).thenReturn(ownedReport(null));
        when(reportMapper.findComparisonMember(1L)).thenReturn(member(1L, "Me", null));
        when(reportMapper.findComparisonPeerMembers(7L, 1L, START, END)).thenReturn(List.of());
        when(reportMapper.findComparisonSpending(List.of(1L), START, END))
            .thenReturn(List.of(spending(1L, "STAY", "20000.0000")));

        ReportComparisonResponse response = reportService.getComparison(
            1L, 100L, ReportComparisonScope.GROUP
        );

        assertEquals(new BigDecimal("20000.0000"), response.getMe().getTotalSpent());
        assertTrue(response.getPeers().isEmpty());
        assertEquals(0, response.getCohort().getSize());
        assertEquals(BigDecimal.ZERO, response.getCohort().getAvgTotalSpent());
        assertTrue(response.getCohort().getCategoryBreakdown().isEmpty());
        assertTrue(response.getRanks().isEmpty());
    }

    @Test
    void getComparison_similar_averagesLatestSnapshotsAndSkipsLegacyOnes() throws Exception {
        when(reportMapper.findReportById(100L)).thenReturn(ownedReport(analytics(
            "1000.0000", "200.00", "FOOD", "800.0000", "OTHER", "200.0000"
        )));
        when(reportMapper.findComparisonMember(1L)).thenReturn(member(1L, "Me", "KR"));
        when(reportMapper.findSimilarCohortAnalytics("KR", 1L, 200)).thenReturn(List.of(
            snapshot(2L, analytics("400.0000", "100.00", "FOOD", "400.0000", null, null)),
            snapshot(3L, null),
            snapshot(4L, analytics("600.0000", "150.00", "SHOPPING", "600.0000", null, null))
        ));

        ReportComparisonResponse response = reportService.getComparison(
            1L, 100L, ReportComparisonScope.SIMILAR
        );

        assertEquals(ReportComparisonScope.SIMILAR, response.getScope());
        assertEquals(ReportComparisonBasis.SNAPSHOT, response.getBasis());
        // 스냅샷 숫자는 JSON에서 읽은 그대로라 자릿수가 다를 수 있다 — 값만 본다.
        assertEquals(0, new BigDecimal("1000").compareTo(response.getMe().getTotalSpent()));
        assertEquals(0, new BigDecimal("200").compareTo(response.getMe().getDailyAverage()));
        assertTrue(response.getPeers().isEmpty());

        assertEquals(2, response.getCohort().getSize());
        assertEquals(new BigDecimal("500.00"), response.getCohort().getAvgTotalSpent());
        assertEquals(new BigDecimal("125.00"), response.getCohort().getAvgDailyAverage());
        assertEquals(List.of("SHOPPING", "FOOD"), response.getCohort().getCategoryBreakdown()
            .stream().map(row -> row.getCategory()).toList());
        assertEquals(
            new BigDecimal("300.00"),
            response.getCohort().getCategoryBreakdown().get(0).getAmount()
        );

        assertEquals(List.of("FOOD", "OTHER"), response.getRanks().stream()
            .map(ReportComparisonRankResponse::getCategory).toList());
        assertEquals(1, response.getRanks().get(0).getRank());
        assertEquals(3, response.getRanks().get(0).getOf());
    }

    @Test
    void getComparison_similar_withoutNationalityDoesNotQueryAndReturnsEmptyCohort() {
        when(reportMapper.findReportById(100L)).thenReturn(ownedReport(null));
        when(reportMapper.findComparisonMember(1L)).thenReturn(member(1L, "Me", " "));

        ReportComparisonResponse response = reportService.getComparison(
            1L, 100L, ReportComparisonScope.SIMILAR
        );

        assertEquals(0, response.getCohort().getSize());
        assertTrue(response.getRanks().isEmpty());
        assertTrue(response.getPeers().isEmpty());
        verify(reportMapper, never()).findSimilarCohortAnalytics(any(), anyLong(), anyInt());
    }

    @Test
    void getComparison_rejectsAnotherMembersReport() {
        when(reportMapper.findReportById(100L)).thenReturn(ownedReport(null));

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> reportService.getComparison(2L, 100L, ReportComparisonScope.GROUP)
        );

        assertEquals(ReportErrorCode.REPORT_JOURNEY_FORBIDDEN, exception.getErrorCode());
        verify(reportMapper, never())
            .findComparisonPeerMembers(anyLong(), anyLong(), any(), any());
    }

    @Test
    void getComparison_missingReportAndMissingScopeAreRejected() {
        when(reportMapper.findReportById(404L)).thenReturn(null);

        assertEquals(
            ReportErrorCode.REPORT_NOT_FOUND,
            assertThrows(BusinessException.class, () ->
                reportService.getComparison(1L, 404L, ReportComparisonScope.GROUP)
            ).getErrorCode()
        );
        assertEquals(
            ReportErrorCode.INVALID_REPORT_INPUT,
            assertThrows(BusinessException.class, () ->
                reportService.getComparison(1L, 100L, null)
            ).getErrorCode()
        );
    }

    private static Report ownedReport(JsonNode analytics) {
        JsonNode content = null;
        if (analytics != null) {
            content = OBJECT_MAPPER.createObjectNode().set("analytics", analytics);
        }
        return Report.builder()
            .reportId(100L)
            .tripId(7L)
            .memberId(1L)
            .title("Seoul Foodie Week")
            .startDate(START)
            .endDate(END)
            .generationStatus("COMPLETED")
            .locale("en")
            .reportContent(content)
            .build();
    }

    private static ReportComparisonMember member(long id, String name, String nationality) {
        return ReportComparisonMember.builder()
            .memberId(id)
            .displayName(name)
            .nationalityCode(nationality)
            .build();
    }

    private static ReportComparisonSpending spending(long memberId, String category, String amount) {
        return ReportComparisonSpending.builder()
            .memberId(memberId)
            .category(category)
            .amount(new BigDecimal(amount))
            .build();
    }

    private static ReportCohortSnapshot snapshot(long memberId, JsonNode analytics) {
        return ReportCohortSnapshot.builder().memberId(memberId).analytics(analytics).build();
    }

    private static JsonNode analytics(
        String total, String daily,
        String firstCategory, String firstAmount,
        String secondCategory, String secondAmount
    ) throws Exception {
        StringBuilder breakdown = new StringBuilder();
        breakdown.append("{\"category\":\"").append(firstCategory)
            .append("\",\"amount\":").append(firstAmount).append(",\"percentage\":0}");
        if (secondCategory != null) {
            breakdown.append(",{\"category\":\"").append(secondCategory)
                .append("\",\"amount\":").append(secondAmount).append(",\"percentage\":0}");
        }
        return OBJECT_MAPPER.readTree(
            "{\"totalSpent\":" + total + ",\"dailyAverage\":" + daily
                + ",\"categoryBreakdown\":[" + breakdown + "],\"dailyTrend\":[]}"
        );
    }
}
