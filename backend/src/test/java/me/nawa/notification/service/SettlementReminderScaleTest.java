package me.nawa.notification.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import me.nawa.config.MySqlSchemaExtension;
import me.nawa.notification.domain.Notification;
import me.nawa.notification.domain.SettlementReminderCriteria;
import me.nawa.notification.mapper.NotificationMapper;
import me.nawa.notification.mapper.SettlementReminderMapper;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 미납이 10만 건일 때 리마인더 실행이 묶음 단위로 끝나는지 재고, 약속한 성질이 그 규모에서도
 * 지켜지는지 본다. <b>CI에서 돌지 않는다.</b> {@code RUN_REMINDER_SCALE_TEST=true}일 때만 돌고,
 * 시드와 측정에 몇 분이 걸린다.
 *
 * <p>이 클래스는 일부러 {@code *IntegrationTest}라는 이름을 피했다. 빌드가 통합 테스트 게이트를
 * 켠 채로 그 이름의 클래스가 한 건도 안 돌면 실패로 보는데, 이 테스트는 별도 게이트라 그 규칙에
 * 걸리면 안 된다.
 *
 * <p><b>일회용 DB에서만 돌린다.</b> 10만 건을 시드하고 끝나면 지우지만, 지우는 범위는 이 테스트가
 * 정한 큰 번호 대역(10억 이상)이다. 팀이 쓰는 DB를 가리키지 마라. 실행 방법과 측정 결과는
 * {@code NOTIFICATION_API.md}의 "대량 처리" 절에 있다.
 *
 * <p>구성: 미납 10만 건(정산 1만 개 × 10명) + 이미 낸 구성원 10만 건 + 아직 3일이 안 된 정산의
 * 5천 건 + 탈퇴 회원 5백 건. 이 가운데 대상은 미납 10만 건이다. 불량 20건은 알림 적재 직전에 예외를
 * 던지게 해 만든다.
 */
@ExtendWith(MySqlSchemaExtension.class)
@EnabledIfEnvironmentVariable(
    named = "RUN_REMINDER_SCALE_TEST",
    matches = "(?i)true"
)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SettlementReminderScaleTest {

    private static final long TIMEOUT_SECONDS = 600L;

    // 시드 규모.
    private static final int PAYERS = 100;
    private static final int DUE_SETTLEMENTS = 10_000;
    private static final int FRESH_SETTLEMENTS = 500;
    private static final int MEMBERS_PER_SETTLEMENT = 10;
    private static final int DUE_MEMBERS = DUE_SETTLEMENTS * MEMBERS_PER_SETTLEMENT;
    private static final int FRESH_MEMBERS = FRESH_SETTLEMENTS * MEMBERS_PER_SETTLEMENT;
    private static final int WITHDRAWN_MEMBERS = 500;
    // 이미 낸 구성원. 시간이 지나면 미납보다 이쪽이 훨씬 많아진다. 번호가 미납보다 크게 잡아서,
    // 미납 행만 읽는 인덱스와 번호 범위를 그냥 훑는 기본키 중 어느 쪽을 쓰는지가 갈리게 한다.
    private static final int PAID_MEMBERS = 100_000;
    private static final int PAID_PER_SETTLEMENT = PAID_MEMBERS / DUE_SETTLEMENTS;
    private static final int SETTLEMENTS = DUE_SETTLEMENTS + FRESH_SETTLEMENTS;
    private static final int BAD_ROWS = 20;

    // 번호 대역. 모두 10억 이상이라 다른 테스트의 자동 증가 번호와 겹치지 않고, 지울 때도 이 범위만 본다.
    private static final long PAYER0 = 1_000_000_000L;
    private static final long DUE0 = 1_100_000_000L;
    private static final long WITHDRAWN0 = 1_200_000_000L;
    private static final long FRESH0 = 1_300_000_000L;
    private static final long PAID0 = 1_400_000_000L;
    private static final long ITEM0 = 1_000_000_000L;
    private static final long APPOINTMENT0 = 1_000_000_000L;
    private static final long TRANSFER0 = 1_000_000_000L;
    private static final long PAID_TRANSFER0 = 1_400_000_000L;
    private static final long SETTLEMENT0 = 1_000_000_000L;
    private static final long PAYER_APPOINTMENT_MEMBER0 = 1_000_000_000L;
    private static final long DUE_APPOINTMENT_MEMBER0 = 1_100_000_000L;
    private static final long WITHDRAWN_APPOINTMENT_MEMBER0 = 1_200_000_000L;
    private static final long FRESH_APPOINTMENT_MEMBER0 = 1_300_000_000L;
    private static final long PAID_APPOINTMENT_MEMBER0 = 1_400_000_000L;
    private static final long PAYER_ROW0 = 1_000_000_000L;
    private static final long DUE_ROW0 = 1_100_000_000L;
    private static final long WITHDRAWN_ROW0 = 1_200_000_000L;
    private static final long FRESH_ROW0 = 1_300_000_000L;
    private static final long PAID_ROW0 = 1_400_000_000L;

    // 정산은 2004-06-01에, 새 정산은 06-14에 만들었고 실행 시각은 06-15다. 06-15에 첫 리마인더를
    // 받는 것은 06-13 자정 전(06-12까지)에 만든 정산이라 앞의 것만 대상이다.
    private static final LocalDateTime NOW = LocalDateTime.of(2004, 6, 15, 10, 0);

    private static HikariDataSource dataSource;
    private static JdbcTemplate jdbc;
    private static AnnotationConfigApplicationContext applicationContext;
    private static SettlementReminderMapper reminderMapper;
    private static SettlementReminderWriter writer;
    private static ThreadPoolTaskExecutor pushExecutor;
    private static final List<String> REPORT = new ArrayList<>();
    private static final AtomicInteger PUSHES = new AtomicInteger();
    /** 알림 INSERT 직전에 같은 스레드에서 실행된다. */
    private static volatile Consumer<List<Notification>> beforeNotificationInsert;

    @BeforeAll
    static void seed() throws Exception {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setDriverClassName(requiredEnvironment("DATABASE_DRIVER"));
        hikariConfig.setJdbcUrl(requiredEnvironment("DATABASE_URL"));
        hikariConfig.setUsername(requiredEnvironment("DATABASE_USERNAME"));
        hikariConfig.setPassword(requiredEnvironment("DATABASE_PASSWORD"));
        hikariConfig.setMaximumPoolSize(8);
        hikariConfig.setMinimumIdle(0);
        dataSource = new HikariDataSource(hikariConfig);
        jdbc = new JdbcTemplate(dataSource);
        applicationContext = new AnnotationConfigApplicationContext(ScaleConfiguration.class);
        reminderMapper = applicationContext.getBean(SettlementReminderMapper.class);
        writer = applicationContext.getBean(SettlementReminderWriter.class);
        pushExecutor = new ThreadPoolTaskExecutor();
        pushExecutor.setCorePoolSize(1);
        pushExecutor.setMaxPoolSize(1);
        pushExecutor.setQueueCapacity(PushConfig.QUEUE_CAPACITY);
        pushExecutor.initialize();

        report("# 미납 리마인더 규모 측정");
        report("- DB: " + jdbc.queryForObject("SELECT VERSION()", String.class)
            + ", time_zone=" + jdbc.queryForObject("SELECT @@global.time_zone", String.class));
        report("- JVM: " + System.getProperty("java.version")
            + ", processors=" + Runtime.getRuntime().availableProcessors());

        // 앞선 실행이 중간에 죽어 남긴 시드가 있어도 같은 상태에서 시작한다.
        deleteSeed();
        long startedAt = System.nanoTime();
        seedRows();
        report("- 시드: 미납 대상 " + DUE_MEMBERS + "건 (정산 " + DUE_SETTLEMENTS + "개), 이미 낸 "
            + PAID_MEMBERS + "건, 아직 3일이 안 된 " + FRESH_MEMBERS + "건, 탈퇴 회원 " + WITHDRAWN_MEMBERS
            + "건, 소요 " + millisSince(startedAt) + "ms");
        assertEquals(DUE_MEMBERS + FRESH_MEMBERS + WITHDRAWN_MEMBERS, count(
            "SELECT COUNT(*) FROM settlement_members WHERE request_status = 'PENDING' "
                + "AND settlement_member_id >= " + DUE_ROW0));
    }

    @AfterAll
    static void cleanUp() throws Exception {
        try {
            if (jdbc != null) {
                long startedAt = System.nanoTime();
                deleteSeed();
                report("- 시드 삭제: " + millisSince(startedAt) + "ms");
            }
        } finally {
            Path file = Path.of("build", "reminder-scale-report.txt");
            Files.createDirectories(file.getParent());
            Files.write(file, REPORT, StandardCharsets.UTF_8);
            REPORT.forEach(System.out::println);
            if (pushExecutor != null) {
                pushExecutor.shutdown();
            }
            if (applicationContext != null) {
                applicationContext.close();
            }
            if (dataSource != null) {
                dataSource.close();
            }
        }
    }

    /**
     * 처음 한 번의 실행. 모든 대상에게 정확히 한 번 가고, 불량 20건만 건너뛰고, 아직 3일이 안 된
     * 정산과 탈퇴 회원은 건드리지 않는다. 같은 실행을 다시 돌리면 불량 외에는 할 일이 없다.
     */
    @Test
    @Order(2)
    void run_hundredThousandMembers_finishesInChunksAndRemindsEachOnce() {
        Set<Long> bad = badRecipients();
        beforeNotificationInsert = notifications -> {
            if (notifications.stream().anyMatch(n -> bad.contains(n.getRecipientMemberId()))) {
                throw new IllegalStateException("bad row");
            }
        };
        resetReminders();

        SettlementReminderResult first = run();
        report("## 1회 실행 (불량 " + BAD_ROWS + "건)");
        report("- " + describe(first));
        assertEquals(DUE_MEMBERS / 100, first.chunks());
        assertEquals(DUE_MEMBERS - BAD_ROWS, first.reminded());
        assertEquals(BAD_ROWS, first.skipped());
        assertExactlyOnce(BAD_ROWS);
        assertEquals(DUE_MEMBERS - BAD_ROWS, PUSHES.get());
        assertUntouched();

        SettlementReminderResult again = run();
        report("## 같은 실행을 다시");
        report("- " + describe(again));
        assertEquals(0, again.reminded());
        assertEquals(BAD_ROWS, again.skipped());
        assertEquals(1, again.chunks());
        assertExactlyOnce(BAD_ROWS);

        beforeNotificationInsert = null;
        SettlementReminderResult fixed = run();
        report("## 불량이 풀린 뒤 다시");
        report("- " + describe(fixed));
        assertEquals(BAD_ROWS, fixed.reminded());
        assertEquals(0, fixed.skipped());
        assertExactlyOnce(0);
    }

    /**
     * 서버가 중간에 멈췄다. 앞의 499개 묶음은 남고, 멈춘 묶음은 기록도 알림도 없이 롤백되며, 다시
     * 돌리면 남은 사람만 처리해 합치면 정확히 한 번씩이다.
     */
    @Test
    @Order(3)
    void run_abortedAtChunk500_resumesWithOnlyTheRemainingMembers() {
        resetReminders();
        AtomicInteger inserts = new AtomicInteger();
        beforeNotificationInsert = notifications -> {
            if (inserts.incrementAndGet() == 500) {
                throw new AssertionError("서버가 멈췄다");
            }
        };

        long startedAt = System.nanoTime();
        assertThrows(AssertionError.class, this::run);
        long abortedAfterMillis = millisSince(startedAt);
        int done = count("SELECT COUNT(*) FROM settlement_members WHERE reminder_count = 1 "
            + "AND settlement_member_id BETWEEN " + (DUE_ROW0 + 1) + " AND " + (DUE_ROW0 + DUE_MEMBERS));
        report("## 강제 중단 후 재개");
        report("- 500번째 묶음에서 중단: 그때까지 " + done + "건 완료 (" + abortedAfterMillis + "ms)");
        assertEquals(499 * 100, done);
        assertEquals(done, count("SELECT COUNT(*) FROM notifications WHERE notification_type = "
            + "'SETTLEMENT_REMINDER' AND recipient_member_id > " + DUE0));

        beforeNotificationInsert = null;
        SettlementReminderResult resumed = run();
        report("- 재개: " + describe(resumed));
        assertEquals(DUE_MEMBERS - done, resumed.reminded());
        assertEquals(0, resumed.skipped());
        assertExactlyOnce(0);
    }

    /**
     * 서버 두 대가 같은 시각에 시작한 상황이다. 둘이 합쳐 정확히 한 번씩 처리하고, 한 사람이 두
     * 번 받는 일이 없다.
     */
    @Test
    @Order(4)
    void run_twoRunsAtTheSameTime_remindEachMemberExactlyOnce() throws Exception {
        beforeNotificationInsert = null;
        resetReminders();

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        SettlementReminderResult first;
        SettlementReminderResult second;
        long startedAt;
        try {
            Future<SettlementReminderResult> firstRun = executor.submit(() -> startTogether(ready, start));
            Future<SettlementReminderResult> secondRun = executor.submit(() -> startTogether(ready, start));
            assertTrue(ready.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            startedAt = System.nanoTime();
            start.countDown();
            first = firstRun.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            second = secondRun.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
        long wallMillis = millisSince(startedAt);

        report("## 동시 2회 실행");
        report("- 실행 A: " + describe(first));
        report("- 실행 B: " + describe(second));
        report("- 두 실행이 끝날 때까지 " + wallMillis + "ms");
        assertEquals(DUE_MEMBERS, first.reminded() + second.reminded());
        assertEquals(0, first.skipped() + second.skipped());
        assertExactlyOnce(0);
    }

    private SettlementReminderResult startTogether(CountDownLatch ready, CountDownLatch start)
        throws InterruptedException {
        ready.countDown();
        assertTrue(start.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        return run();
    }

    private SettlementReminderResult run() {
        PushCounter counter = new PushCounter();
        SettlementReminderService service =
            new SettlementReminderServiceImpl(reminderMapper, writer, counter, pushExecutor);
        return service.sendReminders(NOW);
    }

    /** 대상 10만 건이 모두 정확히 한 번씩 받았는지 SQL 집계로 확인한다. 불량으로 남은 수를 뺀다. */
    private void assertExactlyOnce(int expectedNeverReminded) {
        Map<String, Object> rows = jdbc.queryForMap(
            "SELECT COUNT(*) AS total, SUM(reminder_count = 1) AS once, SUM(reminder_count = 0) AS never, "
                + "SUM(reminder_count > 1) AS more FROM settlement_members "
                + "WHERE settlement_member_id BETWEEN ? AND ?",
            DUE_ROW0 + 1, DUE_ROW0 + DUE_MEMBERS
        );
        assertEquals(DUE_MEMBERS, number(rows.get("total")));
        assertEquals(DUE_MEMBERS - expectedNeverReminded, number(rows.get("once")));
        assertEquals(expectedNeverReminded, number(rows.get("never")));
        assertEquals(0, number(rows.get("more")));

        int notifications = count("SELECT COUNT(*) FROM notifications "
            + "WHERE notification_type = 'SETTLEMENT_REMINDER' AND recipient_member_id > " + DUE0);
        assertEquals(DUE_MEMBERS - expectedNeverReminded, notifications);
        // 한 사람이 한 정산에서 알림을 두 번 받은 경우가 없다.
        assertEquals(0, count("SELECT COUNT(*) FROM (SELECT recipient_member_id FROM notifications "
            + "WHERE notification_type = 'SETTLEMENT_REMINDER' GROUP BY recipient_member_id "
            + "HAVING COUNT(*) > 1) duplicated"));
    }

    /** 대상이 아닌 행은 한 번도 건드려지지 않았다. */
    private void assertUntouched() {
        assertEquals(0, count("SELECT COUNT(*) FROM settlement_members WHERE reminder_count <> 0 "
            + "AND (settlement_member_id >= " + WITHDRAWN_ROW0 + ")"));
        assertEquals(0, count("SELECT COUNT(*) FROM notifications WHERE recipient_member_id >= " + WITHDRAWN0));
    }

    private static void resetReminders() {
        jdbc.update("DELETE FROM notifications WHERE settlement_id BETWEEN ? AND ?",
            SETTLEMENT0 + 1, SETTLEMENT0 + SETTLEMENTS);
        jdbc.update("UPDATE settlement_members SET reminder_count = 0, last_reminded_at = NULL "
            + "WHERE settlement_member_id >= ?", PAYER_ROW0);
        PUSHES.set(0);
    }

    private static Set<Long> badRecipients() {
        Set<Long> bad = new HashSet<>();
        for (int k = 1; k <= BAD_ROWS; k++) {
            bad.add(DUE0 + (long) k * (DUE_MEMBERS / BAD_ROWS));
        }
        return bad;
    }

    private static SettlementReminderCriteria criteria() {
        return SettlementReminderServiceImpl.criteriaAt(NOW);
    }

    private static String describe(SettlementReminderResult result) {
        double seconds = Math.max(result.elapsedMillis(), 1) / 1000.0;
        return "묶음 " + result.chunks() + "개, 알림 " + result.reminded() + "건, 건너뜀 " + result.skipped()
            + "건, " + result.elapsedMillis() + "ms (초당 약 " + Math.round(result.reminded() / seconds) + "건)";
    }

    // ---- 시드 ----------------------------------------------------------------------------

    private static void seedRows() throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("SET SESSION cte_max_recursion_depth = 200000");
            for (String sql : seedStatements()) {
                statement.executeUpdate(sql);
            }
        }
    }

    private static List<String> seedStatements() {
        String dueDay = "'2004-06-01 12:00:00'";
        String freshDay = "'2004-06-14 12:00:00'";
        List<String> sql = new ArrayList<>();
        sql.add("INSERT INTO members (member_id, display_name) " + seq(PAYERS)
            + "SELECT " + PAYER0 + " + n, CONCAT('scale-p-', n) FROM seq");
        sql.add("INSERT INTO members (member_id, display_name) " + seq(DUE_MEMBERS)
            + "SELECT " + DUE0 + " + n, CONCAT('scale-d-', n) FROM seq");
        sql.add("INSERT INTO members (member_id, display_name, member_status) " + seq(WITHDRAWN_MEMBERS)
            + "SELECT " + WITHDRAWN0 + " + n, CONCAT('scale-w-', n), 'WITHDRAWN' FROM seq");
        sql.add("INSERT INTO members (member_id, display_name) " + seq(FRESH_MEMBERS)
            + "SELECT " + FRESH0 + " + n, CONCAT('scale-f-', n) FROM seq");
        sql.add("INSERT INTO explore_items (item_id, created_by, reviewed_by, item_type, approval_status, "
            + "visibility_status, reviewed_at) VALUES (" + (ITEM0 + 1) + ", " + (PAYER0 + 1) + ", " + (PAYER0 + 1)
            + ", 'PLACE', 'APPROVED', 'VISIBLE', '2004-05-01 00:00:00')");
        String payer = PAYER0 + " + 1 + (n % " + PAYERS + ")";
        sql.add("INSERT INTO appointments (appointment_id, item_id, host_member_id, language_code, "
            + "appointment_name, max_members, deposit_amount, activity_start_at, activity_end_at) " + seq(SETTLEMENTS)
            + "SELECT " + APPOINTMENT0 + " + n, " + (ITEM0 + 1) + ", " + payer + ", 'en', CONCAT('scale-a-', n), "
            + "100, 5000, '2004-05-31 10:00:00', '2004-05-31 12:00:00' FROM seq");
        sql.add("INSERT INTO appointment_members (appointment_member_id, appointment_id, member_id) " + seq(SETTLEMENTS)
            + "SELECT " + PAYER_APPOINTMENT_MEMBER0 + " + n, " + APPOINTMENT0 + " + n, " + payer + " FROM seq");
        sql.add("INSERT INTO appointment_members (appointment_member_id, appointment_id, member_id) " + seq(DUE_MEMBERS)
            + "SELECT " + DUE_APPOINTMENT_MEMBER0 + " + n, " + APPOINTMENT0 + " + ((n - 1) DIV "
            + MEMBERS_PER_SETTLEMENT + ") + 1, " + DUE0 + " + n FROM seq");
        sql.add("INSERT INTO appointment_members (appointment_member_id, appointment_id, member_id) "
            + seq(WITHDRAWN_MEMBERS) + "SELECT " + WITHDRAWN_APPOINTMENT_MEMBER0 + " + n, " + APPOINTMENT0
            + " + n, " + WITHDRAWN0 + " + n FROM seq");
        sql.add("INSERT INTO appointment_members (appointment_member_id, appointment_id, member_id) " + seq(FRESH_MEMBERS)
            + "SELECT " + FRESH_APPOINTMENT_MEMBER0 + " + n, " + APPOINTMENT0 + " + " + DUE_SETTLEMENTS
            + " + ((n - 1) DIV " + MEMBERS_PER_SETTLEMENT + ") + 1, " + FRESH0 + " + n FROM seq");
        sql.add("INSERT INTO wallet_transfers (transfer_id, currency_code, transfer_number, transfer_type, "
            + "transfer_status, amount, completed_at) " + seq(SETTLEMENTS)
            + "SELECT " + TRANSFER0 + " + n, 'KRW', CONCAT('scale-t-', n), 'QR_PAYMENT', 'COMPLETED', 100, "
            + dueDay + " FROM seq");
        sql.add("INSERT INTO settlements (settlement_id, appointment_id, created_by_member_id, payer_member_id, "
            + "source_transfer_id, idempotency_key, request_fingerprint, settlement_status, split_method, "
            + "total_amount, payer_share_amount, receivable_amount, created_at) " + seq(SETTLEMENTS)
            + "SELECT " + SETTLEMENT0 + " + n, " + APPOINTMENT0 + " + n, " + payer + ", " + payer + ", "
            + TRANSFER0 + " + n, CONCAT('scale-', n), REPEAT('a', 64), 'REQUESTED', 'EQUAL', 100, 40, 60, "
            + "IF(n <= " + DUE_SETTLEMENTS + ", " + dueDay + ", " + freshDay + ") FROM seq");
        sql.add("INSERT INTO settlement_members (settlement_member_id, settlement_id, appointment_member_id, "
            + "share_amount, request_status) " + seq(SETTLEMENTS)
            + "SELECT " + PAYER_ROW0 + " + n, " + SETTLEMENT0 + " + n, " + PAYER_APPOINTMENT_MEMBER0
            + " + n, 40, 'NOT_REQUESTED' FROM seq");
        sql.add("INSERT INTO settlement_members (settlement_member_id, settlement_id, appointment_member_id, "
            + "share_amount, request_status) " + seq(DUE_MEMBERS)
            + "SELECT " + DUE_ROW0 + " + n, " + SETTLEMENT0 + " + ((n - 1) DIV " + MEMBERS_PER_SETTLEMENT + ") + 1, "
            + DUE_APPOINTMENT_MEMBER0 + " + n, 60, 'PENDING' FROM seq");
        sql.add("INSERT INTO settlement_members (settlement_member_id, settlement_id, appointment_member_id, "
            + "share_amount, request_status) " + seq(WITHDRAWN_MEMBERS)
            + "SELECT " + WITHDRAWN_ROW0 + " + n, " + SETTLEMENT0 + " + n, " + WITHDRAWN_APPOINTMENT_MEMBER0
            + " + n, 60, 'PENDING' FROM seq");
        sql.add("INSERT INTO settlement_members (settlement_member_id, settlement_id, appointment_member_id, "
            + "share_amount, request_status) " + seq(FRESH_MEMBERS)
            + "SELECT " + FRESH_ROW0 + " + n, " + SETTLEMENT0 + " + " + DUE_SETTLEMENTS + " + ((n - 1) DIV "
            + MEMBERS_PER_SETTLEMENT + ") + 1, " + FRESH_APPOINTMENT_MEMBER0 + " + n, 60, 'PENDING' FROM seq");
        // 이미 낸 구성원: 미납과 같은 정산(정산당 10명)에 붙고, 지급 이체가 하나씩 있다.
        sql.add("INSERT INTO members (member_id, display_name) " + seq(PAID_MEMBERS)
            + "SELECT " + PAID0 + " + n, CONCAT('scale-x-', n) FROM seq");
        sql.add("INSERT INTO appointment_members (appointment_member_id, appointment_id, member_id) " + seq(PAID_MEMBERS)
            + "SELECT " + PAID_APPOINTMENT_MEMBER0 + " + n, " + APPOINTMENT0 + " + ((n - 1) DIV "
            + PAID_PER_SETTLEMENT + ") + 1, " + PAID0 + " + n FROM seq");
        sql.add("INSERT INTO wallet_transfers (transfer_id, currency_code, transfer_number, transfer_type, "
            + "transfer_status, amount, completed_at) " + seq(PAID_MEMBERS)
            + "SELECT " + PAID_TRANSFER0 + " + n, 'KRW', CONCAT('scale-pt-', n), 'QR_PAYMENT', 'COMPLETED', 60, "
            + dueDay + " FROM seq");
        sql.add("INSERT INTO settlement_members (settlement_member_id, settlement_id, appointment_member_id, "
            + "share_amount, request_status, paid_transfer_id, paid_at) " + seq(PAID_MEMBERS)
            + "SELECT " + PAID_ROW0 + " + n, " + SETTLEMENT0 + " + ((n - 1) DIV " + PAID_PER_SETTLEMENT + ") + 1, "
            + PAID_APPOINTMENT_MEMBER0 + " + n, 60, 'PAID', " + PAID_TRANSFER0 + " + n, " + dueDay + " FROM seq");
        return sql;
    }

    private static String seq(int max) {
        return "WITH RECURSIVE seq AS (SELECT 1 AS n UNION ALL SELECT n + 1 FROM seq WHERE n < " + max + ") ";
    }

    /** 시드가 만든 행만 외래키 순서대로 지운다. 번호 대역이 10억 이상인 행뿐이다. */
    private static void deleteSeed() {
        jdbc.update("DELETE FROM notifications WHERE settlement_id BETWEEN ? AND ?",
            SETTLEMENT0 + 1, SETTLEMENT0 + SETTLEMENTS);
        jdbc.update("DELETE FROM settlement_members WHERE settlement_member_id >= ?", PAYER_ROW0);
        jdbc.update("DELETE FROM settlements WHERE settlement_id BETWEEN ? AND ?",
            SETTLEMENT0 + 1, SETTLEMENT0 + SETTLEMENTS);
        jdbc.update("DELETE FROM wallet_transfers WHERE transfer_id BETWEEN ? AND ?",
            TRANSFER0 + 1, TRANSFER0 + SETTLEMENTS);
        jdbc.update("DELETE FROM wallet_transfers WHERE transfer_id BETWEEN ? AND ?",
            PAID_TRANSFER0 + 1, PAID_TRANSFER0 + PAID_MEMBERS);
        jdbc.update("DELETE FROM appointment_members WHERE appointment_member_id >= ?", PAYER_APPOINTMENT_MEMBER0);
        jdbc.update("DELETE FROM appointments WHERE appointment_id BETWEEN ? AND ?",
            APPOINTMENT0 + 1, APPOINTMENT0 + SETTLEMENTS);
        jdbc.update("DELETE FROM explore_items WHERE item_id = ?", ITEM0 + 1);
        jdbc.update("DELETE FROM members WHERE member_id >= ?", PAYER0);
    }

    // ---- 보조 ----------------------------------------------------------------------------

    private static int count(String sql) {
        Integer value = jdbc.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }

    private static int number(Object value) {
        return ((Number) value).intValue();
    }

    private static long millisSince(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000;
    }

    private static void report(String line) {
        synchronized (REPORT) {
            REPORT.add(line);
        }
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required for the scale test");
        }
        return value;
    }

    /** 푸시 요청 수만 센다. 실제 발송은 이 측정의 관심사가 아니다. */
    private static final class PushCounter implements PushSender {
        @Override
        public void send(List<Notification> notifications) {
            PUSHES.addAndGet(notifications.size());
        }
    }

    @Configuration
    @EnableTransactionManagement
    static class ScaleConfiguration {

        @Bean
        DataSourceTransactionManager transactionManager() {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        SqlSessionFactory sqlSessionFactory() throws Exception {
            SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
            factoryBean.setDataSource(dataSource);
            factoryBean.setConfigLocation(new ClassPathResource("mybatis-config.xml"));
            factoryBean.setMapperLocations(
                new ClassPathResource("me/nawa/notification/mapper/NotificationMapper.xml"),
                new ClassPathResource("me/nawa/notification/mapper/SettlementReminderMapper.xml")
            );
            return factoryBean.getObject();
        }

        @Bean
        SettlementReminderMapper settlementReminderMapper(SqlSessionFactory sqlSessionFactory) {
            return new SqlSessionTemplate(sqlSessionFactory).getMapper(SettlementReminderMapper.class);
        }

        @Bean
        NotificationMapper notificationMapper(SqlSessionFactory sqlSessionFactory) {
            NotificationMapper target = new SqlSessionTemplate(sqlSessionFactory)
                .getMapper(NotificationMapper.class);
            return (NotificationMapper) Proxy.newProxyInstance(
                NotificationMapper.class.getClassLoader(),
                new Class<?>[]{NotificationMapper.class},
                new BeforeInsertHook(target)
            );
        }

        @Bean
        SettlementReminderWriter settlementReminderWriter(
            SettlementReminderMapper reminderMapper,
            NotificationMapper notificationMapper
        ) {
            return new SettlementReminderWriterImpl(reminderMapper, notificationMapper);
        }
    }

    /** 알림 INSERT 직전에 테스트가 정한 동작을 같은 스레드에서 실행한다. */
    private static final class BeforeInsertHook implements InvocationHandler {
        private final NotificationMapper target;

        private BeforeInsertHook(NotificationMapper target) {
            this.target = target;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Object invoke(Object proxy, Method method, Object[] arguments) throws Throwable {
            Consumer<List<Notification>> hook = beforeNotificationInsert;
            if ("insertNotifications".equals(method.getName()) && hook != null) {
                hook.accept((List<Notification>) arguments[0]);
            }
            try {
                return method.invoke(target, arguments);
            } catch (InvocationTargetException exception) {
                throw exception.getCause();
            }
        }
    }
}
