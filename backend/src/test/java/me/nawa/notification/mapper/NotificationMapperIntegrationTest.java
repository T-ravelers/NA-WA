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
                mapper.findByRecipient(fixture.guestMemberId(), null, 10);

            assertEquals(2, notifications.size());
            // 같은 시각에 들어가도 뒤에 넣은 것이 위로 온다.
            assertEquals("SETTLEMENT_COMPLETED", notifications.get(0).getNotificationType());
            assertEquals(fixture.gatheringName(), notifications.get(0).getGatheringName());
            assertEquals("KRW", notifications.get(0).getCurrencyCode());
            assertEquals(createdAt, notifications.get(0).getCreatedAt());
            assertNull(notifications.get(0).getReadAt());
            assertEquals(2, mapper.countUnreadByRecipient(fixture.guestMemberId()));

            // limit은 실제로 잘린다.
            assertEquals(1, mapper.findByRecipient(fixture.guestMemberId(), null, 1).size());

            // 남의 알림은 보이지 않는다.
            assertEquals(0, mapper.countUnreadByRecipient(fixture.payerMemberId()));

            LocalDateTime readAt = LocalDateTime.now().withNano(0);
            assertEquals(2, mapper.markAllRead(fixture.guestMemberId(), readAt));
            assertEquals(0, mapper.countUnreadByRecipient(fixture.guestMemberId()));
            assertEquals(
                readAt,
                mapper.findByRecipient(fixture.guestMemberId(), null, 10).get(0).getReadAt()
            );

            // 이미 읽은 알림을 다시 읽음 처리해도 건드릴 것이 없다.
            assertEquals(0, mapper.markAllRead(fixture.guestMemberId(), readAt));
        } finally {
            deleteFixture(fixture);
        }
    }

    /**
     * 커서가 다음 쪽을 이어 주는지 본다.
     *
     * 세 알림의 만든 시각이 모두 같아서 순서는 번호로만 갈린다. 알림은 보통 한 번에 여러
     * 건이 함께 들어가므로 이것이 가장 흔한 모양이다.
     */
    @Test
    void findByRecipient_cursorContinuesFromTheGivenNotification() {
        Fixture fixture = createFixture();
        try {
            LocalDateTime createdAt = LocalDateTime.now().withNano(0);
            mapper.insertNotifications(List.of(
                notification(fixture, "SETTLEMENT_REQUESTED", new BigDecimal("60"), createdAt),
                notification(fixture, "SETTLEMENT_PAID", new BigDecimal("30"), createdAt),
                notification(fixture, "SETTLEMENT_COMPLETED", new BigDecimal("100"), createdAt)
            ));

            List<Notification> firstPage =
                mapper.findByRecipient(fixture.guestMemberId(), null, 2);
            assertEquals(2, firstPage.size());

            Long cursor = firstPage.get(1).getNotificationId();
            List<Notification> secondPage =
                mapper.findByRecipient(fixture.guestMemberId(), cursor, 2);

            // 첫 쪽과 겹치지 않고, 남은 하나가 이어서 나온다.
            assertEquals(1, secondPage.size());
            assertEquals("SETTLEMENT_REQUESTED", secondPage.get(0).getNotificationType());

            // 마지막 알림을 커서로 주면 더 볼 것이 없다.
            assertEquals(
                0,
                mapper.findByRecipient(
                    fixture.guestMemberId(), secondPage.get(0).getNotificationId(), 2
                ).size()
            );
        } finally {
            deleteFixture(fixture);
        }
    }

    /**
     * 번호 순서와 시각 순서가 어긋나도 한 건도 빠뜨리지 않는지 본다.
     *
     * created_at은 DB가 아니라 앱이 넣는 값이라 번호와 순서가 늘 같지는 않다. 먼저 들어간
     * 행에 더 나중 시각을 적어 두 순서를 일부러 뒤집는다.
     *
     * 이 자리가 커서를 (시각, 번호) 짝으로 비교해야 하는 이유다. 번호 하나로만 비교하면
     * 오래된 쪽의 번호가 더 커서 조건에 걸리지 못하고, 그 알림은 어느 쪽에도 나오지 않는다.
     */
    @Test
    void findByRecipient_cursorLosesNothingWhenIdOrderDisagreesWithTimeOrder() {
        Fixture fixture = createFixture();
        try {
            LocalDateTime newer = LocalDateTime.now().withNano(0);
            mapper.insertNotifications(List.of(
                notification(fixture, "SETTLEMENT_REQUESTED", new BigDecimal("60"), newer)
            ));
            mapper.insertNotifications(List.of(
                notification(
                    fixture, "SETTLEMENT_PAID", new BigDecimal("30"), newer.minusHours(1)
                )
            ));

            List<Notification> firstPage =
                mapper.findByRecipient(fixture.guestMemberId(), null, 1);
            assertEquals(1, firstPage.size());
            assertEquals("SETTLEMENT_REQUESTED", firstPage.get(0).getNotificationType());

            List<Notification> secondPage = mapper.findByRecipient(
                fixture.guestMemberId(), firstPage.get(0).getNotificationId(), 1
            );

            // 번호는 더 크지만 시각이 더 오래된 알림이다. 여기서 빠지면 영영 볼 수 없다.
            assertEquals(1, secondPage.size());
            assertEquals("SETTLEMENT_PAID", secondPage.get(0).getNotificationType());
        } finally {
            deleteFixture(fixture);
        }
    }

    /**
     * 읽음·지우기는 알림 번호를 경로로 받는다. 수신자 조건이 빠지면 남의 알림 번호를 적어
     * 남의 알림을 읽음 처리하거나 지울 수 있게 되므로, 그 조건이 실제로 막는지 확인한다.
     */
    @Test
    void markReadAndSoftDelete_touchNothingWhenTheNotificationBelongsToSomeoneElse() {
        Fixture fixture = createFixture();
        try {
            LocalDateTime createdAt = LocalDateTime.now().withNano(0);
            mapper.insertNotifications(List.of(
                notification(fixture, "SETTLEMENT_REQUESTED", new BigDecimal("60"), createdAt)
            ));
            Long notificationId =
                mapper.findByRecipient(fixture.guestMemberId(), null, 1).get(0).getNotificationId();
            LocalDateTime now = LocalDateTime.now().withNano(0);

            // 남(원결제자)이 같은 번호를 적어 보내면 아무 행도 바뀌지 않는다.
            assertEquals(0, mapper.markRead(fixture.payerMemberId(), notificationId, now));
            assertEquals(0, mapper.softDelete(fixture.payerMemberId(), notificationId, now));
            assertEquals(1, mapper.countUnreadByRecipient(fixture.guestMemberId()));

            // 주인이 부르면 한 건이 바뀌고, 두 번째부터는 바꿀 것이 없다.
            assertEquals(1, mapper.markRead(fixture.guestMemberId(), notificationId, now));
            assertEquals(0, mapper.markRead(fixture.guestMemberId(), notificationId, now));
            assertEquals(0, mapper.countUnreadByRecipient(fixture.guestMemberId()));

            // 지우면 목록에서 빠진다. 행은 남아 있고 deleted_at만 적힌다.
            assertEquals(1, mapper.softDelete(fixture.guestMemberId(), notificationId, now));
            assertEquals(0, mapper.findByRecipient(fixture.guestMemberId(), null, 10).size());
            assertEquals(0, mapper.softDelete(fixture.guestMemberId(), notificationId, now));
        } finally {
            deleteFixture(fixture);
        }
    }

    @Test
    void softDeleteAll_emptiesOnlyTheCallersList() {
        Fixture fixture = createFixture();
        try {
            LocalDateTime createdAt = LocalDateTime.now().withNano(0);
            mapper.insertNotifications(List.of(
                notification(fixture, "SETTLEMENT_REQUESTED", new BigDecimal("60"), createdAt),
                notification(fixture, "SETTLEMENT_COMPLETED", new BigDecimal("100"), createdAt)
            ));

            // 남이 부르면 지울 것이 없다.
            assertEquals(
                0,
                mapper.softDeleteAll(fixture.payerMemberId(), LocalDateTime.now().withNano(0))
            );
            assertEquals(2, mapper.findByRecipient(fixture.guestMemberId(), null, 10).size());

            assertEquals(
                2,
                mapper.softDeleteAll(fixture.guestMemberId(), LocalDateTime.now().withNano(0))
            );
            assertEquals(0, mapper.findByRecipient(fixture.guestMemberId(), null, 10).size());
            assertEquals(0, mapper.countUnreadByRecipient(fixture.guestMemberId()));
        } finally {
            deleteFixture(fixture);
        }
    }

    /**
     * 남의 알림 번호를 커서로 넣어도 아무것도 알아낼 수 없는지 본다.
     *
     * 커서는 주소로 오는 값이라 남의 번호를 적어 보낼 수 있다. 커서가 가리키는 행을
     * 수신자로 좁혀 찾지 않으면, 그 남의 알림 시각을 기준으로 내 목록이 잘린다. 잘리는
     * 모양만 보고도 그 알림이 언제쯤 생겼는지를 되짚을 수 있다.
     */
    @Test
    void findByRecipient_othersCursorRevealsNothing() {
        Fixture fixture = createFixture();
        try {
            LocalDateTime older = LocalDateTime.now().withNano(0).minusHours(1);
            mapper.insertNotifications(List.of(
                notification(fixture, "SETTLEMENT_REQUESTED", new BigDecimal("60"), older)
            ));
            mapper.insertNotifications(List.of(
                notificationFor(
                    fixture, fixture.payerMemberId(), "SETTLEMENT_PAID",
                    new BigDecimal("30"), LocalDateTime.now().withNano(0)
                )
            ));
            Long othersCursor = mapper
                .findByRecipient(fixture.payerMemberId(), null, 10)
                .get(0)
                .getNotificationId();

            // 좁히지 않으면 남의 알림보다 오래된 내 알림이 그대로 나와, 그 시각을 되짚을 수
            // 있다. 없는 번호를 넣었을 때와 똑같이 빈 쪽이어야 한다.
            assertEquals(
                0,
                mapper.findByRecipient(fixture.guestMemberId(), othersCursor, 10).size()
            );
        } finally {
            deleteFixture(fixture);
        }
    }

    private static Notification notificationFor(
        Fixture fixture, Long recipientMemberId, String type, BigDecimal amount,
        LocalDateTime createdAt
    ) {
        return Notification.builder()
            .recipientMemberId(recipientMemberId)
            .notificationType(type)
            .settlementId(fixture.settlementId())
            .actorName(fixture.payerName())
            .gatheringName(fixture.gatheringName())
            .amount(amount)
            .currencyCode("KRW")
            .createdAt(createdAt)
            .build();
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
