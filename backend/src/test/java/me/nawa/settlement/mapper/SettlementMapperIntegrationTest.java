package me.nawa.settlement.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import me.nawa.settlement.domain.Settlement;
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
            "me/nawa/settlement/mapper/SettlementMapper.xml"
        ));
        SqlSessionFactory sqlSessionFactory = factoryBean.getObject();
        if (!sqlSessionFactory.getConfiguration().hasMapper(SettlementMapper.class)) {
            sqlSessionFactory.getConfiguration().addMapper(SettlementMapper.class);
        }
        mapper = new SqlSessionTemplate(sqlSessionFactory).getMapper(SettlementMapper.class);
    }

    @AfterAll
    static void closeDatabase() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Test
    void cancelSettlement_draft_updatesCancelledState() {
        Fixture fixture = createFixture();
        try {
            Settlement settlement = settlement(fixture, "DRAFT");
            mapper.insertSettlement(settlement);

            int updated = mapper.cancelSettlement(settlement.getSettlementId(), fixture.memberId());

            assertEquals(1, updated);
            assertEquals("CANCELLED", jdbcTemplate.queryForObject(
                "SELECT settlement_status FROM settlements WHERE settlement_id = ?",
                String.class,
                settlement.getSettlementId()
            ));
        } finally {
            deleteFixture(fixture);
        }
    }

    @Test
    void markSettlementRequested_draft_updatesRequestedState() {
        Fixture fixture = createFixture();
        try {
            Settlement settlement = settlement(fixture, "DRAFT");
            mapper.insertSettlement(settlement);

            int updated = mapper.markSettlementRequested(
                settlement.getSettlementId(),
                fixture.memberId()
            );

            assertEquals(1, updated);
            assertEquals("REQUESTED", jdbcTemplate.queryForObject(
                "SELECT settlement_status FROM settlements WHERE settlement_id = ?",
                String.class,
                settlement.getSettlementId()
            ));
        } finally {
            deleteFixture(fixture);
        }
    }

    private static Settlement settlement(Fixture fixture, String status) {
        return Settlement.builder()
            .appointmentId(fixture.appointmentId())
            .createdByMemberId(fixture.memberId())
            .payerMemberId(fixture.memberId())
            .sourceTransferId(fixture.transferId())
            .idempotencyKey("settlement-it-" + UUID.randomUUID())
            .requestFingerprint("a".repeat(64))
            .settlementStatus(status)
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
