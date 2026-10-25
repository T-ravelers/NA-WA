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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import me.nawa.config.MySqlSchemaExtension;
import me.nawa.notification.ReminderTestData;
import me.nawa.notification.ReminderTestData.MemberRow;
import me.nawa.notification.ReminderTestData.SettlementRow;
import me.nawa.notification.domain.Notification;
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
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 실행 한 번이 DB 위에서 약속대로 끝나는지를 본다. 정책의 날짜 계산, 묶음 순회, 동시 실행, 도중
 * 중단 뒤 재개, 불량 한 건이 실제 롤백을 거쳐 건너뛰어지는 흐름이다.
 *
 * 이 클래스의 정산은 2003년에 만들고 {@code now}도 같은 해로 넘긴다. 실행은 DB 전체를 훑으므로
 * 다른 테스트가 지금 시각으로 만든 정산이 대상으로 섞이지 않게 하기 위해서다.
 *
 * 알림 적재 직전에 끼어들 수 있게 {@code NotificationMapper}만 대리 객체로 감쌌다. 여기서 던지는
 * 예외는 묶음의 트랜잭션 안에서 나오므로 롤백까지 함께 확인된다.
 */
@ExtendWith(MySqlSchemaExtension.class)
@EnabledIfEnvironmentVariable(
    named = "RUN_MYSQL_INTEGRATION_TESTS",
    matches = "(?i)true"
)
class SettlementReminderServiceIntegrationTest {

    private static final long TIMEOUT_SECONDS = 60L;
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2003, 6, 10, 14, 0);
    // 정산을 만든 날(10일)의 3일째부터 대상이다.
    private static final LocalDateTime THIRD_DAY = LocalDateTime.of(2003, 6, 13, 10, 0);

    private static HikariDataSource dataSource;
    private static AnnotationConfigApplicationContext applicationContext;
    private static SettlementReminderMapper reminderMapper;
    private static SettlementReminderWriter writer;
    private static ThreadPoolTaskExecutor pushExecutor;
    /** 알림 INSERT 직전에 같은 스레드에서 실행된다. 테스트가 끼어들거나 실패시킬 자리다. */
    private static volatile Consumer<List<Notification>> beforeNotificationInsert;

    private ReminderTestData data;
    private RecordingPushSender pushSender;
    private SettlementReminderService service;

    @BeforeAll
    static void setUpDatabase() {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setDriverClassName(requiredEnvironment("DATABASE_DRIVER"));
        hikariConfig.setJdbcUrl(requiredEnvironment("DATABASE_URL"));
        hikariConfig.setUsername(requiredEnvironment("DATABASE_USERNAME"));
        hikariConfig.setPassword(requiredEnvironment("DATABASE_PASSWORD"));
        hikariConfig.setMaximumPoolSize(8);
        hikariConfig.setMinimumIdle(0);
        dataSource = new HikariDataSource(hikariConfig);
        applicationContext = new AnnotationConfigApplicationContext(ServiceConfiguration.class);
        reminderMapper = applicationContext.getBean(SettlementReminderMapper.class);
        writer = applicationContext.getBean(SettlementReminderWriter.class);
        pushExecutor = new ThreadPoolTaskExecutor();
        pushExecutor.setCorePoolSize(1);
        pushExecutor.setMaxPoolSize(1);
        pushExecutor.setQueueCapacity(PushConfig.QUEUE_CAPACITY);
        pushExecutor.initialize();
    }

    @AfterAll
    static void closeDatabase() {
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

    @BeforeEach
    void setUp() {
        data = new ReminderTestData(dataSource);
        pushSender = new RecordingPushSender();
        service = new SettlementReminderServiceImpl(reminderMapper, writer, pushSender, pushExecutor);
        beforeNotificationInsert = null;
    }

    @AfterEach
    void tearDown() {
        beforeNotificationInsert = null;
        data.cleanup();
    }

    /**
     * 정책 초안을 날짜로 따라간다. 10일 오후 2시에 요청한 정산은 13일부터 첫 알림을 받고, 3일
     * 간격으로 총 3번 받은 뒤 더는 받지 않는다. 같은 날 다시 돌려도 더 가지 않는다.
     */
    @Test
    void sendReminders_followsThePolicyByCalendarDays() {
        SettlementRow settlement = data.settlement(CREATED_AT);
        MemberRow member = data.pendingMember(settlement);

        assertEquals(0, run(LocalDateTime.of(2003, 6, 12, 10, 0)).reminded());
        assertEquals(1, run(LocalDateTime.of(2003, 6, 13, 10, 0)).reminded());
        assertEquals(0, run(LocalDateTime.of(2003, 6, 13, 15, 0)).reminded());
        assertEquals(0, run(LocalDateTime.of(2003, 6, 15, 10, 0)).reminded());
        assertEquals(1, run(LocalDateTime.of(2003, 6, 16, 10, 0)).reminded());
        assertEquals(1, run(LocalDateTime.of(2003, 6, 19, 10, 0)).reminded());
        // 상한(3번)에 닿았다. 간격이 아무리 지나도 더 가지 않는다.
        assertEquals(0, run(LocalDateTime.of(2003, 6, 22, 10, 0)).reminded());
        assertEquals(0, run(LocalDateTime.of(2003, 7, 22, 10, 0)).reminded());

        assertEquals(3, data.reminderCount(member));
        assertEquals(3, data.reminderNotificationCount(member));
        assertEquals(LocalDateTime.of(2003, 6, 19, 10, 0), data.lastRemindedAt(member));
        assertEquals(3, pushSender.sent().size());
    }

    @Test
    void sendReminders_doesNotRemindThosePaidCompletedOrWithdrawn() {
        SettlementRow open = data.settlement(CREATED_AT);
        MemberRow due = data.pendingMember(open);
        MemberRow paid = data.paidMember(open);
        MemberRow withdrawn = data.pendingMember(open);
        data.setMemberStatus(withdrawn, "WITHDRAWN");
        SettlementRow completed = data.settlement(CREATED_AT);
        MemberRow inCompleted = data.pendingMember(completed);
        data.completeSettlement(completed);

        SettlementReminderResult result = run(THIRD_DAY);

        assertEquals(1, result.reminded());
        assertEquals(1, data.reminderNotificationCount(due));
        for (MemberRow member : List.of(paid, withdrawn, inCompleted)) {
            assertEquals(0, data.reminderCount(member));
            assertEquals(0, data.reminderNotificationCount(member));
        }
    }

    @Test
    void sendReminders_manyMembers_areHandledInChunksAndPushedOncePerReminder() {
        SettlementRow settlement = data.settlement(CREATED_AT);
        List<MemberRow> members = data.pendingMembers(settlement, 250);

        SettlementReminderResult result = run(THIRD_DAY);

        assertEquals(3, result.chunks());
        assertEquals(250, result.reminded());
        assertEquals(0, result.skipped());
        assertEachRemindedOnce(members);
        assertEquals(recipientIds(members), pushedRecipientIds());
        assertEquals(250, pushSender.sent().size());
    }

    /**
     * 서버 두 대가 같은 시각에 같은 후보를 읽고 돌려도 한 사람에게 두 번 가지 않는다. 행 잠금과
     * 재확인 조건이 막는다. 둘이 합쳐 정확히 한 번씩 처리한다.
     */
    @Test
    void sendReminders_twoRunsAtTheSameTime_notifyEachMemberExactlyOnce() throws Exception {
        SettlementRow settlement = data.settlement(CREATED_AT);
        List<MemberRow> members = data.pendingMembers(settlement, 400);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        SettlementReminderResult first;
        SettlementReminderResult second;
        try {
            Future<SettlementReminderResult> firstRun = executor.submit(() -> startTogether(ready, start));
            Future<SettlementReminderResult> secondRun = executor.submit(() -> startTogether(ready, start));
            assertTrue(ready.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            start.countDown();
            first = firstRun.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            second = secondRun.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertEquals(400, first.reminded() + second.reminded());
        assertEquals(0, first.skipped() + second.skipped());
        assertEachRemindedOnce(members);
        assertEquals(400, pushSender.sent().size());
        assertEquals(recipientIds(members), pushedRecipientIds());
    }

    /**
     * 세 번째 묶음에서 서버가 멈췄다. 멈춘 묶음은 기록도 알림도 남기지 않고(트랜잭션째 롤백), 앞의 두
     * 묶음은 그대로 남는다. 다시 돌리면 남은 사람만 처리하고, 이미 받은 사람은 다시 받지 않는다.
     */
    @Test
    void sendReminders_abortedMidRun_resumesWithOnlyTheRemainingMembers() {
        SettlementRow settlement = data.settlement(CREATED_AT);
        List<MemberRow> members = data.pendingMembers(settlement, 350);
        AtomicInteger inserts = new AtomicInteger();
        beforeNotificationInsert = notifications -> {
            if (inserts.incrementAndGet() == 3) {
                // RuntimeException이 아니라서 한 건씩 다시 시도하는 경로가 잡지 못한다.
                throw new AssertionError("서버가 멈췄다");
            }
        };

        assertThrows(AssertionError.class, () -> run(THIRD_DAY));

        int remindedBeforeCrash = (int) members.stream().filter(member -> data.reminderCount(member) == 1).count();
        assertEquals(200, remindedBeforeCrash);
        // 멈춘 묶음의 100명은 기록도 알림도 없다.
        assertEquals(200, members.stream().mapToInt(data::reminderNotificationCount).sum());
        assertEquals(200, pushSender.sent().size());

        beforeNotificationInsert = null;
        SettlementReminderResult resumed = run(THIRD_DAY);

        assertEquals(2, resumed.chunks());
        assertEquals(150, resumed.reminded());
        assertEachRemindedOnce(members);
        assertEquals(350, pushSender.sent().size());
        assertEquals(recipientIds(members), pushedRecipientIds());
    }

    /**
     * 불량 한 건이 있으면 그 묶음은 통째로 롤백되고, 한 건씩 다시 돌리면 불량만 건너뛰어진다. 나머지
     * 99명은 알림을 받고, 푸시는 롤백된 시도가 아니라 커밋된 알림에만 나간다. 불량이 사라지면 다음
     * 실행이 그 한 건을 처리한다.
     */
    @Test
    void sendReminders_oneBadRow_skipsOnlyThatOneAndRetriesItNextRun() {
        SettlementRow settlement = data.settlement(CREATED_AT);
        List<MemberRow> members = data.pendingMembers(settlement, 250);
        MemberRow bad = members.get(119);
        beforeNotificationInsert = notifications -> {
            if (notifications.stream().anyMatch(n -> n.getRecipientMemberId().equals(bad.memberId()))) {
                throw new IllegalStateException("bad row");
            }
        };

        SettlementReminderResult result = run(THIRD_DAY);

        assertEquals(3, result.chunks());
        assertEquals(249, result.reminded());
        assertEquals(1, result.skipped());
        assertEquals(0, data.reminderCount(bad));
        assertEquals(0, data.reminderNotificationCount(bad));
        List<MemberRow> others = members.stream().filter(member -> member != bad).toList();
        assertEachRemindedOnce(others);
        assertEquals(recipientIds(others), pushedRecipientIds());
        assertEquals(249, pushSender.sent().size());

        beforeNotificationInsert = null;
        SettlementReminderResult nextRun = run(THIRD_DAY);

        assertEquals(1, nextRun.reminded());
        assertEquals(1, data.reminderCount(bad));
        assertEachRemindedOnce(members);
    }

    private SettlementReminderResult run(LocalDateTime now) {
        return service.sendReminders(now);
    }

    private SettlementReminderResult startTogether(CountDownLatch ready, CountDownLatch start)
        throws InterruptedException {
        ready.countDown();
        assertTrue(start.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        return service.sendReminders(THIRD_DAY);
    }

    private void assertEachRemindedOnce(List<MemberRow> members) {
        for (MemberRow member : members) {
            assertEquals(1, data.reminderCount(member), "reminder_count of " + member.memberId());
            assertEquals(1, data.reminderNotificationCount(member),
                "notifications of " + member.memberId());
        }
    }

    private static Set<Long> recipientIds(List<MemberRow> members) {
        return members.stream().map(MemberRow::memberId).collect(Collectors.toSet());
    }

    private Set<Long> pushedRecipientIds() {
        return new HashSet<>(pushSender.sent().stream().map(Notification::getRecipientMemberId).toList());
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required for MySQL integration tests");
        }
        return value;
    }

    /** 푸시 요청을 받아 적어 두기만 하는 대역이다. 실제 발송은 이 테스트의 관심사가 아니다. */
    private static final class RecordingPushSender implements PushSender {
        private final List<Notification> sent = new CopyOnWriteArrayList<>();

        @Override
        public void send(List<Notification> notifications) {
            sent.addAll(notifications);
        }

        List<Notification> sent() {
            return new ArrayList<>(sent);
        }
    }

    @Configuration
    @EnableTransactionManagement
    static class ServiceConfiguration {

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
