package me.nawa.notification.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import me.nawa.config.MySqlSchemaExtension;
import me.nawa.notification.domain.Notification;
import me.nawa.notification.domain.SettlementMemberShare;
import me.nawa.notification.domain.SettlementNotificationSnapshot;
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
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;

@ExtendWith(MySqlSchemaExtension.class)
@EnabledIfEnvironmentVariable(
    named = "RUN_MYSQL_INTEGRATION_TESTS",
    matches = "(?i)true"
)
class NotificationMapperIntegrationTest {

    private static HikariDataSource dataSource;
    private static JdbcTemplate jdbcTemplate;
    private static NotificationMapper mapper;

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
            new ClassPathResource("me/nawa/notification/mapper/NotificationMapper.xml")
        );
        SqlSessionFactory sqlSessionFactory = factoryBean.getObject();
        if (!sqlSessionFactory.getConfiguration().hasMapper(NotificationMapper.class)) {
            sqlSessionFactory.getConfiguration().addMapper(NotificationMapper.class);
        }
        mapper = new SqlSessionTemplate(sqlSessionFactory).getMapper(NotificationMapper.class);
    }

    @AfterAll
    static void closeDatabase() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    /**
     * 알림 문장에 넣을 값을 정산 하나에서 다 읽어 오는지 본다.
     *
     * 통화는 settlements에 없고 원거래에만 있어서 조인을 한 번 더 탄다. 이 조인이 빠지면
     * 금액은 나오는데 단위가 사라진다.
     */
    @Test
    void findSettlementSnapshot_readsPayerGatheringTotalAndCurrencyFromSourceTransfer() {
        Fixture fixture = createFixture();
        try {
            SettlementNotificationSnapshot snapshot =
                mapper.findSettlementSnapshot(fixture.settlementId());

            assertNotNull(snapshot);
            assertEquals(fixture.payerMemberId(), snapshot.getPayerMemberId());
            assertEquals(fixture.payerName(), snapshot.getPayerName());
            assertEquals(fixture.gatheringName(), snapshot.getGatheringName());
            assertEquals(0, new BigDecimal("100").compareTo(snapshot.getTotalAmount()));
            assertEquals("KRW", snapshot.getCurrencyCode());
        } finally {
            deleteFixture(fixture);
        }
    }

    /**
     * 원결제자 본인 몫 행은 청구 대상이 아니라 목록에서 빠진다.
     *
     * 정산 상세의 납부 현황(findCollectionMembers)과 같은 기준이다. 두 곳이 어긋나면
     * 화면에 안 보이는 사람에게 알림만 가거나 그 반대가 된다.
     */
    @Test
    void findSettlementMemberShares_excludesThePayersOwnRow() {
        Fixture fixture = createFixture();
        try {
            List<SettlementMemberShare> shares =
                mapper.findSettlementMemberShares(fixture.settlementId());

            assertEquals(1, shares.size());
            assertEquals(fixture.guestMemberId(), shares.get(0).getMemberId());
            assertEquals(fixture.guestName(), shares.get(0).getDisplayName());
            assertEquals(0, new BigDecimal("60").compareTo(shares.get(0).getShareAmount()));
            assertEquals("PENDING", shares.get(0).getRequestStatus());
        } finally {
            deleteFixture(fixture);
        }
    }

    /**
     * 적재 → 최신순 조회 → 미읽음 개수 → 읽음 처리까지 한 번에 따라간다.
     *
     * 만든 시각과 읽은 시각 **둘 다** DB에게 묻지 않고 애플리케이션이 넘긴 값이 그대로
     * 들어가야 한다. CI는 MySQL을 일부러 UTC로 띄우므로, DB 시계를 쓰면 여기서 9시간이
     * 어긋나 드러난다. 만든 시각은 화면에 그대로 보이는 값이라 특히 어긋나면 안 된다.
     */
    @Test
    void insertReadAndCount_followTheRecipientAndStoreTimeGivenByApplication() {
        Fixture fixture = createFixture();
        try {
            LocalDateTime createdAt = LocalDateTime.now().withNano(0);
            mapper.insertNotifications(List.of(
                notification(fixture, "SETTLEMENT_REQUESTED", new BigDecimal("60"), createdAt),
                notification(fixture, "SETTLEMENT_COMPLETED", new BigDecimal("100"), createdAt)
            ));

            List<Notification> notifications =
                mapper.findByRecipient(fixture.guestMemberId(), 10);

            assertEquals(2, notifications.size());
            // 같은 시각에 들어가도 뒤에 넣은 것이 위로 온다.
            assertEquals("SETTLEMENT_COMPLETED", notifications.get(0).getNotificationType());
            assertEquals(fixture.gatheringName(), notifications.get(0).getGatheringName());
            assertEquals("KRW", notifications.get(0).getCurrencyCode());
            assertEquals(createdAt, notifications.get(0).getCreatedAt());
            assertNull(notifications.get(0).getReadAt());
            assertEquals(2, mapper.countUnreadByRecipient(fixture.guestMemberId()));

            // limit은 실제로 잘린다.
            assertEquals(1, mapper.findByRecipient(fixture.guestMemberId(), 1).size());

            // 남의 알림은 보이지 않는다.
            assertEquals(0, mapper.countUnreadByRecipient(fixture.payerMemberId()));

            LocalDateTime readAt = LocalDateTime.now().withNano(0);
            assertEquals(2, mapper.markAllRead(fixture.guestMemberId(), readAt));
            assertEquals(0, mapper.countUnreadByRecipient(fixture.guestMemberId()));
            assertEquals(
                readAt,
                mapper.findByRecipient(fixture.guestMemberId(), 10).get(0).getReadAt()
            );

            // 이미 읽은 알림을 다시 읽음 처리해도 건드릴 것이 없다.
            assertEquals(0, mapper.markAllRead(fixture.guestMemberId(), readAt));
        } finally {
            deleteFixture(fixture);
        }
    }

    private static Notification notification(
        Fixture fixture, String type, BigDecimal amount, LocalDateTime createdAt
    ) {
        return Notification.builder()
            .recipientMemberId(fixture.guestMemberId())
            .notificationType(type)
            .settlementId(fixture.settlementId())
            .actorName(fixture.payerName())
            .gatheringName(fixture.gatheringName())
            .amount(amount)
            .currencyCode("KRW")
            .createdAt(createdAt)
            .build();
    }

    private static Fixture createFixture() {
        // members.display_name과 wallet_transfers.transfer_number가 둘 다 VARCHAR(50)이다.
        // 접두사를 길게 붙이면 UUID 36자와 합쳐 잘려 나가므로 짧게 유지한다.
        String unique = UUID.randomUUID().toString();
        String marker = "nit-" + unique;
        String payerName = "nit-p-" + unique;
        String guestName = "nit-g-" + unique;
        long payerMemberId = insert("members", "member_id", Map.of("display_name", payerName));
        long guestMemberId = insert("members", "member_id", Map.of("display_name", guestName));
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
            "deposit_amount", new BigDecimal("5000"),
            "activity_start_at", LocalDateTime.now().minusHours(2),
            "activity_end_at", LocalDateTime.now().minusHours(1)
        ));
        long payerAppointmentMemberId = insert("appointment_members", "appointment_member_id", Map.of(
            "appointment_id", appointmentId,
            "member_id", payerMemberId
        ));
        long guestAppointmentMemberId = insert("appointment_members", "appointment_member_id", Map.of(
            "appointment_id", appointmentId,
            "member_id", guestMemberId
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
        // chk_settlements_receivable이 total = payer_share + receivable까지 확인한다.
        // 세 값을 따로 두면 통과하지 못하므로 원결제자 몫 40과 받을 돈 60으로 나눠 맞춘다.
        // Map.of는 열 쌍까지라 여기서만 ofEntries를 쓴다.
        long settlementId = insert("settlements", "settlement_id", Map.ofEntries(
            Map.entry("appointment_id", appointmentId),
            Map.entry("created_by_member_id", payerMemberId),
            Map.entry("payer_member_id", payerMemberId),
            Map.entry("source_transfer_id", transferId),
            Map.entry("idempotency_key", marker),
            Map.entry("request_fingerprint", "a".repeat(64)),
            Map.entry("settlement_status", "REQUESTED"),
            Map.entry("split_method", "EQUAL"),
            Map.entry("total_amount", new BigDecimal("100")),
            Map.entry("payer_share_amount", new BigDecimal("40")),
            Map.entry("receivable_amount", new BigDecimal("60"))
        ));
        insert("settlement_members", "settlement_member_id", Map.of(
            "settlement_id", settlementId,
            "appointment_member_id", payerAppointmentMemberId,
            "share_amount", new BigDecimal("40"),
            "request_status", "NOT_REQUESTED"
        ));
        insert("settlement_members", "settlement_member_id", Map.of(
            "settlement_id", settlementId,
            "appointment_member_id", guestAppointmentMemberId,
            "share_amount", new BigDecimal("60"),
            "request_status", "PENDING"
        ));
        return new Fixture(
            payerMemberId, payerName, guestMemberId, guestName, itemId, appointmentId,
            payerAppointmentMemberId, guestAppointmentMemberId, transferId, settlementId, marker
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
        // 알림은 정산과 회원을 가리키므로 그 둘보다 먼저 지운다.
        jdbcTemplate.update("DELETE FROM notifications WHERE settlement_id = ?", fixture.settlementId());
        jdbcTemplate.update("DELETE FROM settlement_members WHERE settlement_id = ?", fixture.settlementId());
        jdbcTemplate.update("DELETE FROM settlements WHERE settlement_id = ?", fixture.settlementId());
        jdbcTemplate.update("DELETE FROM wallet_transfers WHERE transfer_id = ?", fixture.transferId());
        jdbcTemplate.update(
            "DELETE FROM appointment_members WHERE appointment_member_id IN (?, ?)",
            fixture.payerAppointmentMemberId(), fixture.guestAppointmentMemberId()
        );
        jdbcTemplate.update("DELETE FROM appointments WHERE appointment_id = ?", fixture.appointmentId());
        jdbcTemplate.update("DELETE FROM explore_items WHERE item_id = ?", fixture.itemId());
        jdbcTemplate.update(
            "DELETE FROM members WHERE member_id IN (?, ?)",
            fixture.payerMemberId(), fixture.guestMemberId()
        );
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required");
        }
        return value;
    }

    private record Fixture(
        long payerMemberId,
        String payerName,
        long guestMemberId,
        String guestName,
        long itemId,
        long appointmentId,
        long payerAppointmentMemberId,
        long guestAppointmentMemberId,
        long transferId,
        long settlementId,
        String gatheringName
    ) {
    }
}
