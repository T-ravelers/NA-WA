package me.nawa.report.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import me.nawa.config.MySqlSchemaExtension;
import me.nawa.journey.mapper.JourneyMapper;
import me.nawa.report.domain.ReportComparisonSpending;
import me.nawa.report.domain.ReportExpense;
import me.nawa.report.dto.request.ReportCreateRequest;
import me.nawa.report.dto.response.ReportDetailResponse;
import me.nawa.report.mapper.ReportMapper;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 지출이 결제액이 아니라 정산으로 회수하고 남은 순액인지 실제 MySQL에서 확인한다(#543).
 *
 * mapper를 모킹하면 검증할 수 없다 — 상계는 전부 SQL 안에 있고, 서비스는 넘어온 금액을
 * 더하기만 한다. 픽스처는 ReportComparisonIntegrationTest와 같은 방식의 인라인 INSERT이고,
 * 끝나면 역순으로 지운다.
 */
@ExtendWith(MySqlSchemaExtension.class)
@EnabledIfEnvironmentVariable(named = "RUN_MYSQL_INTEGRATION_TESTS", matches = "(?i)true")
class ReportSettlementNettingIntegrationTest {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private static HikariDataSource dataSource;
    private static JdbcTemplate jdbcTemplate;
    private static ReportMapper reportMapper;
    private static JourneyMapper journeyMapper;
    private static ReportService reportService;

    @BeforeAll
    static void setUpDatabase() throws Exception {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setDriverClassName(requiredEnvironment("DATABASE_DRIVER"));
        hikariConfig.setJdbcUrl(requiredEnvironment("DATABASE_URL"));
        hikariConfig.setUsername(requiredEnvironment("DATABASE_USERNAME"));
        hikariConfig.setPassword(requiredEnvironment("DATABASE_PASSWORD"));
        hikariConfig.setMaximumPoolSize(2);
        hikariConfig.setMinimumIdle(0);
        dataSource = new HikariDataSource(hikariConfig);
        jdbcTemplate = new JdbcTemplate(dataSource);

        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setConfigLocation(new ClassPathResource("mybatis-config.xml"));
        factoryBean.setMapperLocations(
            new ClassPathResource("me/nawa/report/mapper/ReportMapper.xml"),
            new ClassPathResource("me/nawa/journey/mapper/JourneyMapper.xml")
        );
        SqlSessionFactory sqlSessionFactory = factoryBean.getObject();
        if (!sqlSessionFactory.getConfiguration().hasMapper(ReportMapper.class)) {
            sqlSessionFactory.getConfiguration().addMapper(ReportMapper.class);
        }
        if (!sqlSessionFactory.getConfiguration().hasMapper(JourneyMapper.class)) {
            sqlSessionFactory.getConfiguration().addMapper(JourneyMapper.class);
        }
        SqlSessionTemplate sessionTemplate = new SqlSessionTemplate(sqlSessionFactory);
        reportMapper = sessionTemplate.getMapper(ReportMapper.class);
        journeyMapper = sessionTemplate.getMapper(JourneyMapper.class);
        reportService = new ReportService(reportMapper);
    }

    @AfterAll
    static void closeDatabase() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    /**
     * 5만 원을 결제하고 5명이 1만 원씩 나눠 그중 둘이 냈다. 결제자의 지출은 결제액 5만 원도,
     * 다 받았을 때의 1만 원도 아닌 3만 원이다 — 받지 못한 두 몫은 아직 결제자가 지고 있다.
     */
    @Test
    void expensesSubtractOnlyTheSettlementSharesThatWereActuallyPaid() {
        Deque<Runnable> cleanup = new ArrayDeque<>();
        try {
            LocalDate endDate = LocalDate.now(KOREA_ZONE).minusDays(1);
            LocalDate startDate = endDate.minusDays(3);
            LocalDateTime paymentAt = endDate.atTime(12, 0);
            LocalDateTime settlementPaidAt = endDate.plusDays(1).atTime(12, 0);

            long payer = insertMember("Netting payer", cleanup);
            long paidOne = insertMember("Netting paid one", cleanup);
            long paidTwo = insertMember("Netting paid two", cleanup);
            long unpaidOne = insertMember("Netting unpaid one", cleanup);
            long unpaidTwo = insertMember("Netting unpaid two", cleanup);

            long payerWallet = insertWallet(payer, cleanup);
            long paidOneWallet = insertWallet(paidOne, cleanup);

            long payerTrip = insertTrip(payer, startDate, endDate, cleanup);
            long paidOneTrip = insertTrip(paidOne, startDate, endDate, cleanup);

            long item = insertApprovedEventItem(payer, cleanup);
            long appointment = insertAppointment(item, payer, endDate, cleanup);
            long payerMembership = insertActiveMembership(appointment, payer, payerTrip, cleanup);
            long paidOneMembership =
                insertActiveMembership(appointment, paidOne, paidOneTrip, cleanup);
            long paidTwoMembership = insertActiveMembership(appointment, paidTwo, null, cleanup);
            long unpaidOneMembership = insertActiveMembership(appointment, unpaidOne, null, cleanup);
            long unpaidTwoMembership = insertActiveMembership(appointment, unpaidTwo, null, cleanup);

            long payment = insertTransferWithDebit(
                payer, payerWallet, "QR_PAYMENT", "FOOD", "50000.0000", paymentAt, cleanup
            );
            long settlement = insertSettlement(
                appointment, payer, payment, "50000.0000", "10000.0000", "40000.0000", cleanup
            );

            // 원결제자 본인 몫은 만들 때부터 NOT_REQUESTED다 — PAID로 넘어갈 수 없으므로
            // 회수액에 절대 섞이지 않는다(EqualSettlementCreator).
            insertSettlementMember(
                settlement, payerMembership, "10000.0000", "NOT_REQUESTED", null, cleanup
            );
            long paidOneTransfer = insertTransferWithDebit(
                paidOne, paidOneWallet, "SETTLEMENT", null, "10000.0000", settlementPaidAt,
                cleanup
            );
            insertSettlementMember(
                settlement, paidOneMembership, "10000.0000", "PAID", paidOneTransfer, cleanup
            );
            long paidTwoWallet = insertWallet(paidTwo, cleanup);
            long paidTwoTransfer = insertTransferWithDebit(
                paidTwo, paidTwoWallet, "SETTLEMENT", null, "10000.0000", settlementPaidAt,
                cleanup
            );
            insertSettlementMember(
                settlement, paidTwoMembership, "10000.0000", "PAID", paidTwoTransfer, cleanup
            );
            insertSettlementMember(
                settlement, unpaidOneMembership, "10000.0000", "PENDING", null, cleanup
            );
            insertSettlementMember(
                settlement, unpaidTwoMembership, "10000.0000", "PENDING", null, cleanup
            );

            List<ReportExpense> payerCandidates =
                reportMapper.findExpenseCandidates(payerTrip, payer);
            assertEquals(1, payerCandidates.size());
            assertEquals(
                new BigDecimal("30000.0000"), payerCandidates.get(0).getAmount(),
                "결제액 50000에서 실제로 받은 20000만 빠져야 한다"
            );
            assertEquals("FOOD", payerCandidates.get(0).getCategory());
            assertEquals(
                endDate, payerCandidates.get(0).getOccurredOn(),
                "상계해도 날짜는 원 결제일 그대로여야 한다"
            );

            ReportCreateRequest request = new ReportCreateRequest();
            request.setTransferIds(List.of(payment));
            ReportDetailResponse report = reportService.createReport(payer, payerTrip, request);
            cleanupReport(payerTrip, cleanup);
            // 스냅샷은 JSON을 거치며 뒤쪽 0이 깎이므로(30000.0000 → 30000.0) 자릿수가 아니라
            // 값으로 비교한다.
            assertEquals(
                0,
                new BigDecimal("30000.0000").compareTo(
                    report.getReportContent().getAnalytics().getTotalSpent()
                )
            );
            assertEquals(
                0,
                new BigDecimal("30000.0000").compareTo(
                    report.getReportContent().getAnalytics()
                        .getCategoryBreakdown().get(0).getAmount()
                )
            );

            // 정산은 여정 종료 다음 날 지급됐다. 실제 지급일로 자르면 결제자에게서 빠진
            // 1만 원이 지급자 지출에는 더해지지 않아 사라진다. 원 결제의 날짜와 카테고리에
            // 귀속해야 합계가 보존된다.
            List<ReportExpense> paidOneCandidates =
                reportMapper.findExpenseCandidates(paidOneTrip, paidOne);
            assertEquals(1, paidOneCandidates.size());
            assertEquals(new BigDecimal("10000.0000"), paidOneCandidates.get(0).getAmount());
            assertEquals("FOOD", paidOneCandidates.get(0).getCategory());
            assertEquals(endDate, paidOneCandidates.get(0).getOccurredOn());

            ReportCreateRequest paidOneRequest = new ReportCreateRequest();
            paidOneRequest.setTransferIds(List.of(paidOneTransfer));
            ReportDetailResponse paidOneReport = reportService.createReport(
                paidOne, paidOneTrip, paidOneRequest
            );
            cleanupReport(paidOneTrip, cleanup);
            assertEquals(
                0,
                new BigDecimal("10000.0000").compareTo(
                    paidOneReport.getReportContent().getAnalytics().getTotalSpent()
                ),
                "종료 뒤 지급한 사람의 리포트에서도 분담액이 사라지면 안 된다"
            );
            assertEquals(
                "FOOD",
                paidOneReport.getReportContent().getAnalytics()
                    .getCategoryBreakdown().get(0).getCategory()
            );

            // 여정 상세의 「쓴 금액」도 같은 정의다. 여기서만 빼지 않으면 같은 여정에서
            // 상세 화면 50000과 리포트 30000이 갈리고, 예산 잔액도 실제보다 적게 나온다.
            assertEquals(
                0,
                new BigDecimal("30000.0000").compareTo(
                    journeyMapper.findCurrentSpentAmount(payerTrip, payer)
                )
            );
            assertEquals(
                0,
                new BigDecimal("10000.0000").compareTo(
                    journeyMapper.findCurrentSpentAmount(paidOneTrip, paidOne)
                ),
                "종료 뒤 지급한 정산도 원 결제일 기준으로 여정 지출에 포함돼야 한다"
            );

            // 비교 쿼리도 같은 정의여야 상세 화면 총액과 갈리지 않는다.
            List<ReportComparisonSpending> spending = reportMapper.findComparisonSpending(
                List.of(payer, paidOne), startDate, endDate
            );
            assertEquals(new BigDecimal("30000.0000"), amountOf(spending, payer, "FOOD"));
            assertEquals(new BigDecimal("10000.0000"), amountOf(spending, paidOne, "FOOD"));
        } finally {
            while (!cleanup.isEmpty()) {
                cleanup.pop().run();
            }
        }
    }

    private static BigDecimal amountOf(
        List<ReportComparisonSpending> spending, long memberId, String category
    ) {
        return spending.stream()
            .filter(row -> row.getMemberId() == memberId && category.equals(row.getCategory()))
            .map(ReportComparisonSpending::getAmount)
            .findFirst()
            .orElseThrow(() -> new AssertionError(
                "회원 " + memberId + "의 " + category + " 지출이 없다"
            ));
    }

    private static void cleanupReport(long tripId, Deque<Runnable> cleanup) {
        cleanup.push(() -> jdbcTemplate.update("DELETE FROM reports WHERE trip_id = ?", tripId));
        cleanup.push(() -> jdbcTemplate.update(
            "DELETE FROM trip_expense_links WHERE trip_id = ?", tripId
        ));
    }

    private static long insertMember(String name, Deque<Runnable> cleanup) {
        jdbcTemplate.update(
            "INSERT INTO members (display_name, nationality_code) VALUES (?, 'KR')", name
        );
        long id = lastInsertId();
        cleanup.push(() -> jdbcTemplate.update("DELETE FROM members WHERE member_id = ?", id));
        return id;
    }

    private static long insertWallet(long memberId, Deque<Runnable> cleanup) {
        jdbcTemplate.update(
            "INSERT INTO wallet_owners (member_id, owner_type) VALUES (?, 'MEMBER')", memberId
        );
        long ownerId = lastInsertId();
        cleanup.push(() -> jdbcTemplate.update(
            "DELETE FROM wallet_owners WHERE wallet_owner_id = ?", ownerId
        ));
        jdbcTemplate.update(
            "INSERT INTO wallets (wallet_owner_id, currency_code, available_balance) "
                + "VALUES (?, 'KRW', 100000.0000)", ownerId
        );
        long walletId = lastInsertId();
        cleanup.push(() -> jdbcTemplate.update("DELETE FROM wallets WHERE wallet_id = ?", walletId));
        return walletId;
    }

    /** chk_explore_items_review — APPROVED는 검수자·검수 시각이 필수다. */
    private static long insertApprovedEventItem(long reviewerId, Deque<Runnable> cleanup) {
        jdbcTemplate.update(
            "INSERT INTO explore_items (item_type, approval_status, visibility_status, "
                + "reviewed_by, reviewed_at) VALUES ('EVENT', 'APPROVED', 'VISIBLE', ?, "
                + "CURRENT_TIMESTAMP)", reviewerId
        );
        long id = lastInsertId();
        cleanup.push(() -> jdbcTemplate.update("DELETE FROM explore_items WHERE item_id = ?", id));
        return id;
    }

    private static long insertTrip(
        long memberId, LocalDate startDate, LocalDate endDate, Deque<Runnable> cleanup
    ) {
        jdbcTemplate.update(
            "INSERT INTO trips (member_id, title, start_date, end_date) VALUES (?, ?, ?, ?)",
            memberId, "Netting journey", startDate, endDate
        );
        long id = lastInsertId();
        cleanup.push(() -> jdbcTemplate.update("DELETE FROM trips WHERE trip_id = ?", id));
        return id;
    }

    private static long insertAppointment(
        long itemId, long hostMemberId, LocalDate activityDate, Deque<Runnable> cleanup
    ) {
        LocalDateTime start = activityDate.atTime(10, 0);
        jdbcTemplate.update(
            "INSERT INTO appointments (item_id, host_member_id, language_code, appointment_name, "
                + "max_members, deposit_amount, appointment_status, activity_start_at, "
                + "activity_end_at) VALUES (?, ?, 'en', 'Netting appointment', 6, 10000, "
                + "'COMPLETED', ?, ?)",
            itemId, hostMemberId, start, start.plusHours(2)
        );
        long id = lastInsertId();
        cleanup.push(() -> jdbcTemplate.update(
            "DELETE FROM appointments WHERE appointment_id = ?", id
        ));
        return id;
    }

    private static long insertActiveMembership(
        long appointmentId, long memberId, Long tripId, Deque<Runnable> cleanup
    ) {
        jdbcTemplate.update(
            "INSERT INTO appointment_members (appointment_id, member_id, trip_id, "
                + "membership_status) VALUES (?, ?, ?, 'ACTIVE')",
            appointmentId, memberId, tripId
        );
        long id = lastInsertId();
        cleanup.push(() -> jdbcTemplate.update(
            "DELETE FROM appointment_members WHERE appointment_member_id = ?", id
        ));
        return id;
    }

    private static long insertTransferWithDebit(
        long memberId, long walletId, String transferType, String category, String amount,
        LocalDateTime paidAt, Deque<Runnable> cleanup
    ) {
        jdbcTemplate.update(
            "INSERT INTO wallet_transfers (currency_code, initiator_member_id, transfer_number, "
                + "transfer_type, transfer_status, amount, spending_category, memo, completed_at) "
                + "VALUES ('KRW', ?, ?, ?, 'COMPLETED', ?, ?, 'Netting expense', ?)",
            memberId, "NET-" + UUID.randomUUID().toString().replace("-", ""),
            transferType, new BigDecimal(amount), category, paidAt
        );
        long transferId = lastInsertId();
        cleanup.push(() -> jdbcTemplate.update(
            "DELETE FROM wallet_transfers WHERE transfer_id = ?", transferId
        ));
        jdbcTemplate.update(
            "INSERT INTO wallet_ledger_entries (transfer_id, wallet_id, entry_type, amount, "
                + "balance_after) VALUES (?, ?, 'DEBIT', ?, 50000.0000)",
            transferId, walletId, new BigDecimal(amount)
        );
        long entryId = lastInsertId();
        cleanup.push(() -> jdbcTemplate.update(
            "DELETE FROM wallet_ledger_entries WHERE ledger_entry_id = ?", entryId
        ));
        return transferId;
    }

    /** chk_settlements_creator_is_payer — 만든 사람과 결제자가 같아야 한다. */
    private static long insertSettlement(
        long appointmentId, long payerMemberId, long sourceTransferId, String total,
        String payerShare, String receivable, Deque<Runnable> cleanup
    ) {
        jdbcTemplate.update(
            "INSERT INTO settlements (appointment_id, created_by_member_id, payer_member_id, "
                + "source_transfer_id, idempotency_key, request_fingerprint, settlement_status, "
                + "split_method, total_amount, payer_share_amount, receivable_amount) "
                + "VALUES (?, ?, ?, ?, ?, SHA2(?, 256), 'REQUESTED', 'EQUAL', ?, ?, ?)",
            appointmentId, payerMemberId, payerMemberId, sourceTransferId,
            "netting-" + UUID.randomUUID(), "netting-" + sourceTransferId,
            new BigDecimal(total), new BigDecimal(payerShare), new BigDecimal(receivable)
        );
        long id = lastInsertId();
        cleanup.push(() -> jdbcTemplate.update(
            "DELETE FROM settlements WHERE settlement_id = ?", id
        ));
        return id;
    }

    /** chk_settlement_members_paid — PAID는 이체와 지급 시각이 함께 있어야 한다. */
    private static void insertSettlementMember(
        long settlementId, long appointmentMemberId, String shareAmount, String requestStatus,
        Long paidTransferId, Deque<Runnable> cleanup
    ) {
        jdbcTemplate.update(
            "INSERT INTO settlement_members (settlement_id, appointment_member_id, share_amount, "
                + "request_status, paid_transfer_id, paid_at) VALUES (?, ?, ?, ?, ?, ?)",
            settlementId, appointmentMemberId, new BigDecimal(shareAmount), requestStatus,
            paidTransferId, paidTransferId == null ? null : LocalDateTime.now(KOREA_ZONE)
        );
        long id = lastInsertId();
        cleanup.push(() -> jdbcTemplate.update(
            "DELETE FROM settlement_members WHERE settlement_member_id = ?", id
        ));
    }

    private static long lastInsertId() {
        Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        if (id == null) {
            throw new IllegalStateException("LAST_INSERT_ID() returned null");
        }
        return id;
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required");
        }
        return value;
    }
}
