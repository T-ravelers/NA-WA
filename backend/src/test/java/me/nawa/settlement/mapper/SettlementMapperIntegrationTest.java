package me.nawa.settlement.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import me.nawa.config.MySqlSchemaExtension;
import me.nawa.settlement.domain.Settlement;
import me.nawa.settlement.domain.SettlementCollectionMember;
import me.nawa.settlement.domain.SettlementItem;
import me.nawa.settlement.domain.SettlementItemShare;
import me.nawa.settlement.domain.SettlementMember;
import me.nawa.settlement.domain.SettlementReceipt;
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

    /**
     * 글자 인식은 이 조회 하나로 권한을 가른다. 남의 초안을 넘겨 대신 인식시키는 것을 여기서
     * 막지 못하면 다른 곳에 걸러 낼 자리가 없다.
     */
    @Test
    void findDraftForUploader_ownDraft_returnsRowAndHidesItFromOthers() {
        Fixture fixture = createFixture();
        try {
            SettlementReceipt draft = draft(fixture);
            receiptMapper.insertReceipt(draft);

            SettlementReceipt found = receiptMapper.findDraftForUploader(
                draft.getSettlementReceiptId(), fixture.memberId()
            );

            assertNotNull(found);
            assertEquals(draft.getObjectKey(), found.getObjectKey());
            assertEquals("image/png", found.getContentType());
            assertNull(receiptMapper.findDraftForUploader(
                draft.getSettlementReceiptId(), fixture.memberId() + 1
            ));
        } finally {
            deleteFixture(fixture);
        }
    }

    /**
     * 정산에 붙은 뒤에는 품목이 확정된 뒤라 다시 읽을 이유가 없고, 인식은 부를 때마다 요금이
     * 나간다. 그래서 연결되는 순간 이 조회에서 빠져야 한다.
     */
    @Test
    void findDraftForUploader_linkedToSettlement_returnsNull() {
        Fixture fixture = createFixture();
        try {
            Settlement settlement = settlement(fixture);
            mapper.insertSettlement(settlement);
            SettlementReceipt draft = draft(fixture);
            receiptMapper.insertReceipt(draft);
            receiptMapper.linkToSettlement(
                draft.getSettlementReceiptId(), settlement.getSettlementId(), fixture.memberId()
            );

            assertNull(receiptMapper.findDraftForUploader(
                draft.getSettlementReceiptId(), fixture.memberId()
            ));
        } finally {
            deleteFixture(fixture);
        }
    }

    /**
     * 원결제자가 볼 "누가 냈나" 목록은 돈을 낼 사람만 담는다.
     *
     * 정산을 만든 사람도 구성원 행을 가지지만 자기 자신에게 보낼 돈은 없어서 NOT_REQUESTED로
     * 남는다. 이 행이 목록에 섞이면 전원이 다 내도 화면의 인원수가 끝까지 차지 않는다.
     */
    @Test
    void findCollectionMembers_excludesPayerRowAndReadsNameShareAndStatus() {
        Fixture fixture = createFixture();
        String guestName = "settlement-it-" + UUID.randomUUID();
        long guestMemberId = 0L;
        long guestAppointmentMemberId = 0L;
        try {
            Settlement settlement = settlement(fixture);
            mapper.insertSettlement(settlement);
            guestMemberId = insert("members", "member_id", Map.of("display_name", guestName));
            guestAppointmentMemberId = insert("appointment_members", "appointment_member_id", Map.of(
                "appointment_id", fixture.appointmentId(),
                "member_id", guestMemberId
            ));
            mapper.insertSettlementMembers(List.of(
                new SettlementMember(
                    null, settlement.getSettlementId(), fixture.appointmentMemberId(),
                    fixture.memberId(), new BigDecimal("40"), "NOT_REQUESTED", null
                ),
                new SettlementMember(
                    null, settlement.getSettlementId(), guestAppointmentMemberId,
                    guestMemberId, new BigDecimal("60"), "PENDING", null
                )
            ));

            List<SettlementCollectionMember> pending =
                mapper.findCollectionMembers(settlement.getSettlementId());

            assertEquals(1, pending.size());
            assertEquals(guestAppointmentMemberId, pending.get(0).getAppointmentMemberId());
            assertEquals(guestName, pending.get(0).getDisplayName());
            assertEquals(0, new BigDecimal("60").compareTo(pending.get(0).getShareAmount()));
            assertEquals("PENDING", pending.get(0).getRequestStatus());

            mapper.markSettlementMemberPaid(
                mapper.findMemberBySettlementAndMember(settlement.getSettlementId(), guestMemberId)
                    .getSettlementMemberId(),
                fixture.transferId(),
                "settlement-it-" + UUID.randomUUID()
            );

            List<SettlementCollectionMember> paid =
                mapper.findCollectionMembers(settlement.getSettlementId());

            assertEquals(1, paid.size());
            assertEquals("PAID", paid.get(0).getRequestStatus());

            // 약속에서 나가도 이미 진 빚은 남는다. 목록에서 빼면 정산은 끝나지 않았는데
            // 화면만 다 냈다고 말하게 된다.
            jdbcTemplate.update(
                "UPDATE appointment_members SET membership_status = 'LEFT', "
                    + "left_at = CURRENT_TIMESTAMP WHERE appointment_member_id = ?",
                guestAppointmentMemberId
            );

            assertEquals(1, mapper.findCollectionMembers(settlement.getSettlementId()).size());

            // 참가 기록이 지워져도 같다. 완료 판정은 정산 구성원 행만 보므로, 이 목록이
            // 참가 쪽을 더 걸러내면 화면과 정산 상태가 어긋난다.
            jdbcTemplate.update(
                "UPDATE appointment_members SET deleted_at = CURRENT_TIMESTAMP "
                    + "WHERE appointment_member_id = ?",
                guestAppointmentMemberId
            );

            assertEquals(1, mapper.findCollectionMembers(settlement.getSettlementId()).size());
        } finally {
            // 참가 행과 회원을 지우려면 그것을 가리키는 정산 구성원 행이 먼저 없어져야 한다.
            jdbcTemplate.update(
                "DELETE sm FROM settlement_members sm "
                    + "JOIN settlements s ON s.settlement_id = sm.settlement_id "
                    + "WHERE s.source_transfer_id = ?",
                fixture.transferId()
            );
            jdbcTemplate.update(
                "DELETE FROM appointment_members WHERE appointment_member_id = ?",
                guestAppointmentMemberId
            );
            jdbcTemplate.update("DELETE FROM members WHERE member_id = ?", guestMemberId);
            deleteFixture(fixture);
        }
    }

    /**
     * 안 낸 사람이 목록 위로 온다.
     *
     * 이 카드를 여는 이유가 "누가 아직 안 냈나"라서, 낸 사람 사이에 섞어 두면 원결제자가
     * 배지를 하나씩 훑어야 한다. 같은 상태 안에서는 참가 행 번호 순서로 두어 목록이 볼
     * 때마다 뒤바뀌지 않게 한다.
     */
    @Test
    void findCollectionMembers_unpaidComesFirstThenStableByAppointmentMember() {
        Fixture fixture = createFixture();
        long earlyPaidMemberId = 0L;
        long earlyPaidAppointmentMemberId = 0L;
        long latePendingMemberId = 0L;
        long latePendingAppointmentMemberId = 0L;
        try {
            Settlement settlement = settlement(fixture);
            mapper.insertSettlement(settlement);

            // 먼저 들어와서 이미 낸 사람과, 나중에 들어와서 아직 안 낸 사람.
            // 참가 행 번호만 보면 낸 사람이 위인데, 안 낸 사람이 위로 와야 한다.
            earlyPaidMemberId = insert("members", "member_id",
                Map.of("display_name", "settlement-it-" + UUID.randomUUID()));
            earlyPaidAppointmentMemberId = insert("appointment_members", "appointment_member_id",
                Map.of("appointment_id", fixture.appointmentId(), "member_id", earlyPaidMemberId));
            latePendingMemberId = insert("members", "member_id",
                Map.of("display_name", "settlement-it-" + UUID.randomUUID()));
            latePendingAppointmentMemberId = insert("appointment_members", "appointment_member_id",
                Map.of("appointment_id", fixture.appointmentId(), "member_id", latePendingMemberId));

            mapper.insertSettlementMembers(List.of(
                new SettlementMember(
                    null, settlement.getSettlementId(), fixture.appointmentMemberId(),
                    fixture.memberId(), new BigDecimal("40"), "NOT_REQUESTED", null
                ),
                new SettlementMember(
                    null, settlement.getSettlementId(), earlyPaidAppointmentMemberId,
                    earlyPaidMemberId, new BigDecimal("30"), "PENDING", null
                ),
                new SettlementMember(
                    null, settlement.getSettlementId(), latePendingAppointmentMemberId,
                    latePendingMemberId, new BigDecimal("30"), "PENDING", null
                )
            ));
            mapper.markSettlementMemberPaid(
                mapper.findMemberBySettlementAndMember(
                    settlement.getSettlementId(), earlyPaidMemberId
                ).getSettlementMemberId(),
                fixture.transferId(),
                "settlement-it-" + UUID.randomUUID()
            );

            List<SettlementCollectionMember> members =
                mapper.findCollectionMembers(settlement.getSettlementId());

            assertEquals(2, members.size());
            assertEquals(latePendingAppointmentMemberId, members.get(0).getAppointmentMemberId());
            assertEquals("PENDING", members.get(0).getRequestStatus());
            assertEquals(earlyPaidAppointmentMemberId, members.get(1).getAppointmentMemberId());
            assertEquals("PAID", members.get(1).getRequestStatus());
        } finally {
            jdbcTemplate.update(
                "DELETE sm FROM settlement_members sm "
                    + "JOIN settlements s ON s.settlement_id = sm.settlement_id "
                    + "WHERE s.source_transfer_id = ?",
                fixture.transferId()
            );
            jdbcTemplate.update(
                "DELETE FROM appointment_members WHERE appointment_member_id IN (?, ?)",
                earlyPaidAppointmentMemberId, latePendingAppointmentMemberId
            );
            jdbcTemplate.update(
                "DELETE FROM members WHERE member_id IN (?, ?)",
                earlyPaidMemberId, latePendingMemberId
            );
            deleteFixture(fixture);
        }
    }

    /**
     * 마지막 사람이 낼 때까지는 완료로 넘어가지 않고, 넘어가는 순간의 시각은 DB가 아니라
     * 애플리케이션이 넘긴 값이 그대로 들어가야 한다.
     *
     * 운영 DB의 시계는 앱과 같게 맞춰 뒀지만 CI의 MySQL은 이 의존을 드러내려고 일부러
     * UTC다. DB 시계로 적으면 그 차이만큼 어긋난 시각이 화면에 "언제 끝났는지"로 보이고,
     * 기간으로 거를 때 경계 근처의 정산이 다른 날짜로 묶인다.
     */
    @Test
    void completeSettlementIfNoPendingPayments_lastPaymentDone_storesTimeGivenByApplication() {
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
                "PENDING",
                null
            )));
            Long settlementMemberId = jdbcTemplate.queryForObject(
                "SELECT settlement_member_id FROM settlement_members WHERE settlement_id = ?",
                Long.class,
                settlement.getSettlementId()
            );
            LocalDateTime completedAt = LocalDateTime.of(2026, 8, 20, 21, 5, 0);

            assertEquals(0, mapper.completeSettlementIfNoPendingPayments(
                settlement.getSettlementId(), completedAt
            ));
            assertNull(jdbcTemplate.queryForObject(
                "SELECT completed_at FROM settlements WHERE settlement_id = ?",
                Timestamp.class,
                settlement.getSettlementId()
            ));

            mapper.markSettlementMemberPaid(
                settlementMemberId, fixture.transferId(), "settlement-it-pay"
            );

            assertEquals(1, mapper.completeSettlementIfNoPendingPayments(
                settlement.getSettlementId(), completedAt
            ));
            assertEquals("COMPLETED", jdbcTemplate.queryForObject(
                "SELECT settlement_status FROM settlements WHERE settlement_id = ?",
                String.class,
                settlement.getSettlementId()
            ));
            Timestamp stored = jdbcTemplate.queryForObject(
                "SELECT completed_at FROM settlements WHERE settlement_id = ?",
                Timestamp.class,
                settlement.getSettlementId()
            );
            assertNotNull(stored);
            assertEquals(completedAt, stored.toLocalDateTime());
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
