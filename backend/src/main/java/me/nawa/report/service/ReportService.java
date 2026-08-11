package me.nawa.report.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import me.nawa.common.exception.BusinessException;
import me.nawa.report.domain.Report;
import me.nawa.report.domain.ReportJourney;
import me.nawa.report.domain.ReportTimelineItem;
import me.nawa.report.domain.ReportExpense;
import me.nawa.report.dto.request.ReportCreateRequest;
import me.nawa.report.dto.response.ReportContentDayResponse;
import me.nawa.report.dto.response.ReportContentItemResponse;
import me.nawa.report.dto.response.ReportContentJourneyResponse;
import me.nawa.report.dto.response.ReportContentResponse;
import me.nawa.report.dto.response.ReportDetailResponse;
import me.nawa.report.dto.response.ReportSummaryResponse;
import me.nawa.report.dto.response.ReportAnalyticsResponse;
import me.nawa.report.dto.response.ReportCategoryBreakdownResponse;
import me.nawa.report.dto.response.ReportDailyTrendResponse;
import me.nawa.report.dto.response.ReportExpenseCandidateResponse;
import me.nawa.report.exception.ReportErrorCode;
import me.nawa.report.mapper.ReportMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReportService {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
    private static final Set<String> SUPPORTED_LOCALES = Set.of(
        "en",
        "ja",
        "zh-CN",
        "zh-TW",
        "vi"
    );
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final ReportMapper reportMapper;

    @Transactional
    public ReportDetailResponse createReport(
        Long memberId,
        Long tripId,
        ReportCreateRequest request
    ) {
        validateMemberId(memberId);
        validateResourceId(tripId);
        String locale = normalizeLocale(request);
        List<Long> transferIds = normalizeTransferIds(request);

        ReportJourney journey = reportMapper.findJourneyForUpdate(tripId);
        if (journey == null) {
            throw new BusinessException(
                ReportErrorCode.REPORT_JOURNEY_NOT_FOUND
            );
        }
        if (!memberId.equals(journey.getMemberId())) {
            throw new BusinessException(
                ReportErrorCode.REPORT_JOURNEY_FORBIDDEN
            );
        }
        if (journey.getEndDate() == null
            || !journey.getEndDate().isBefore(
                LocalDate.now(KOREA_ZONE)
            )) {
            throw new BusinessException(
                ReportErrorCode.JOURNEY_NOT_COMPLETED
            );
        }
        List<ReportExpense> expenses = findSelectedExpenses(
            memberId, tripId, transferIds
        );
        Report activeReport = reportMapper.findActiveReportByTripId(tripId);
        if (activeReport != null) {
            if (sameLedgerEntries(tripId, expenses)) {
                Report existing = reportMapper.findReportById(
                    activeReport.getReportId()
                );
                if (existing != null) {
                    return toDetailResponse(existing);
                }
            }
            throw new BusinessException(ReportErrorCode.REPORT_ALREADY_EXISTS);
        }

        for (ReportExpense expense : expenses) {
            Long linkedTripId = reportMapper.findLinkedTripIdByLedgerEntryId(
                expense.getLedgerEntryId()
            );
            if (linkedTripId != null) {
                throw new BusinessException(
                    ReportErrorCode.REPORT_EXPENSE_ALREADY_LINKED
                );
            }
        }

        List<ReportTimelineItem> timeline =
            reportMapper.findTimelineItemsByTripId(tripId);
        Report report = Report.builder()
            .tripId(tripId)
            .generationStatus("COMPLETED")
            .locale(locale)
            .reportContent(toSnapshot(journey, timeline, expenses))
            .build();
        for (ReportExpense expense : expenses) {
            reportMapper.insertTripExpenseLink(
                tripId, expense.getLedgerEntryId()
            );
        }
        reportMapper.insertReport(report);

        Report savedReport = reportMapper.findReportById(
            report.getReportId()
        );
        if (savedReport == null) {
            throw new IllegalStateException(
                "Inserted Report could not be read back"
            );
        }
        return toDetailResponse(savedReport);
    }

    @Transactional(readOnly = true)
    public List<ReportExpenseCandidateResponse> getExpenseCandidates(
        Long memberId,
        Long tripId
    ) {
        validateMemberId(memberId);
        validateResourceId(tripId);
        ReportJourney journey = reportMapper.findJourneyById(tripId);
        if (journey == null) {
            throw new BusinessException(ReportErrorCode.REPORT_JOURNEY_NOT_FOUND);
        }
        if (!memberId.equals(journey.getMemberId())) {
            throw new BusinessException(ReportErrorCode.REPORT_JOURNEY_FORBIDDEN);
        }
        return reportMapper.findExpenseCandidates(tripId, memberId).stream()
            .map(expense -> ReportExpenseCandidateResponse.builder()
                .transferId(expense.getTransferId())
                .amount(expense.getAmount())
                .occurredOn(expense.getOccurredOn())
                .category(normalizeCategory(expense.getCategory()))
                .memo(expense.getMemo())
                .selected(expense.isSelected())
                .build())
            .toList();
    }

    @Transactional(readOnly = true)
    public List<ReportSummaryResponse> getReports(Long memberId) {
        validateMemberId(memberId);

        List<Report> reports = reportMapper.findReportsByMemberId(memberId);
        if (reports == null || reports.isEmpty()) {
            return List.of();
        }
        return reports.stream()
            .map(this::toSummaryResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public ReportDetailResponse getReport(Long memberId, Long reportId) {
        validateMemberId(memberId);
        validateResourceId(reportId);

        Report report = reportMapper.findReportById(reportId);
        if (report == null) {
            throw new BusinessException(ReportErrorCode.REPORT_NOT_FOUND);
        }
        if (!memberId.equals(report.getMemberId())) {
            throw new BusinessException(
                ReportErrorCode.REPORT_JOURNEY_FORBIDDEN
            );
        }
        return toDetailResponse(report);
    }

    private JsonNode toSnapshot(
        ReportJourney journey,
        List<ReportTimelineItem> timeline,
        List<ReportExpense> expenses
    ) {
        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        root.putObject("journey")
            .put("tripId", journey.getTripId())
            .put("title", journey.getTitle())
            .put("startDate", journey.getStartDate().toString())
            .put("endDate", journey.getEndDate().toString());

        ArrayNode days = root.putArray("days");
        Map<LocalDate, ArrayNode> itemsByDate = new LinkedHashMap<>();
        if (timeline != null) {
            for (ReportTimelineItem item : timeline) {
                ArrayNode items = itemsByDate.computeIfAbsent(
                    item.getVisitDate(), visitDate -> {
                        ObjectNode day = days.addObject();
                        day.put("visitDate", visitDate.toString());
                        return day.putArray("items");
                    }
                );
                items.addObject().put("tripItemId", item.getTripItemId())
                    .put("itemId", item.getItemId()).put("itemType", item.getItemType())
                    .put("title", item.getTitle()).put("status", item.getStatus());
            }
        }
        putAnalytics(root, calculateAnalytics(journey, expenses));
        return root;
    }

    private ReportSummaryResponse toSummaryResponse(Report report) {
        return ReportSummaryResponse.builder()
            .reportId(report.getReportId())
            .tripId(report.getTripId())
            .title(report.getTitle())
            .startDate(report.getStartDate())
            .endDate(report.getEndDate())
            .generationStatus(report.getGenerationStatus())
            .locale(report.getLocale())
            .generatedAt(report.getGeneratedAt())
            .createdAt(report.getCreatedAt())
            .build();
    }

    private ReportDetailResponse toDetailResponse(Report report) {
        ReportSummaryResponse summary = toSummaryResponse(report);
        return ReportDetailResponse.builder()
            .reportId(summary.getReportId())
            .tripId(summary.getTripId())
            .title(summary.getTitle())
            .startDate(summary.getStartDate())
            .endDate(summary.getEndDate())
            .generationStatus(summary.getGenerationStatus())
            .locale(summary.getLocale())
            .generatedAt(summary.getGeneratedAt())
            .createdAt(summary.getCreatedAt())
            .reportContent(toContentResponse(report.getReportContent()))
            .build();
    }

    private ReportContentResponse toContentResponse(JsonNode content) {
        if (content == null || !content.isObject()) {
            throw new IllegalStateException("Report content is missing");
        }

        JsonNode journey = content.path("journey");
        ReportContentJourneyResponse journeyResponse =
            ReportContentJourneyResponse.builder()
                .tripId(journey.path("tripId").asLong())
                .title(journey.path("title").asText())
                .startDate(LocalDate.parse(journey.path("startDate").asText()))
                .endDate(LocalDate.parse(journey.path("endDate").asText()))
                .build();

        List<ReportContentDayResponse> days = new ArrayList<>();
        JsonNode dayNodes = content.path("days");
        if (dayNodes.isArray()) {
            for (JsonNode day : dayNodes) {
                List<ReportContentItemResponse> items = new ArrayList<>();
                JsonNode itemNodes = day.path("items");
                if (itemNodes.isArray()) {
                    for (JsonNode item : itemNodes) {
                        items.add(ReportContentItemResponse.builder()
                            .tripItemId(item.path("tripItemId").asLong())
                            .itemId(item.path("itemId").asLong())
                            .itemType(item.path("itemType").asText())
                            .title(item.path("title").asText())
                            .status(item.path("status").asText())
                            .build());
                    }
                }
                days.add(ReportContentDayResponse.builder()
                    .visitDate(LocalDate.parse(day.path("visitDate").asText()))
                    .items(List.copyOf(items))
                    .build());
            }
        }

        return ReportContentResponse.builder()
            .journey(journeyResponse)
            .days(List.copyOf(days))
            .analytics(toAnalyticsResponse(content.path("analytics")))
            .build();
    }

    private List<Long> normalizeTransferIds(ReportCreateRequest request) {
        if (request == null || request.getTransferIds() == null) {
            return List.of();
        }
        List<Long> transferIds = request.getTransferIds();
        if (transferIds.stream().anyMatch(id -> id == null || id <= 0)
            || new HashSet<>(transferIds).size() != transferIds.size()) {
            throw new BusinessException(ReportErrorCode.INVALID_REPORT_EXPENSE);
        }
        return List.copyOf(transferIds);
    }

    private List<ReportExpense> findSelectedExpenses(
        Long memberId, Long tripId, List<Long> transferIds
    ) {
        if (transferIds.isEmpty()) {
            return List.of();
        }
        List<ReportExpense> expenses = reportMapper.findEligibleExpensesForUpdate(
            tripId, memberId, transferIds
        );
        if (expenses.size() != transferIds.size()) {
            throw new BusinessException(ReportErrorCode.INVALID_REPORT_EXPENSE);
        }
        return expenses;
    }

    private boolean sameLedgerEntries(Long tripId, List<ReportExpense> expenses) {
        Set<Long> existing = new HashSet<>(
            reportMapper.findLinkedLedgerEntryIdsByTripId(tripId)
        );
        Set<Long> requested = expenses.stream()
            .map(ReportExpense::getLedgerEntryId).collect(java.util.stream.Collectors.toSet());
        return existing.equals(requested);
    }

    private ReportAnalyticsResponse calculateAnalytics(
        ReportJourney journey, List<ReportExpense> expenses
    ) {
        BigDecimal total = expenses.stream().map(ReportExpense::getAmount)
            .reduce(ZERO, BigDecimal::add);
        long days = java.time.temporal.ChronoUnit.DAYS.between(
            journey.getStartDate(), journey.getEndDate()
        ) + 1;
        Map<String, BigDecimal> categories = new LinkedHashMap<>();
        Map<LocalDate, BigDecimal> daily = new LinkedHashMap<>();
        for (LocalDate date = journey.getStartDate(); !date.isAfter(journey.getEndDate()); date = date.plusDays(1)) {
            daily.put(date, ZERO);
        }
        for (ReportExpense expense : expenses) {
            String category = normalizeCategory(expense.getCategory());
            categories.merge(category, expense.getAmount(), BigDecimal::add);
            daily.merge(expense.getOccurredOn(), expense.getAmount(), BigDecimal::add);
        }
        List<ReportCategoryBreakdownResponse> breakdown = categories.entrySet().stream()
            .map(entry -> ReportCategoryBreakdownResponse.builder()
                .category(entry.getKey()).amount(entry.getValue())
                .percentage(total.signum() == 0 ? ZERO : entry.getValue()
                    .multiply(BigDecimal.valueOf(100)).divide(total, 2, RoundingMode.HALF_UP))
                .build())
            .sorted(Comparator.comparing(ReportCategoryBreakdownResponse::getAmount).reversed()
                .thenComparing(ReportCategoryBreakdownResponse::getCategory))
            .toList();
        List<ReportDailyTrendResponse> trend = daily.entrySet().stream()
            .map(entry -> ReportDailyTrendResponse.builder().date(entry.getKey())
                .amount(entry.getValue()).build()).toList();
        return ReportAnalyticsResponse.builder().totalSpent(total)
            .dailyAverage(total.divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP))
            .categoryBreakdown(breakdown).dailyTrend(trend).build();
    }

    private void putAnalytics(
        ObjectNode root, ReportAnalyticsResponse analytics
    ) {
        ObjectNode analyticsNode = root.putObject("analytics");
        analyticsNode.put("totalSpent", analytics.getTotalSpent());
        analyticsNode.put("dailyAverage", analytics.getDailyAverage());
        ArrayNode categories = analyticsNode.putArray("categoryBreakdown");
        for (ReportCategoryBreakdownResponse category : analytics.getCategoryBreakdown()) {
            categories.addObject().put("category", category.getCategory())
                .put("amount", category.getAmount())
                .put("percentage", category.getPercentage());
        }
        ArrayNode trend = analyticsNode.putArray("dailyTrend");
        for (ReportDailyTrendResponse day : analytics.getDailyTrend()) {
            trend.addObject().put("date", day.getDate().toString())
                .put("amount", day.getAmount());
        }
    }

    private ReportAnalyticsResponse toAnalyticsResponse(JsonNode analytics) {
        if (analytics == null || !analytics.isObject()) {
            return null;
        }
        List<ReportCategoryBreakdownResponse> breakdown = new ArrayList<>();
        for (JsonNode category : analytics.path("categoryBreakdown")) {
            breakdown.add(ReportCategoryBreakdownResponse.builder()
                .category(category.path("category").asText())
                .amount(category.path("amount").decimalValue())
                .percentage(category.path("percentage").decimalValue())
                .build());
        }
        List<ReportDailyTrendResponse> trend = new ArrayList<>();
        for (JsonNode day : analytics.path("dailyTrend")) {
            trend.add(ReportDailyTrendResponse.builder()
                .date(LocalDate.parse(day.path("date").asText()))
                .amount(day.path("amount").decimalValue())
                .build());
        }
        return ReportAnalyticsResponse.builder()
            .totalSpent(analytics.path("totalSpent").decimalValue())
            .dailyAverage(analytics.path("dailyAverage").decimalValue())
            .categoryBreakdown(List.copyOf(breakdown))
            .dailyTrend(List.copyOf(trend))
            .build();
    }

    private String normalizeCategory(String category) {
        return category == null || category.isBlank() ? "OTHER" : category;
    }

    private String normalizeLocale(ReportCreateRequest request) {
        if (request == null || request.getLocale() == null) {
            return "en";
        }

        String locale = request.getLocale().trim();
        if (!SUPPORTED_LOCALES.contains(locale)) {
            throw new BusinessException(ReportErrorCode.INVALID_REPORT_INPUT);
        }
        return locale;
    }

    private void validateMemberId(Long memberId) {
        if (memberId == null || memberId <= 0) {
            throw new BusinessException(ReportErrorCode.INVALID_REPORT_INPUT);
        }
    }

    private void validateResourceId(Long resourceId) {
        if (resourceId == null || resourceId <= 0) {
            throw new BusinessException(ReportErrorCode.INVALID_REPORT_INPUT);
        }
    }
}
