package me.nawa.settlement.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import me.nawa.common.exception.BusinessException;
import me.nawa.config.MySqlSchemaExtension;
import me.nawa.settlement.dto.request.CreateSettlementRequest;
import me.nawa.settlement.dto.response.SettlementCreateResponse;
import me.nawa.settlement.exception.SettlementErrorCode;
import me.nawa.settlement.mapper.SettlementMapper;
import me.nawa.settlement.service.creation.EqualSettlementCreator;
import me.nawa.settlement.service.creation.ItemizedSettlementCreator;
import me.nawa.settlement.service.creation.SettlementCreationHandler;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@ExtendWith(MySqlSchemaExtension.class)
@EnabledIfEnvironmentVariable(
    named = "RUN_MYSQL_INTEGRATION_TESTS",
    matches = "(?i)true"
)
class SettlementCreationConcurrencyIntegrationTest {

    private static final long TIMEOUT_SECONDS = 10L;

    private static HikariDataSource dataSource;
    private static JdbcTemplate jdbcTemplate;
    private static AnnotationConfigApplicationContext applicationContext;
    private static SettlementCreationService creationService;
    private static volatile SourceAbsenceBarrier sourceAbsenceBarrier;

    @BeforeAll
    static void setUpDatabase() {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setDriverClassName(requiredEnvironment("DATABASE_DRIVER"));
        hikariConfig.setJdbcUrl(requiredEnvironment("DATABASE_URL"));
        hikariConfig.setUsername(requiredEnvironment("DATABASE_USERNAME"));
        hikariConfig.setPassword(requiredEnvironment("DATABASE_PASSWORD"));
        hikariConfig.setMaximumPoolSize(4);
        hikariConfig.setMinimumIdle(0);
        dataSource = new HikariDataSource(hikariConfig);
        jdbcTemplate = new JdbcTemplate(dataSource);
        applicationContext = new AnnotationConfigApplicationContext(ConcurrencyConfiguration.class);
        creationService = applicationContext.getBean(SettlementCreationService.class);
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

    @Test
    void createSettlement_sameKeyConcurrentRequestsReturnOneWinnerAfterUniqueConflict()
        throws Exception {
        Fixture fixture = createFixture();
        try {
            assertTrue(AopUtils.isAopProxy(
                applicationContext.getBean(SettlementCreationAttemptService.class)
            ));
            String idempotencyKey = "same-key-" + UUID.randomUUID();

            List<CreationOutcome> outcomes = createConcurrently(
                fixture,
                idempotencyKey,
                idempotencyKey
            );

            List<Long> settlementIds = outcomes.stream().map(outcome -> {
                assertEquals(null, outcome.failure());
                return outcome.response().getId();
            }).toList();
            assertEquals(1L, settlementIds.stream().distinct().count());
            assertEquals(1, countSettlements(fixture.transferId()));
        } finally {
            deleteFixture(fixture);
        }
    }

    @Test
    void createSettlement_differentKeysConcurrentRequestsReturnSourceAlreadyUsedForLoser()
        throws Exception {
        Fixture fixture = createFixture();
        try {
            List<CreationOutcome> outcomes = createConcurrently(
                fixture,
                "first-key-" + UUID.randomUUID(),
                "second-key-" + UUID.randomUUID()
            );

            assertEquals(1L, outcomes.stream().filter(CreationOutcome::succeeded).count());
            BusinessException conflict = assertInstanceOf(
                BusinessException.class,
                outcomes.stream().map(CreationOutcome::failure)
                    .filter(failure -> failure != null)
                    .findFirst()
                    .orElseThrow()
            );
            assertEquals(SettlementErrorCode.SETTLEMENT_SOURCE_ALREADY_USED, conflict.getErrorCode());
            assertEquals("SETTLEMENT-010", conflict.getErrorCode().getCode());
            assertEquals(1, countSettlements(fixture.transferId()));
        } finally {
            deleteFixture(fixture);
        }
    }

    private static List<CreationOutcome> createConcurrently(
        Fixture fixture,
        String firstKey,
        String secondKey
    ) throws Exception {
        sourceAbsenceBarrier = new SourceAbsenceBarrier();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<CreationOutcome> first = executor.submit(createTask(
                fixture, firstKey, ready, start
            ));
            Future<CreationOutcome> second = executor.submit(createTask(
                fixture, secondKey, ready, start
            ));

            assertTrue(ready.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            start.countDown();
            assertTrue(sourceAbsenceBarrier.awaitBothChecks());
            sourceAbsenceBarrier.release();
            return List.of(
                first.get(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                second.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            );
        } finally {
            start.countDown();
            if (sourceAbsenceBarrier != null) {
                sourceAbsenceBarrier.release();
            }
            executor.shutdownNow();
            executor.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }

    private static Callable<CreationOutcome> createTask(
        Fixture fixture,
        String idempotencyKey,
        CountDownLatch ready,
        CountDownLatch start
    ) {
        return () -> {
            ready.countDown();
            if (!start.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent creation start timed out");
            }
            try {
                return new CreationOutcome(creationService.createSettlement(
                    fixture.payerMemberId(),
                    fixture.appointmentId(),
                    idempotencyKey,
                    equalRequest(fixture)
                ), null);
            } catch (Throwable throwable) {
                return new CreationOutcome(null, throwable);
            }
        };
    }

    private static CreateSettlementRequest equalRequest(Fixture fixture) {
        CreateSettlementRequest request = new CreateSettlementRequest();
        request.setSourceTransferId(fixture.transferId());
        request.setType("EQUAL");
        request.setParticipantAppointmentMemberIds(List.of(
            fixture.payerAppointmentMemberId(), fixture.payeeAppointmentMemberId()
        ));
        return request;
    }

    private static int countSettlements(long transferId) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM settlements WHERE source_transfer_id = ?",
            Integer.class,
            transferId
        );
    }

    private static Fixture createFixture() {
        String marker = "st-" + UUID.randomUUID();
        long payerMemberId = insert("members", "member_id", Map.of("display_name", marker + "-payer"));
        long payeeMemberId = insert("members", "member_id", Map.of("display_name", marker + "-payee"));
        long tripId = insert("trips", "trip_id", Map.of(
            "member_id", payerMemberId,
            "title", marker,
            "start_date", LocalDate.now().minusDays(1),
            "end_date", LocalDate.now()
        ));
        long itemId = insert("explore_items", "item_id", Map.of(
            "created_by", payerMemberId,
            "reviewed_by", payerMemberId,
            "item_type", "PLACE",
            "approval_status", "APPROVED",
            "visibility_status", "VISIBLE",
            "reviewed_at", LocalDateTime.now()
        ));
        long appointmentId = insert("appointments", "appointment_id", Map.of(
            "item_id", itemId,
            "host_member_id", payerMemberId,
            "language_code", "en",
            "appointment_name", marker,
            "max_members", 2,
            "join_deadline", LocalDateTime.now().minusDays(2),
            "deposit_amount", new BigDecimal("5000"),
            "activity_start_at", LocalDateTime.now().minusHours(2),
            "activity_end_at", LocalDateTime.now().minusHours(1)
        ));
        long payerAppointmentMemberId = insert("appointment_members", "appointment_member_id", Map.of(
            "appointment_id", appointmentId,
            "member_id", payerMemberId,
            "trip_id", tripId,
            "membership_status", "ACTIVE"
        ));
        long payeeAppointmentMemberId = insert("appointment_members", "appointment_member_id", Map.of(
            "appointment_id", appointmentId,
            "member_id", payeeMemberId,
            "membership_status", "ACTIVE"
        ));
        long walletOwnerId = insert("wallet_owners", "wallet_owner_id", Map.of(
            "member_id", payerMemberId,
            "owner_type", "MEMBER"
        ));
        long walletId = insert("wallets", "wallet_id", Map.of(
            "wallet_owner_id", walletOwnerId,
            "currency_code", "KRW"
        ));
        long transferId = insert("wallet_transfers", "transfer_id", Map.of(
            "currency_code", "KRW",
            "initiator_member_id", payerMemberId,
            "transfer_number", marker,
            "transfer_type", "QR_PAYMENT",
            "transfer_status", "COMPLETED",
            "amount", new BigDecimal("100"),
            "completed_at", LocalDateTime.now()
        ));
        long ledgerEntryId = insert("wallet_ledger_entries", "ledger_entry_id", Map.of(
            "transfer_id", transferId,
            "wallet_id", walletId,
            "entry_type", "DEBIT",
            "amount", new BigDecimal("100"),
            "balance_after", BigDecimal.ZERO
        ));
        jdbcTemplate.update(
            "INSERT INTO trip_expense_links (trip_id, ledger_entry_id, appointment_member_id) VALUES (?, ?, ?)",
            tripId,
            ledgerEntryId,
            payerAppointmentMemberId
        );
        return new Fixture(
            payerMemberId,
            payeeMemberId,
            tripId,
            itemId,
            appointmentId,
            payerAppointmentMemberId,
            payeeAppointmentMemberId,
            walletOwnerId,
            walletId,
            transferId,
            ledgerEntryId
        );
    }

    private static long insert(String table, String keyColumn, Map<String, ?> values) {
        return new SimpleJdbcInsert(dataSource)
            .withTableName(table)
            .usingColumns(values.keySet().toArray(String[]::new))
            .usingGeneratedKeyColumns(keyColumn)
            .executeAndReturnKey(values)
            .longValue();
    }

    private static void deleteFixture(Fixture fixture) {
        jdbcTemplate.update("DELETE FROM settlement_members WHERE settlement_id IN (SELECT settlement_id FROM settlements WHERE source_transfer_id = ?)", fixture.transferId());
        jdbcTemplate.update("DELETE FROM settlements WHERE source_transfer_id = ?", fixture.transferId());
        jdbcTemplate.update("DELETE FROM trip_expense_links WHERE ledger_entry_id = ?", fixture.ledgerEntryId());
        jdbcTemplate.update("DELETE FROM wallet_ledger_entries WHERE ledger_entry_id = ?", fixture.ledgerEntryId());
        jdbcTemplate.update("DELETE FROM wallet_transfers WHERE transfer_id = ?", fixture.transferId());
        jdbcTemplate.update("DELETE FROM wallets WHERE wallet_id = ?", fixture.walletId());
        jdbcTemplate.update("DELETE FROM wallet_owners WHERE wallet_owner_id = ?", fixture.walletOwnerId());
        jdbcTemplate.update("DELETE FROM appointment_members WHERE appointment_id = ?", fixture.appointmentId());
        jdbcTemplate.update("DELETE FROM appointments WHERE appointment_id = ?", fixture.appointmentId());
        jdbcTemplate.update("DELETE FROM explore_items WHERE item_id = ?", fixture.itemId());
        jdbcTemplate.update("DELETE FROM trips WHERE trip_id = ?", fixture.tripId());
        jdbcTemplate.update("DELETE FROM members WHERE member_id IN (?, ?)", fixture.payerMemberId(), fixture.payeeMemberId());
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
    static class ConcurrencyConfiguration {

        @Bean
        DataSourceTransactionManager transactionManager() {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        SqlSessionFactory sqlSessionFactory() throws Exception {
            SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
            factoryBean.setDataSource(dataSource);
            factoryBean.setConfigLocation(new ClassPathResource("mybatis-config.xml"));
            factoryBean.setMapperLocations(new ClassPathResource(
                "me/nawa/settlement/mapper/SettlementMapper.xml"
            ));
            return factoryBean.getObject();
        }

        @Bean
        SettlementMapper settlementMapper(SqlSessionFactory sqlSessionFactory) {
            SettlementMapper target = new SqlSessionTemplate(sqlSessionFactory)
                .getMapper(SettlementMapper.class);
            return (SettlementMapper) Proxy.newProxyInstance(
                SettlementMapper.class.getClassLoader(),
                new Class<?>[]{SettlementMapper.class},
                new SourceAbsenceBarrierMapper(target)
            );
        }

        @Bean
        SettlementAmountAllocator settlementAmountAllocator() {
            return new SettlementAmountAllocator();
        }

        @Bean
        EqualSettlementCreator equalSettlementCreator(
            SettlementMapper settlementMapper,
            SettlementAmountAllocator settlementAmountAllocator
        ) {
            return new EqualSettlementCreator(settlementMapper, settlementAmountAllocator);
        }

        @Bean
        ItemizedSettlementCreator itemizedSettlementCreator(SettlementMapper settlementMapper) {
            return new ItemizedSettlementCreator(settlementMapper);
        }

        @Bean
        SettlementCreationAttemptService settlementCreationAttemptService(
            SettlementMapper settlementMapper,
            List<SettlementCreationHandler> handlers
        ) {
            return new SettlementCreationAttemptService(
                settlementMapper, handlers, noOpReceiptService()
            );
        }

        /*
         * 이 테스트는 원거래 하나를 두고 두 스레드가 경쟁할 때 한쪽만 성공하는지를 본다.
         * 영수증은 이 경쟁과 무관하고 요청에도 담기지 않으므로 아무 일도 하지 않는 대역을 쓴다.
         */
        private SettlementReceiptService noOpReceiptService() {
            return new SettlementReceiptService() {
                @Override
                public me.nawa.settlement.dto.response.SettlementReceiptUploadResponse upload(
                    Long memberId, String declaredContentType, byte[] content
                ) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void linkToSettlement(Long memberId, Long settlementId, Long receiptId) {
                }

                @Override
                public me.nawa.common.storage.StoredReceipt getReceipt(
                    Long memberId, Long settlementId
                ) {
                    throw new UnsupportedOperationException();
                }
            };
        }

        @Bean
        SettlementCreationService settlementCreationService(
            SettlementMapper settlementMapper,
            SettlementCreationAttemptService settlementCreationAttemptService
        ) {
            return new SettlementCreationServiceImpl(settlementMapper, settlementCreationAttemptService);
        }
    }

    private static final class SourceAbsenceBarrierMapper implements InvocationHandler {
        private final SettlementMapper target;

        private SourceAbsenceBarrierMapper(SettlementMapper target) {
            this.target = target;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) throws Throwable {
            Object result = invokeTarget(method, arguments);
            if ("findBySourceTransferId".equals(method.getName()) && result == null) {
                SourceAbsenceBarrier barrier = sourceAbsenceBarrier;
                if (barrier != null) {
                    barrier.awaitReleaseAfterCheck();
                }
            }
            return result;
        }

        private Object invokeTarget(Method method, Object[] arguments) throws Throwable {
            try {
                return method.invoke(target, arguments);
            } catch (InvocationTargetException exception) {
                throw exception.getCause();
            }
        }
    }

    private static final class SourceAbsenceBarrier {
        private final CountDownLatch bothChecks = new CountDownLatch(2);
        private final CountDownLatch release = new CountDownLatch(1);

        private boolean awaitBothChecks() throws InterruptedException {
            return bothChecks.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }

        private void awaitReleaseAfterCheck() throws InterruptedException {
            bothChecks.countDown();
            if (!release.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent source absence barrier timed out");
            }
        }

        private void release() {
            release.countDown();
        }
    }

    private record CreationOutcome(SettlementCreateResponse response, Throwable failure) {
        private boolean succeeded() {
            return response != null;
        }
    }

    private record Fixture(
        long payerMemberId,
        long payeeMemberId,
        long tripId,
        long itemId,
        long appointmentId,
        long payerAppointmentMemberId,
        long payeeAppointmentMemberId,
        long walletOwnerId,
        long walletId,
        long transferId,
        long ledgerEntryId
    ) {
    }
}
