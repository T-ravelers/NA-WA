package me.nawa.wallet.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import me.nawa.wallet.domain.enums.SpendingScope;
import me.nawa.wallet.dto.request.QrPaymentExecuteRequest;
import me.nawa.wallet.dto.response.QrPaymentExecuteResponse;
import me.nawa.wallet.mapper.QrPaymentCodeMapper;
import me.nawa.wallet.mapper.TripExpenseLinkMapper;
import me.nawa.wallet.mapper.WalletLedgerMapper;
import me.nawa.wallet.mapper.WalletMapper;
import me.nawa.wallet.mapper.WalletTransferMapper;
import me.nawa.wallet.util.QrTokenGenerator;
import me.nawa.wallet.util.TransactionNumberGenerator;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 같은 Idempotency-Key로 동시에 두 번 들어온 QR 결제 실행 요청이, 실제 MySQL의
 * REPEATABLE READ 아래에서도 이체를 하나만 만들고 나머지는 그 결과를 그대로 돌려주는지 검증한다.
 *
 * 단위 테스트(QrPaymentServiceImplTest)는 Mockito로 findByIdempotencyKeyForUpdate의 반환값을
 * 순서대로 흉내내므로, 일반 SELECT가 REPEATABLE READ 스냅샷에 묶여 동시 커밋을 못 보는 실제
 * 격리 수준 문제는 재현하지 못한다. 여기서는 두 스레드가 각자의 트랜잭션(각자의 커넥션)으로
 * 진짜 동시에 executePayment를 호출해, DB 잠금이 실제로 직렬화하는지를 본다.
 */
@EnabledIfEnvironmentVariable(
    named = "RUN_MYSQL_INTEGRATION_TESTS",
    matches = "(?i)true"
)
class QrPaymentConcurrencyIntegrationTest {

    private static HikariDataSource dataSource;
    private static WalletMapper walletMapper;
    private static QrPaymentCodeMapper qrPaymentCodeMapper;
    private static WalletTransferMapper walletTransferMapper;
    private static WalletLedgerMapper walletLedgerMapper;
    private static TripExpenseLinkMapper tripExpenseLinkMapper;
    private static TransactionTemplate transactionTemplate;
    private static JdbcTemplate jdbcTemplate;
    private static QrPaymentServiceImpl qrPaymentService;

    private Long payerMemberId;
    private Long payeeMemberId;

    @BeforeAll
    static void setUpDatabase() throws Exception {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setDriverClassName(requiredEnvironment("DATABASE_DRIVER"));
        hikariConfig.setJdbcUrl(requiredEnvironment("DATABASE_URL"));
        hikariConfig.setUsername(requiredEnvironment("DATABASE_USERNAME"));
        hikariConfig.setPassword(requiredEnvironment("DATABASE_PASSWORD"));
        // 두 스레드가 동시에 각자의 트랜잭션(커넥션)을 쥐어야 하므로 여유 있게 잡는다
        hikariConfig.setMaximumPoolSize(4);
        hikariConfig.setMinimumIdle(0);
        dataSource = new HikariDataSource(hikariConfig);

        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setConfigLocation(new ClassPathResource("mybatis-config.xml"));
        SqlSessionFactory sqlSessionFactory = factoryBean.getObject();
        sqlSessionFactory.getConfiguration().addMapper(WalletMapper.class);
        sqlSessionFactory.getConfiguration().addMapper(QrPaymentCodeMapper.class);
        sqlSessionFactory.getConfiguration().addMapper(WalletTransferMapper.class);
        sqlSessionFactory.getConfiguration().addMapper(WalletLedgerMapper.class);
        sqlSessionFactory.getConfiguration().addMapper(TripExpenseLinkMapper.class);

        SqlSessionTemplate sqlSessionTemplate = new SqlSessionTemplate(sqlSessionFactory);
        walletMapper = sqlSessionTemplate.getMapper(WalletMapper.class);
        qrPaymentCodeMapper = sqlSessionTemplate.getMapper(QrPaymentCodeMapper.class);
        walletTransferMapper = sqlSessionTemplate.getMapper(WalletTransferMapper.class);
        walletLedgerMapper = sqlSessionTemplate.getMapper(WalletLedgerMapper.class);
        tripExpenseLinkMapper = sqlSessionTemplate.getMapper(TripExpenseLinkMapper.class);

        transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        jdbcTemplate = new JdbcTemplate(dataSource);

        qrPaymentService = new QrPaymentServiceImpl(
            walletMapper,
            qrPaymentCodeMapper,
            new QrTokenGenerator(),
            walletTransferMapper,
            walletLedgerMapper,
            new TransactionNumberGenerator(),
            tripExpenseLinkMapper
        );
    }

    @AfterAll
    static void closeDatabase() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @AfterEach
    void cleanUpTestData() {
        // 동시성을 실제로 보려면 각 스레드가 커밋해야 하므로, 다른 통합 테스트처럼
        // setRollbackOnly()로 되돌릴 수 없다 — 만든 행을 직접 지운다.
        if (payerMemberId == null && payeeMemberId == null) {
            return;
        }

        jdbcTemplate.update(
            "DELETE tel FROM trip_expense_links tel"
                + " JOIN wallet_ledger_entries e ON e.ledger_entry_id = tel.ledger_entry_id"
                + " JOIN wallets w ON w.wallet_id = e.wallet_id"
                + " JOIN wallet_owners o ON o.wallet_owner_id = w.wallet_owner_id"
                + " WHERE o.member_id IN (?, ?)",
            payerMemberId, payeeMemberId
        );
        jdbcTemplate.update(
            "DELETE e FROM wallet_ledger_entries e"
                + " JOIN wallets w ON w.wallet_id = e.wallet_id"
                + " JOIN wallet_owners o ON o.wallet_owner_id = w.wallet_owner_id"
                + " WHERE o.member_id IN (?, ?)",
            payerMemberId, payeeMemberId
        );
        // qr_payment_codes.completed_transfer_id가 wallet_transfers를 참조하므로,
        // wallet_transfers를 지우기 전에 먼저 지워야 한다.
        jdbcTemplate.update(
            "DELETE q FROM qr_payment_codes q"
                + " JOIN wallets w ON w.wallet_id = q.payee_wallet_id"
                + " JOIN wallet_owners o ON o.wallet_owner_id = w.wallet_owner_id"
                + " WHERE o.member_id = ?",
            payeeMemberId
        );
        jdbcTemplate.update(
            "DELETE t FROM wallet_transfers t"
                + " WHERE t.initiator_member_id IN (?, ?)",
            payerMemberId, payeeMemberId
        );
        jdbcTemplate.update(
            "DELETE w FROM wallets w"
                + " JOIN wallet_owners o ON o.wallet_owner_id = w.wallet_owner_id"
                + " WHERE o.member_id IN (?, ?)",
            payerMemberId, payeeMemberId
        );
        jdbcTemplate.update(
            "DELETE FROM wallet_owners WHERE member_id IN (?, ?)",
            payerMemberId, payeeMemberId
        );
        jdbcTemplate.update(
            "DELETE FROM members WHERE member_id IN (?, ?)",
            payerMemberId, payeeMemberId
        );
    }

    @Test
    void executePayment_createsExactlyOneTransfer_whenSameIdempotencyKeyRequestedConcurrently()
        throws Exception {
        payerMemberId = createMember("결제자");
        payeeMemberId = createMember("수취인");
        long payerWalletId = createActiveWallet(payerMemberId, new BigDecimal("100000.0000"));
        long payeeWalletId = createActiveWallet(payeeMemberId, BigDecimal.ZERO);
        String qrToken = "concurrency-test-" + UUID.randomUUID();
        createActiveQr(qrToken, payeeWalletId, new BigDecimal("10000.0000"));

        String idempotencyKey = "concurrency-idem-" + UUID.randomUUID();
        QrPaymentExecuteRequest request =
            new QrPaymentExecuteRequest(qrToken, null, SpendingScope.PERSONAL, null);

        Callable<QrPaymentExecuteResponse> attempt = () -> transactionTemplate.execute(
            status -> qrPaymentService.executePayment(payerMemberId, idempotencyKey, request)
        );

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<QrPaymentExecuteResponse> first = executor.submit(attempt);
            Future<QrPaymentExecuteResponse> second = executor.submit(attempt);

            QrPaymentExecuteResponse firstResponse = first.get(10, TimeUnit.SECONDS);
            QrPaymentExecuteResponse secondResponse = second.get(10, TimeUnit.SECONDS);

            assertEquals(
                firstResponse.transferId(),
                secondResponse.transferId(),
                "동시 재요청은 같은 결제(transferId)를 돌려줘야 한다 — 이체가 두 번 생기면 안 된다"
            );
            assertEquals(0, firstResponse.amount().compareTo(secondResponse.amount()));
            assertEquals(0, firstResponse.balanceAfter().compareTo(secondResponse.balanceAfter()));
        } finally {
            executor.shutdown();
        }

        Integer transferCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM wallet_transfers WHERE idempotency_key = ?",
            Integer.class,
            idempotencyKey
        );
        assertEquals(1, transferCount, "같은 키로 만들어진 이체는 정확히 1건이어야 한다");

        BigDecimal payerBalance = jdbcTemplate.queryForObject(
            "SELECT available_balance FROM wallets WHERE wallet_id = ?",
            BigDecimal.class,
            payerWalletId
        );
        assertEquals(
            0,
            new BigDecimal("90000.0000").compareTo(payerBalance),
            "결제자 잔액은 10000원 딱 한 번만 차감돼야 한다 — 중복 차감이면 실패한다"
        );
    }

    private long createMember(String displayName) {
        jdbcTemplate.update(
            "INSERT INTO members (display_name, preferred_language, member_status)"
                + " VALUES (?, 'en', 'ACTIVE')",
            displayName
        );
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private long createActiveWallet(long memberId, BigDecimal balance) {
        jdbcTemplate.update(
            "INSERT INTO wallet_owners (member_id, owner_type) VALUES (?, 'MEMBER')",
            memberId
        );
        long walletOwnerId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        jdbcTemplate.update(
            "INSERT INTO wallets (wallet_owner_id, currency_code, available_balance, wallet_status)"
                + " VALUES (?, 'KRW', ?, 'ACTIVE')",
            walletOwnerId, balance
        );
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private void createActiveQr(String qrToken, long payeeWalletId, BigDecimal amount) {
        jdbcTemplate.update(
            "INSERT INTO qr_payment_codes"
                + " (payee_wallet_id, qr_token, amount, memo, payment_status, expires_at)"
                + " VALUES (?, ?, ?, '동시성 테스트', 'ACTIVE', ?)",
            payeeWalletId, qrToken, amount, LocalDateTime.now().plusMinutes(5)
        );
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required for MySQL integration tests");
        }
        return value;
    }
}
