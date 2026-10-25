package me.nawa.notification.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import me.nawa.config.MySqlSchemaExtension;
import me.nawa.notification.ReminderTestData;
import me.nawa.notification.ReminderTestData.MemberRow;
import me.nawa.notification.ReminderTestData.SettlementRow;
import me.nawa.notification.domain.Notification;
import me.nawa.notification.domain.SettlementReminderCriteria;
import me.nawa.notification.mapper.NotificationMapper;
import me.nawa.notification.mapper.SettlementReminderMapper;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 묶음 하나가 트랜잭션 하나로 끝나는지, 그 안에서 잠금이 약속대로 동작하는지를 실제 DB로 본다.
 *
 * 트랜잭션 프록시가 필요해서 스프링 컨텍스트를 직접 조립한다. 알림 적재 직전에 끼어들 수 있게
 * {@code NotificationMapper}만 대리 객체로 감쌌다.
 *
 * 이 클래스의 정산은 2002년에 만들고 {@code now}도 같은 해로 넘긴다. 다른 테스트가 지금 시각으로
 * 만든 정산이 대상으로 섞이지 않게 하기 위해서다.
 */
@ExtendWith(MySqlSchemaExtension.class)
@EnabledIfEnvironmentVariable(
    named = "RUN_MYSQL_INTEGRATION_TESTS",
    matches = "(?i)true"
)
class SettlementReminderWriterIntegrationTest {

    private static final long TIMEOUT_SECONDS = 10L;
    private static final LocalDateTime NOW = LocalDateTime.of(2002, 6, 15, 10, 0);
    private static final LocalDateTime BEFORE = LocalDateTime.of(2002, 6, 13, 0, 0);
    private static final LocalDateTime DUE_CREATED_AT = BEFORE.minusDays(1);
    private static final SettlementReminderCriteria CRITERIA =
        new SettlementReminderCriteria(NOW, BEFORE, BEFORE, 3);

    private static HikariDataSource dataSource;
    private static JdbcTemplate jdbcTemplate;
    private static AnnotationConfigApplicationContext applicationContext;
    private static SettlementReminderWriter writer;
    /** 알림 INSERT 직전에 같은 스레드에서 실행된다. 테스트가 끼어들 자리다. */
    private static volatile Runnable beforeNotificationInsert;

    private ReminderTestData data;

    @BeforeAll
    static void setUpDatabase() {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setDriverClassName(requiredEnvironment("DATABASE_DRIVER"));
        hikariConfig.setJdbcUrl(requiredEnvironment("DATABASE_URL"));
        hikariConfig.setUsername(requiredEnvironment("DATABASE_USERNAME"));
        hikariConfig.setPassword(requiredEnvironment("DATABASE_PASSWORD"));
        hikariConfig.setMaximumPoolSize(6);
        hikariConfig.setMinimumIdle(0);
        dataSource = new HikariDataSource(hikariConfig);
        jdbcTemplate = new JdbcTemplate(dataSource);
        applicationContext = new AnnotationConfigApplicationContext(WriterConfiguration.class);
        writer = applicationContext.getBean(SettlementReminderWriter.class);
    }

    @AfterAll
    static void closeDatabase() {
        if (applicationContext != null) {
            applicationContext.close();
        }
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @BeforeEach
    void setUp() {
        data = new ReminderTestData(dataSource);
        beforeNotificationInsert = null;
    }

    @AfterEach
    void tearDown() {
        beforeNotificationInsert = null;
        data.cleanup();
    }

    @Test
    void remind_marksAndInsertsTogetherAndReturnsWhatItCommitted() {
        assertTrue(AopUtils.isAopProxy(writer));
        SettlementRow settlement = data.settlement(DUE_CREATED_AT);
        MemberRow member = data.pendingMember(settlement);

        List<Notification> recorded = writer.remind(List.of(member.settlementMemberId()), CRITERIA);

        assertEquals(1, recorded.size());
        assertEquals(1, data.reminderCount(member));
        assertEquals(NOW, data.lastRemindedAt(member));
        // 돌려받은 알림이 곧 커밋된 줄이다. 같은 내용이 알림 표에 있다.
        record Stored(String type, long settlementId, String actorName, String gatheringName,
                      BigDecimal amount, String currencyCode, LocalDateTime createdAt) {
        }
        List<Stored> stored = jdbcTemplate.query(
            "SELECT notification_type, settlement_id, actor_name, gathering_name, amount, "
                + "currency_code, created_at FROM notifications WHERE recipient_member_id = ?",
            (row, index) -> new Stored(
                row.getString("notification_type"), row.getLong("settlement_id"),
                row.getString("actor_name"), row.getString("gathering_name"),
                row.getBigDecimal("amount"), row.getString("currency_code"),
                row.getTimestamp("created_at").toLocalDateTime()
            ),
            member.memberId()
        );
        assertEquals(1, stored.size());
        assertEquals("SETTLEMENT_REMINDER", stored.get(0).type());
        assertEquals(settlement.settlementId(), stored.get(0).settlementId());
        assertEquals(settlement.payerName(), stored.get(0).actorName());
        assertEquals(settlement.gatheringName(), stored.get(0).gatheringName());
        assertEquals(0, ReminderTestData.SHARE.compareTo(stored.get(0).amount()));
        assertEquals("KRW", stored.get(0).currencyCode());
        // 시각은 DB 기본값이 아니라 이번 실행이 넘긴 값이다.
        assertEquals(NOW, stored.get(0).createdAt());
        assertEquals(member.memberId(), recorded.get(0).getRecipientMemberId());
        assertEquals(NOW, recorded.get(0).getCreatedAt());
    }

    /**
     * 알림 적재가 실패하면 발송 기록도 되돌아가야 한다. 기록만 남으면 그 사람은 받은 적 없는
     * 알림을 이미 받은 것으로 치고 다음 실행에서도 건너뛰어진다.
     */
    @Test
    void remind_insertFails_leavesNeitherTheMarkNorTheNotification() {
        SettlementRow settlement = data.settlement(DUE_CREATED_AT);
        MemberRow member = data.pendingMember(settlement);
        beforeNotificationInsert = () -> {
            throw new IllegalStateException("insert failed");
        };

        assertThrows(IllegalStateException.class,
            () -> writer.remind(List.of(member.settlementMemberId()), CRITERIA));

        assertEquals(0, data.reminderCount(member));
        assertNull(data.lastRemindedAt(member));
        assertEquals(0, data.reminderNotificationCount(member));
    }

    @Test
    void remind_runsInReadCommitted() {
        SettlementRow settlement = data.settlement(DUE_CREATED_AT);
        MemberRow member = data.pendingMember(settlement);
        AtomicReference<String> isolation = new AtomicReference<>();
        beforeNotificationInsert = () -> {
            // 트랜잭션에 묶인 연결로 묻는다. 새 연결이면 이 트랜잭션의 격리 수준이 아니다.
            Connection connection = DataSourceUtils.getConnection(dataSource);
            try (PreparedStatement statement = connection.prepareStatement("SELECT @@transaction_isolation");
                 ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                isolation.set(resultSet.getString(1));
            } catch (java.sql.SQLException exception) {
                throw new IllegalStateException(exception);
            } finally {
                DataSourceUtils.releaseConnection(connection, dataSource);
            }
        };

        writer.remind(List.of(member.settlementMemberId()), CRITERIA);

        assertEquals("READ-COMMITTED", isolation.get());
    }

    /** 다른 트랜잭션이 잡은 구성원은 기다리지 않고 빠지고, 나머지만 처리된다. */
    @Test
    void remind_skipsAMemberHeldByAnotherTransaction() throws Exception {
        SettlementRow settlement = data.settlement(DUE_CREATED_AT);
        MemberRow held = data.pendingMember(settlement);
        MemberRow free = data.pendingMember(settlement);

        try (Connection holder = dataSource.getConnection()) {
            holder.setAutoCommit(false);
            try {
                lock(holder, "SELECT settlement_member_id FROM settlement_members "
                    + "WHERE settlement_member_id = ? FOR UPDATE", held.settlementMemberId());

                List<Notification> recorded = writer.remind(
                    List.of(held.settlementMemberId(), free.settlementMemberId()), CRITERIA
                );

                assertEquals(List.of(free.memberId()),
                    recorded.stream().map(Notification::getRecipientMemberId).toList());
            } finally {
                holder.rollback();
            }
        }

        assertEquals(0, data.reminderCount(held));
        assertEquals(1, data.reminderCount(free));
    }

    /**
     * 같은 묶음을 두 곳이 동시에 처리해도 한 사람에게는 한 번만 간다. 서버 두 대가 같은 시각에
     * 같은 후보를 읽는 상황이다.
     */
    @Test
    void remind_sameChunkInTwoThreads_notifiesEachMemberOnce() throws Exception {
        SettlementRow settlement = data.settlement(DUE_CREATED_AT);
        List<MemberRow> members = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            members.add(data.pendingMember(settlement));
        }
        List<Long> ids = members.stream().map(MemberRow::settlementMemberId).toList();

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<List<Notification>> task = () -> {
                ready.countDown();
                assertTrue(start.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
                return writer.remind(ids, CRITERIA);
            };
            Future<List<Notification>> first = executor.submit(task);
            Future<List<Notification>> second = executor.submit(task);
            assertTrue(ready.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            start.countDown();

            int total = first.get(TIMEOUT_SECONDS, TimeUnit.SECONDS).size()
                + second.get(TIMEOUT_SECONDS, TimeUnit.SECONDS).size();
            assertEquals(members.size(), total);
        } finally {
            executor.shutdownNow();
        }
        for (MemberRow member : members) {
            assertEquals(1, data.reminderCount(member));
            assertEquals(1, data.reminderNotificationCount(member));
        }
    }

    /**
     * 같은 사람의 지급과 리마인더가 겹쳐도 서로를 기다리다 교착에 빠지지 않는다.
     *
     * 리마인더는 구성원 행을 쥔 채 알림을 INSERT하고, 그 외래키 검사가 정산 행에 공유 잠금을
     * 요구한다. 지급은 정산 행을 먼저 쥐고 구성원 행을 요구한다. 서로 상대가 쥔 것을 기다리는
     * 순환이다. 리마인더가 정산 행에도 공유 잠금을 함께 잡아 두면(lockTargets) 지급이 정산 행
     * 앞에서 리마인더가 끝나기를 기다릴 뿐 순환이 생기지 않는다.
     *
     * 리마인더가 구성원 행을 쥐고 알림을 쓰기 직전에 멈춘 사이 지급 쪽 트랜잭션을 끼워 넣는다.
     */
    @Test
    void remind_overlappingAPaymentOfTheSameMember_doesNotDeadlock() throws Exception {
        SettlementRow settlement = data.settlement(DUE_CREATED_AT);
        MemberRow member = data.pendingMember(settlement);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        beforeNotificationInsert = () -> {
            locked.countDown();
            try {
                assertTrue(release.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        };
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<List<Notification>> reminder = executor.submit(
                () -> writer.remind(List.of(member.settlementMemberId()), CRITERIA));
            assertTrue(locked.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));

            Future<Void> payment = executor.submit(() -> {
                try (Connection connection = dataSource.getConnection()) {
                    connection.setAutoCommit(false);
                    // SettlementPaymentServiceImpl.paySettlement의 잠금 순서다.
                    lock(connection, "SELECT settlement_id FROM settlements "
                        + "WHERE settlement_id = ? FOR UPDATE", settlement.settlementId());
                    lock(connection, "SELECT settlement_member_id FROM settlement_members "
                        + "WHERE settlement_member_id = ? FOR UPDATE", member.settlementMemberId());
                    connection.commit();
                }
                return null;
            });
            giveThePaymentTimeToWait();
            release.countDown();

            assertEquals(1, reminder.get(TIMEOUT_SECONDS, TimeUnit.SECONDS).size());
            payment.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
        assertEquals(1, data.reminderCount(member));
    }

    /**
     * 지급 쪽이 첫 잠금(또는 구성원 행 잠금) 앞에서 기다리기 시작할 시간을 준다.
     *
     * 접속 계정에는 잠금 대기를 보여 주는 뷰(PROCESS 권한)를 읽을 권한이 없어서 DB에 물어볼
     * 수 없다. 시간이 모자라 지급이 아직 잠금을 요청하지 못했다면 겹침이 생기지 않아 이 테스트가
     * 교착을 못 잡고 지나갈 뿐 실패하지는 않는다.
     */
    private static void giveThePaymentTimeToWait() throws InterruptedException {
        Thread.sleep(300);
    }

    private static void lock(Connection connection, String sql, long id) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            statement.executeQuery().close();
        }
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required for MySQL integration tests");
        }
        return value;
    }

    @Configuration
    @EnableTransactionManagement
    static class WriterConfiguration {

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
        public Object invoke(Object proxy, Method method, Object[] arguments) throws Throwable {
            Runnable hook = beforeNotificationInsert;
            if ("insertNotifications".equals(method.getName()) && hook != null) {
                hook.run();
            }
            try {
                return method.invoke(target, arguments);
            } catch (InvocationTargetException exception) {
                throw exception.getCause();
            }
        }
    }
}
