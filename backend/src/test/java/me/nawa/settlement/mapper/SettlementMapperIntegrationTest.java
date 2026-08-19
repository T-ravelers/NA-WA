package me.nawa.settlement.mapper;

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
import me.nawa.settlement.domain.Settlement;
import me.nawa.settlement.domain.SettlementItem;
import me.nawa.settlement.domain.SettlementItemShare;
import me.nawa.settlement.domain.SettlementMember;
import me.nawa.settlement.domain.SettlementReceipt;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;

@EnabledIfEnvironmentVariable(
    named = "RUN_MYSQL_INTEGRATION_TESTS",
    matches = "(?i)true"
)
class SettlementMapperIntegrationTest {

    private static HikariDataSource dataSource;
    private static JdbcTemplate jdbcTemplate;
    private static SettlementMapper mapper;
    private static SettlementReceiptMapper receiptMapper;

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
            new ClassPathResource("me/nawa/settlement/mapper/SettlementMapper.xml"),
            new ClassPathResource("me/nawa/settlement/mapper/SettlementReceiptMapper.xml")
        );
        SqlSessionFactory sqlSessionFactory = factoryBean.getObject();
        if (!sqlSessionFactory.getConfiguration().hasMapper(SettlementMapper.class)) {
            sqlSessionFactory.getConfiguration().addMapper(SettlementMapper.class);
        }
        if (!sqlSessionFactory.getConfiguration().hasMapper(SettlementReceiptMapper.class)) {
            sqlSessionFactory.getConfiguration().addMapper(SettlementReceiptMapper.class);
        }
        SqlSessionTemplate sqlSession = new SqlSessionTemplate(sqlSessionFactory);
        mapper = sqlSession.getMapper(SettlementMapper.class);
        receiptMapper = sqlSession.getMapper(SettlementReceiptMapper.class);
    }

    @AfterAll
    static void closeDatabase() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Test
    void insertSettlement_requested_persistsV9CreationColumns() {
        Fixture fixture = createFixture();
        try {
            Settlement settlement = settlement(fixture);
            mapper.insertSettlement(settlement);

            assertEquals("REQUESTED", jdbcTemplate.queryForObject(
                "SELECT settlement_status FROM settlements WHERE settlement_id = ?",
                String.class,
                settlement.getSettlementId()
            ));
            assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM settlements WHERE settlement_id = ? "
                    + "AND idempotency_key IS NOT NULL AND request_fingerprint IS NOT NULL",
                Integer.class,
                settlement.getSettlementId()
            ));
        } finally {
            deleteFixture(fixture);
        }
    }

    @Test
    void insertSettlementItemShares_generatedItemId_linksActiveSettlementMember() {
        Fixture fixture = createFixture();
        try {
            Settlement settlement = settlement(fixture);
            mapper.insertSettlement(settlement);
            mapper.insertSettlementMembers(List.of(new SettlementMember(
                null,
                settlement.getSettlementId(),
                fixture.appointmentMemberId(),
                fixture.memberId(),
                new BigDecimal("100"),
                "NOT_REQUESTED",
                null
            )));
            SettlementItem item = new SettlementItem(
                settlement.getSettlementId(), "Dinner", new BigDecimal("100"), BigDecimal.ONE,
                new BigDecimal("100"), (short) 0
            );
            mapper.insertSettlementItem(item);
            mapper.insertSettlementItemShares(settlement.getSettlementId(), List.of(
                new SettlementItemShare(
                    null, item.getSettlementItemId(), fixture.appointmentMemberId(), BigDecimal.ONE,
                    new BigDecimal("100")
                )
            ));

            assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM settlement_item_shares WHERE settlement_item_id = ? "
                    + "AND allocated_quantity = ? AND allocated_amount = ?",
                Integer.class,
                item.getSettlementItemId(),
                BigDecimal.ONE,
                new BigDecimal("100")
            ));
        } finally {
            deleteFixture(fixture);
        }
    }

    @Test
    void linkToSettlement_ownDraft_fillsSettlementIdOnce() {
        Fixture fixture = createFixture();
        try {
            Settlement settlement = settlement(fixture);
            mapper.insertSettlement(settlement);
            SettlementReceipt draft = draft(fixture);
            receiptMapper.insertReceipt(draft);

            assertNotNull(draft.getSettlementReceiptId());
            assertNull(jdbcTemplate.queryForObject(
                "SELECT settlement_id FROM settlement_receipts WHERE settlement_receipt_id = ?",
                Long.class,
                draft.getSettlementReceiptId()
            ));

            int linked = receiptMapper.linkToSettlement(
                draft.getSettlementReceiptId(), settlement.getSettlementId(), fixture.memberId()
            );

            assertEquals(1, linked);
            assertEquals(settlement.getSettlementId(), jdbcTemplate.queryForObject(
                "SELECT settlement_id FROM settlement_receipts WHERE settlement_receipt_id = ?",
                Long.class,
                draft.getSettlementReceiptId()
            ));

            // 이미 쓰인 초안은 두 번째 연결에서 아무 행도 바꾸지 못해야 한다.
            assertEquals(0, receiptMapper.linkToSettlement(
                draft.getSettlementReceiptId(), settlement.getSettlementId(), fixture.memberId()
            ));
        } finally {
            deleteFixture(fixture);
        }
    }

    @Test
    void linkToSettlement_draftOfAnotherMember_changesNothing() {
        Fixture fixture = createFixture();
        try {
            Settlement settlement = settlement(fixture);
            mapper.insertSettlement(settlement);
            SettlementReceipt draft = draft(fixture);
            receiptMapper.insertReceipt(draft);

            int linked = receiptMapper.linkToSettlement(
                draft.getSettlementReceiptId(), settlement.getSettlementId(), fixture.memberId() + 1
            );

            assertEquals(0, linked);
        } finally {
            deleteFixture(fixture);
        }
    }

    @Test
    void findBySettlementIdForViewer_participantAndOutsider_returnsRowOnlyForParticipant() {
        Fixture fixture = createFixture();
        try {
            Settlement settlement = settlement(fixture);
            mapper.insertSettlement(settlement);
            SettlementReceipt draft = draft(fixture);
            receiptMapper.insertReceipt(draft);
            receiptMapper.linkToSettlement(
                draft.getSettlementReceiptId(), settlement.getSettlementId(), fixture.memberId()
            );

            SettlementReceipt visible = receiptMapper.findBySettlementIdForViewer(
                settlement.getSettlementId(), fixture.memberId()
            );
            SettlementReceipt hidden = receiptMapper.findBySettlementIdForViewer(
                settlement.getSettlementId(), fixture.memberId() + 1
            );

            assertNotNull(visible);
            assertEquals(draft.getObjectKey(), visible.getObjectKey());
            assertNull(hidden);
        } finally {
            deleteFixture(fixture);
        }
    }

    /**
     * 만료로 표시한 뒤에도 행은 계속 조회돼야 한다. 여기서 걸러 버리면 처음 알아챈 한 명만
     * "사라졌다"는 답을 받고 나머지 참여자는 "처음부터 없었다"와 구분할 수 없게 된다.
     * 두 번째 markExpired가 0을 돌려주는 것은 처음 알아챈 시각이 덮어써지지 않는다는 뜻이다.
     */
    @Test
    void markExpired_onceMarked_stillVisibleWithTimestampAndNotMarkedAgain() {
        Fixture fixture = createFixture();
        try {
            Settlement settlement = settlement(fixture);
            mapper.insertSettlement(settlement);
            SettlementReceipt draft = draft(fixture);
            receiptMapper.insertReceipt(draft);
            receiptMapper.linkToSettlement(
                draft.getSettlementReceiptId(), settlement.getSettlementId(), fixture.memberId()
            );

            assertEquals(1, receiptMapper.markExpired(draft.getSettlementReceiptId()));

            SettlementReceipt expired = receiptMapper.findBySettlementIdForViewer(
                settlement.getSettlementId(), fixture.memberId()
            );
            assertNotNull(expired);
            assertNotNull(expired.getDeletedAt());
            assertEquals(0, receiptMapper.markExpired(draft.getSettlementReceiptId()));

            // 만료된 뒤에도 비참여자에게는 여전히 보이지 않아야 한다.
            assertNull(receiptMapper.findBySettlementIdForViewer(
                settlement.getSettlementId(), fixture.memberId() + 1
            ));
        } finally {
            deleteFixture(fixture);
        }
    }

    private static SettlementReceipt draft(Fixture fixture) {
        return new SettlementReceipt(
            fixture.memberId(),
            "receipts/" + fixture.memberId() + "/" + UUID.randomUUID() + ".png",
            "image/png",
            1024
        );
    }

    private static Settlement settlement(Fixture fixture) {
        return Settlement.builder()
            .appointmentId(fixture.appointmentId())
            .createdByMemberId(fixture.memberId())
            .payerMemberId(fixture.memberId())
            .sourceTransferId(fixture.transferId())
            .idempotencyKey("settlement-it-" + UUID.randomUUID())
            .requestFingerprint("a".repeat(64))
            .settlementStatus("REQUESTED")
            .splitMethod("EQUAL")
            .totalAmount(new BigDecimal("100"))
            .payerShareAmount(new BigDecimal("100"))
            .receivableAmount(BigDecimal.ZERO)
            .build();
    }

    private static Fixture createFixture() {
        String marker = "settlement-it-" + UUID.randomUUID();
        long memberId = insert("members", "member_id", Map.of("display_name", marker));
        long itemId = insert("explore_items", "item_id", Map.of(
            "created_by", memberId,
            "reviewed_by", memberId,
            "item_type", "PLACE",
            "approval_status", "APPROVED",
            "visibility_status", "VISIBLE",
            "reviewed_at", LocalDateTime.now()
        ));
        long appointmentId = insert("appointments", "appointment_id", Map.of(
            "item_id", itemId,
            "host_member_id", memberId,
            "language_code", "en",
            "appointment_name", marker,
            "max_members", 2,
            "join_deadline", LocalDateTime.now().minusDays(2),
            "deposit_amount", new BigDecimal("5000"),
            "activity_start_at", LocalDateTime.now().minusHours(2),
            "activity_end_at", LocalDateTime.now().minusHours(1)
        ));
        long appointmentMemberId = insert("appointment_members", "appointment_member_id", Map.of(
            "appointment_id", appointmentId,
            "member_id", memberId
        ));
        long transferId = insert("wallet_transfers", "transfer_id", Map.of(
            "currency_code", "KRW",
            "initiator_member_id", memberId,
            "transfer_number", marker,
            "transfer_type", "QR_PAYMENT",
            "transfer_status", "COMPLETED",
            "amount", new BigDecimal("100"),
            "completed_at", LocalDateTime.now()
        ));
        return new Fixture(memberId, itemId, appointmentId, appointmentMemberId, transferId);
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
        // settlements와 members를 참조하므로 그 둘보다 먼저 지운다.
        jdbcTemplate.update(
            "DELETE FROM settlement_receipts WHERE uploaded_by_member_id = ?",
            fixture.memberId()
        );
        jdbcTemplate.update(
            "DELETE sis FROM settlement_item_shares sis "
                + "JOIN settlement_items si ON si.settlement_item_id = sis.settlement_item_id "
                + "JOIN settlements s ON s.settlement_id = si.settlement_id "
                + "WHERE s.source_transfer_id = ?",
            fixture.transferId()
        );
        jdbcTemplate.update(
            "DELETE si FROM settlement_items si "
                + "JOIN settlements s ON s.settlement_id = si.settlement_id "
                + "WHERE s.source_transfer_id = ?",
            fixture.transferId()
        );
        jdbcTemplate.update(
            "DELETE sm FROM settlement_members sm "
                + "JOIN settlements s ON s.settlement_id = sm.settlement_id "
                + "WHERE s.source_transfer_id = ?",
            fixture.transferId()
        );
        jdbcTemplate.update("DELETE FROM settlements WHERE source_transfer_id = ?", fixture.transferId());
        jdbcTemplate.update("DELETE FROM wallet_transfers WHERE transfer_id = ?", fixture.transferId());
        jdbcTemplate.update(
            "DELETE FROM appointment_members WHERE appointment_member_id = ?",
            fixture.appointmentMemberId()
        );
        jdbcTemplate.update("DELETE FROM appointments WHERE appointment_id = ?", fixture.appointmentId());
        jdbcTemplate.update("DELETE FROM explore_items WHERE item_id = ?", fixture.itemId());
        jdbcTemplate.update("DELETE FROM members WHERE member_id = ?", fixture.memberId());
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required");
        }
        return value;
    }

    private record Fixture(
        long memberId,
        long itemId,
        long appointmentId,
        long appointmentMemberId,
        long transferId
    ) {
    }
}
