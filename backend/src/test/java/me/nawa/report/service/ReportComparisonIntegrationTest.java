package me.nawa.report.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import me.nawa.config.MySqlSchemaExtension;
import me.nawa.report.domain.ReportComparisonBasis;
import me.nawa.report.domain.ReportComparisonScope;
import me.nawa.report.dto.response.ReportComparisonResponse;
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
 * 비교 쿼리(#398)를 실제 MySQL에서 돌린다 — 여정↔약속↔동료↔결제의 4단 조인은 mapper 모킹으로
 * 검증할 수 없다. 픽스처는 AppointmentMapperIntegrationTest·ReportConcurrencyIntegrationTest와 같은
 * 방식의 인라인 INSERT이고, 끝나면 역순으로 지운다.
 */
@ExtendWith(MySqlSchemaExtension.class)
@EnabledIfEnvironmentVariable(named = "RUN_MYSQL_INTEGRATION_TESTS", matches = "(?i)true")
class ReportComparisonIntegrationTest {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private static HikariDataSource dataSource;
    private static JdbcTemplate jdbcTemplate;
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
        factoryBean.setMapperLocations(new ClassPathResource(
            "me/nawa/report/mapper/ReportMapper.xml"
        ));
        SqlSessionFactory sqlSessionFactory = factoryBean.getObject();
        if (!sqlSessionFactory.getConfiguration().hasMapper(ReportMapper.class)) {
            sqlSessionFactory.getConfiguration().addMapper(ReportMapper.class);
        }
        reportService = new ReportService(
            new SqlSessionTemplate(sqlSessionFactory).getMapper(ReportMapper.class)
        );
    }

    @AfterAll
    static void closeDatabase() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Test
    void getComparison_findsPeersForHostAndParticipantAndSkipsCancelledAppointments() {
        Deque<Runnable> cleanup = new ArrayDeque<>();
        try {
            LocalDate endDate = LocalDate.now(KOREA_ZONE).minusDays(1);
            LocalDate startDate = endDate.minusDays(3);
            LocalDateTime paidAt = endDate.atTime(12, 0);

            long host = insertMember("Comparison host", "KR", cleanup);
            long peer = insertMember("Comparison peer", "KR", cleanup);
            long hostWallet = insertWallet(host, cleanup);
            long peerWallet = insertWallet(peer, cleanup);

            long item = insertApprovedEventItem(host, cleanup);
            long hostTrip = insertTrip(host, startDate, endDate, cleanup);
            long appointment = insertAppointment(item, host, endDate, "COMPLETED", cleanup);
            insertActiveMembership(appointment, host, hostTrip, cleanup);
            // #407부터 참여자도 자기 여정을 고르므로 appointment_members.trip_id가 채워진다.
            long peerTrip = insertTrip(peer, startDate, endDate, cleanup);
            insertActiveMembership(appointment, peer, peerTrip, cleanup);
            insertConfirmedTripItem(hostTrip, item, appointment, endDate, cleanup);

            // 기간이 겹치는 호스트의 다른 여정에 묶인 약속 동료는 이 여정 리포트에 섞이지 않아야 한다.
            // 활동일로 근사하던 갈래(#415에서 제거)가 있으면 여기서 bystander가 섞인다.
            long bystander = insertMember("Comparison bystander", "KR", cleanup);
            long otherTrip = insertTrip(host, startDate, endDate, cleanup);
            long otherItem = insertApprovedEventItem(host, cleanup);
            long otherAppointment = insertAppointment(otherItem, host, endDate, "COMPLETED", cleanup);
            insertActiveMembership(otherAppointment, host, otherTrip, cleanup);
            insertActiveMembership(otherAppointment, bystander, null, cleanup);
            insertConfirmedTripItem(otherTrip, otherItem, otherAppointment, endDate, cleanup);

            // 취소된 약속의 참가자는 어느 쪽 리포트에도 나오지 않아야 한다.
            long stranger = insertMember("Comparison stranger", "JP", cleanup);
            long cancelled = insertAppointment(item, host, endDate, "CANCELLED", cleanup);
            insertActiveMembership(cancelled, host, hostTrip, cleanup);
            insertActiveMembership(cancelled, stranger, null, cleanup);

            insertPaidExpense(host, hostWallet, "FOOD", "12000.0000", paidAt, cleanup);
            insertPaidExpense(peer, peerWallet, "SHOPPING", "8000.0000", paidAt, cleanup);
            insertPaidExpense(peer, peerWallet, "FOOD", "4000.0000", paidAt, cleanup);

            long hostReport = insertCompletedReport(hostTrip, "12000.0000", "3000.00",
                "FOOD", "12000.0000", cleanup);
            long peerReport = insertCompletedReport(
                peerTrip, "5000.0000", "1250.00", "FOOD", "5000.0000", cleanup
            );

            ReportComparisonResponse group = reportService.getComparison(
                host, hostReport, ReportComparisonScope.GROUP
            );
            assertEquals(ReportComparisonBasis.LIVE, group.getBasis());
            assertEquals(new BigDecimal("12000.0000"), group.getMe().getTotalSpent());
            assertEquals(1, group.getPeers().size());
            assertEquals(peer, group.getPeers().get(0).getMemberId());
            assertTrue(group.getPeers().stream()
                .noneMatch(member -> member.getMemberId().equals(bystander)));
            assertEquals(new BigDecimal("12000.0000"), group.getPeers().get(0).getTotalSpent());
            assertEquals("SHOPPING",
                group.getPeers().get(0).getCategoryBreakdown().get(0).getCategory());
            assertEquals(1, group.getCohort().getSize());
            assertEquals("FOOD", group.getRanks().get(0).getCategory());
            assertEquals(1, group.getRanks().get(0).getRank());
            assertEquals(2, group.getRanks().get(0).getOf());

            // 참가자 쪽에서도 방장이 동료로 잡힌다 — appointment_members.trip_id 갈래.
            ReportComparisonResponse peerGroup = reportService.getComparison(
                peer, peerReport, ReportComparisonScope.GROUP
            );
            assertEquals(1, peerGroup.getPeers().size());
            assertEquals(host, peerGroup.getPeers().get(0).getMemberId());
            assertEquals(new BigDecimal("12000.0000"), peerGroup.getMe().getTotalSpent());

            ReportComparisonResponse similar = reportService.getComparison(
                host, hostReport, ReportComparisonScope.SIMILAR
            );
            assertEquals(ReportComparisonBasis.SNAPSHOT, similar.getBasis());
            assertTrue(similar.getPeers().isEmpty());
            assertEquals(1, similar.getCohort().getSize());
            assertEquals(new BigDecimal("5000.00"), similar.getCohort().getAvgTotalSpent());
            assertEquals(new BigDecimal("1250.00"), similar.getCohort().getAvgDailyAverage());
            assertEquals(1, similar.getRanks().get(0).getRank());
            assertEquals(2, similar.getRanks().get(0).getOf());
        } finally {
            while (!cleanup.isEmpty()) {
                cleanup.pop().run();
            }
        }
    }

    private static long insertMember(String name, String nationality, Deque<Runnable> cleanup) {
        jdbcTemplate.update(
            "INSERT INTO members (display_name, nationality_code) VALUES (?, ?)", name, nationality
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
            memberId, "Comparison journey", startDate, endDate
        );
        long id = lastInsertId();
        cleanup.push(() -> jdbcTemplate.update("DELETE FROM trips WHERE trip_id = ?", id));
        return id;
    }

    private static long insertAppointment(
        long itemId, long hostMemberId, LocalDate activityDate, String status,
        Deque<Runnable> cleanup
    ) {
        LocalDateTime start = activityDate.atTime(10, 0);
        jdbcTemplate.update(
            "INSERT INTO appointments (item_id, host_member_id, language_code, appointment_name, "
                + "max_members, deposit_amount, appointment_status, activity_start_at, "
                + "activity_end_at) VALUES (?, ?, 'en', 'Comparison appointment', 5, 10000, "
                + "?, ?, ?)",
            itemId, hostMemberId, status, start, start.plusHours(2)
        );
        long id = lastInsertId();
        cleanup.push(() -> jdbcTemplate.update(
            "DELETE FROM appointments WHERE appointment_id = ?", id
        ));
        return id;
    }

    private static void insertActiveMembership(
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
    }

    private static void insertConfirmedTripItem(
        long tripId, long itemId, long appointmentId, LocalDate visitDate, Deque<Runnable> cleanup
    ) {
        jdbcTemplate.update(
            "INSERT INTO trip_items (trip_id, item_id, appointment_id, visit_date, "
                + "trip_item_status, confirmed_at) VALUES (?, ?, ?, ?, 'CONFIRMED', "
                + "CURRENT_TIMESTAMP)",
            tripId, itemId, appointmentId, visitDate
        );
        long id = lastInsertId();
        cleanup.push(() -> jdbcTemplate.update(
            "DELETE FROM trip_items WHERE trip_item_id = ?", id
        ));
    }

    private static void insertPaidExpense(
        long memberId, long walletId, String category, String amount,
        LocalDateTime paidAt, Deque<Runnable> cleanup
    ) {
        jdbcTemplate.update(
            "INSERT INTO wallet_transfers (currency_code, initiator_member_id, transfer_number, "
                + "transfer_type, transfer_status, amount, spending_category, memo, completed_at) "
                + "VALUES ('KRW', ?, ?, 'QR_PAYMENT', 'COMPLETED', ?, ?, 'Comparison expense', ?)",
            memberId, "CMP-" + UUID.randomUUID().toString().replace("-", ""),
            new BigDecimal(amount), category, paidAt
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
    }

    private static long insertCompletedReport(
        long tripId, String total, String daily, String category, String amount,
        Deque<Runnable> cleanup
    ) {
        String content = "{\"journey\":{\"tripId\":" + tripId + ",\"title\":\"Comparison journey\","
            + "\"startDate\":\"2026-08-01\",\"endDate\":\"2026-08-04\"},\"days\":[],"
            + "\"analytics\":{\"totalSpent\":" + total + ",\"dailyAverage\":" + daily
            + ",\"categoryBreakdown\":[{\"category\":\"" + category + "\",\"amount\":" + amount
            + ",\"percentage\":100.00}],\"dailyTrend\":[]}}";
        jdbcTemplate.update(
            "INSERT INTO reports (trip_id, generation_status, locale, report_content, "
                + "generated_at) VALUES (?, 'COMPLETED', 'en', ?, CURRENT_TIMESTAMP)",
            tripId, content
        );
        long id = lastInsertId();
        cleanup.push(() -> jdbcTemplate.update("DELETE FROM reports WHERE report_id = ?", id));
        return id;
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
