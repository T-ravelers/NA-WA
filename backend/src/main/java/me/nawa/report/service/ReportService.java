package me.nawa.report.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
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
import me.nawa.report.domain.ReportCohortSnapshot;
import me.nawa.report.domain.ReportComparisonBasis;
import me.nawa.report.domain.ReportComparisonMember;
import me.nawa.report.domain.ReportComparisonScope;
import me.nawa.report.domain.ReportComparisonSpending;
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
import me.nawa.report.dto.response.ReportComparisonCohortResponse;
import me.nawa.report.dto.response.ReportComparisonMemberResponse;
import me.nawa.report.dto.response.ReportComparisonRankResponse;
import me.nawa.report.dto.response.ReportComparisonResponse;
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
        "zh-TW",
        "vi"
    );
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    /**
     * 같은 국적 코호트 상한. 앱으로 넘어오는 행 수만 자른다 — ROW_NUMBER()는 같은 국적의
     * 리포트를 모두 훑어 순위를 매긴 뒤이므로, 스캔 자체가 줄지는 않는다.
     */
    private static final int SIMILAR_COHORT_LIMIT = 200;

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
        List<ReportCategoryBreakdownResponse> breakdown = buildBreakdown(categories, total);
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

    /** 카테고리별 금액을 비중과 함께 정렬한다 — 금액 내림차순, 같으면 카테고리 오름차순. */
    private List<ReportCategoryBreakdownResponse> buildBreakdown(
        Map<String, BigDecimal> categories, BigDecimal total
    ) {
        return categories.entrySet().stream()
            .map(entry -> ReportCategoryBreakdownResponse.builder()
                .category(entry.getKey()).amount(entry.getValue())
                .percentage(total.signum() == 0 ? ZERO : entry.getValue()
                    .multiply(BigDecimal.valueOf(100)).divide(total, 2, RoundingMode.HALF_UP))
                .build())
            .sorted(Comparator.comparing(ReportCategoryBreakdownResponse::getAmount).reversed()
                .thenComparing(ReportCategoryBreakdownResponse::getCategory))
            .toList();
    }

    // ── 비교 (#398) ──────────────────────────────────────────────────────

    /**
     * 내 리포트를 같은 약속 동료(GROUP) 또는 같은 국적 회원(SIMILAR)과 비교한다.
     *
     * <p>숫자만 내린다. 차이의 부호와 문구는 프론트엔드가 비중으로 계산한다.</p>
     */
    @Transactional(readOnly = true)
    public ReportComparisonResponse getComparison(
        Long memberId,
        Long reportId,
        ReportComparisonScope scope
    ) {
        validateMemberId(memberId);
        validateResourceId(reportId);
        if (scope == null) {
            throw new BusinessException(ReportErrorCode.INVALID_REPORT_INPUT);
        }

        Report report = reportMapper.findReportById(reportId);
        if (report == null) {
            throw new BusinessException(ReportErrorCode.REPORT_NOT_FOUND);
        }
        if (!memberId.equals(report.getMemberId())) {
            throw new BusinessException(ReportErrorCode.REPORT_JOURNEY_FORBIDDEN);
        }
        ReportComparisonMember me = reportMapper.findComparisonMember(memberId);
        if (me == null) {
            throw new IllegalStateException("Report owner could not be read");
        }

        long days = ChronoUnit.DAYS.between(report.getStartDate(), report.getEndDate()) + 1;
        return scope == ReportComparisonScope.GROUP
            ? compareWithGroup(report, me, days)
            : compareWithSimilarTravelers(report, me, days);
    }

    /**
     * 같은 약속 동료와 비교. 나와 동료 모두 여정 기간의 결제를 지금 다시 합산한다(LIVE).
     * 저장된 스냅샷은 내가 고른 지출만 담고 있어 동료와 정의가 다르다 — 한쪽만 스냅샷을
     * 쓰면 같은 잣대가 아니다.
     */
    private ReportComparisonResponse compareWithGroup(
        Report report, ReportComparisonMember me, long days
    ) {
        List<ReportComparisonMember> peers = reportMapper.findComparisonPeerMembers(
            report.getTripId(), me.getMemberId()
        );
        List<Long> memberIds = new ArrayList<>();
        memberIds.add(me.getMemberId());
        peers.forEach(peer -> memberIds.add(peer.getMemberId()));

        Map<Long, Map<String, BigDecimal>> amountsByMember = new LinkedHashMap<>();
        for (ReportComparisonSpending row : reportMapper.findComparisonSpending(
            memberIds, report.getStartDate(), report.getEndDate()
        )) {
            amountsByMember.computeIfAbsent(row.getMemberId(), id -> new LinkedHashMap<>())
                .merge(normalizeCategory(row.getCategory()), row.getAmount(), BigDecimal::add);
        }

        Map<String, BigDecimal> myAmounts = amountsByMember.getOrDefault(me.getMemberId(), Map.of());
        List<Map<String, BigDecimal>> peerAmounts = new ArrayList<>();
        List<BigDecimal> peerDailyAverages = new ArrayList<>();
        List<ReportComparisonMemberResponse> peerResponses = new ArrayList<>();
        for (ReportComparisonMember peer : peers) {
            Map<String, BigDecimal> amounts = amountsByMember.getOrDefault(peer.getMemberId(), Map.of());
            ReportComparisonMemberResponse response = toComparisonMember(peer, amounts, days);
            peerAmounts.add(amounts);
            peerDailyAverages.add(response.getDailyAverage());
            peerResponses.add(response);
        }

        return ReportComparisonResponse.builder()
            .scope(ReportComparisonScope.GROUP)
            .basis(ReportComparisonBasis.LIVE)
            .me(toComparisonMember(me, myAmounts, days))
            .peers(List.copyOf(peerResponses))
            .cohort(toCohort(peerAmounts, peerDailyAverages))
            .ranks(rankCategories(myAmounts, peerAmounts))
            .build();
    }

    /**
     * 같은 국적 회원과 비교. 양쪽 다 저장된 스냅샷을 읽는다(SNAPSHOT) — 남의 지출을 다시
     * 합산하지 않고, 리포트를 만든 회원만 코호트에 들어간다. 동료 개인은 노출하지 않는다.
     */
    private ReportComparisonResponse compareWithSimilarTravelers(
        Report report, ReportComparisonMember me, long days
    ) {
        ReportAnalyticsResponse mine = toAnalyticsResponse(
            report.getReportContent() == null ? null : report.getReportContent().path("analytics")
        );
        Map<String, BigDecimal> myAmounts = amountsOf(mine);
        ReportComparisonMemberResponse meResponse = mine == null
            ? toComparisonMember(me, myAmounts, days)
            : ReportComparisonMemberResponse.builder()
                .memberId(me.getMemberId())
                .displayName(me.getDisplayName())
                .profileImageUrl(me.getProfileImageUrl())
                .totalSpent(mine.getTotalSpent())
                .dailyAverage(mine.getDailyAverage())
                .categoryBreakdown(mine.getCategoryBreakdown())
                .build();

        List<Map<String, BigDecimal>> cohortAmounts = new ArrayList<>();
        List<BigDecimal> cohortDailyAverages = new ArrayList<>();
        String nationality = me.getNationalityCode();
        if (nationality != null && !nationality.isBlank()) {
            for (ReportCohortSnapshot snapshot : reportMapper.findSimilarCohortAnalytics(
                nationality, me.getMemberId(), SIMILAR_COHORT_LIMIT
            )) {
                ReportAnalyticsResponse analytics = toAnalyticsResponse(snapshot.getAnalytics());
                if (analytics == null) {
                    continue;
                }
                cohortAmounts.add(amountsOf(analytics));
                cohortDailyAverages.add(analytics.getDailyAverage());
            }
        }

        return ReportComparisonResponse.builder()
            .scope(ReportComparisonScope.SIMILAR)
            .basis(ReportComparisonBasis.SNAPSHOT)
            .me(meResponse)
            .peers(List.of())
            .cohort(toCohort(cohortAmounts, cohortDailyAverages))
            .ranks(rankCategories(myAmounts, cohortAmounts))
            .build();
    }

    private ReportComparisonMemberResponse toComparisonMember(
        ReportComparisonMember member, Map<String, BigDecimal> amounts, long days
    ) {
        BigDecimal total = sumOf(amounts);
        return ReportComparisonMemberResponse.builder()
            .memberId(member.getMemberId())
            .displayName(member.getDisplayName())
            .profileImageUrl(member.getProfileImageUrl())
            .totalSpent(total)
            .dailyAverage(total.divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP))
            .categoryBreakdown(buildBreakdown(amounts, total))
            .build();
    }

    /** 코호트 평균. 인원이 0이면 0과 빈 목록이다 — 프론트가 빈 상태를 그린다. */
    private ReportComparisonCohortResponse toCohort(
        List<Map<String, BigDecimal>> amountsPerMember, List<BigDecimal> dailyAverages
    ) {
        int size = amountsPerMember.size();
        if (size == 0) {
            return ReportComparisonCohortResponse.builder()
                .size(0).avgTotalSpent(ZERO).avgDailyAverage(ZERO)
                .categoryBreakdown(List.of())
                .build();
        }
        BigDecimal divisor = BigDecimal.valueOf(size);
        Map<String, BigDecimal> summed = new LinkedHashMap<>();
        BigDecimal summedTotal = ZERO;
        for (Map<String, BigDecimal> amounts : amountsPerMember) {
            amounts.forEach((category, amount) -> summed.merge(category, amount, BigDecimal::add));
            summedTotal = summedTotal.add(sumOf(amounts));
        }
        Map<String, BigDecimal> averaged = new LinkedHashMap<>();
        summed.forEach((category, amount) ->
            averaged.put(category, amount.divide(divisor, 2, RoundingMode.HALF_UP)));
        BigDecimal avgTotal = summedTotal.divide(divisor, 2, RoundingMode.HALF_UP);
        BigDecimal avgDaily = dailyAverages.stream().reduce(ZERO, BigDecimal::add)
            .divide(divisor, 2, RoundingMode.HALF_UP);
        return ReportComparisonCohortResponse.builder()
            .size(size)
            .avgTotalSpent(avgTotal)
            .avgDailyAverage(avgDaily)
            .categoryBreakdown(buildBreakdown(averaged, avgTotal))
            .build();
    }

    /**
     * 내 카테고리마다 순위를 매긴다. 나보다 많이 쓴 사람 수 + 1이 순위이고, 비교할 사람이
     * 없으면 순위도 없다. 순서는 내 비중 순(buildBreakdown)이다.
     */
    private List<ReportComparisonRankResponse> rankCategories(
        Map<String, BigDecimal> myAmounts, List<Map<String, BigDecimal>> others
    ) {
        if (others.isEmpty()) {
            return List.of();
        }
        List<ReportComparisonRankResponse> ranks = new ArrayList<>();
        for (ReportCategoryBreakdownResponse mine : buildBreakdown(myAmounts, sumOf(myAmounts))) {
            long ahead = others.stream()
                .map(amounts -> amounts.getOrDefault(mine.getCategory(), ZERO))
                .filter(amount -> amount.compareTo(mine.getAmount()) > 0)
                .count();
            ranks.add(ReportComparisonRankResponse.builder()
                .category(mine.getCategory())
                .rank((int) ahead + 1)
                .of(others.size() + 1)
                .build());
        }
        return List.copyOf(ranks);
    }

    private Map<String, BigDecimal> amountsOf(ReportAnalyticsResponse analytics) {
        Map<String, BigDecimal> amounts = new LinkedHashMap<>();
        if (analytics == null || analytics.getCategoryBreakdown() == null) {
            return amounts;
        }
        for (ReportCategoryBreakdownResponse row : analytics.getCategoryBreakdown()) {
            amounts.merge(normalizeCategory(row.getCategory()), row.getAmount(), BigDecimal::add);
        }
        return amounts;
    }

    private BigDecimal sumOf(Map<String, BigDecimal> amounts) {
        return amounts.values().stream().reduce(ZERO, BigDecimal::add);
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
