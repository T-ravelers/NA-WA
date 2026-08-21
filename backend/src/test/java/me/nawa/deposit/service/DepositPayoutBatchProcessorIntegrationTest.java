package me.nawa.deposit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import me.nawa.appointment.domain.Appointment;
import me.nawa.appointment.dto.request.AppointmentAttendanceRequest;
import me.nawa.appointment.dto.request.AppointmentCreateRequest;
import me.nawa.appointment.mapper.AppointmentMapper;
import me.nawa.appointment.service.AppointmentService;
import me.nawa.config.MySqlSchemaExtension;
import me.nawa.deposit.domain.AttendanceStatus;
import me.nawa.deposit.domain.DepositPayout;
import me.nawa.deposit.domain.DepositPayoutBatch;
import me.nawa.deposit.domain.DepositStatus;
import me.nawa.deposit.domain.ResolutionStatus;
import me.nawa.deposit.mapper.DepositMapper;
import me.nawa.deposit.mapper.DepositPayoutBatchMapper;
import me.nawa.deposit.mapper.DepositPayoutMapper;
import me.nawa.journey.mapper.JourneyMapper;
import me.nawa.settlement.service.SettlementAmountAllocator;
import me.nawa.wallet.mapper.WalletLedgerMapper;
import me.nawa.wallet.mapper.WalletMapper;
import me.nawa.wallet.mapper.WalletTransferMapper;
import me.nawa.wallet.service.WalletTransferService;
import me.nawa.wallet.util.TransactionNumberGenerator;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

/**
 * confirmAttendance가 만든 PENDING 정산 배치를 DepositPayoutBatchProcessor가 실제
 * MySQL 위에서 지갑 이체까지 완료하는지 검증한다. 출석 회원에게는 본인 보증금이
 * 환급되고, 노쇼 회원의 보증금은 출석 회원에게 분배되어 최종적으로 DEPOSIT_POOL
 * 잔액이 0으로 돌아오는지 확인한다.
 */
@ExtendWith(MySqlSchemaExtension.class)
@EnabledIfEnvironmentVariable(
    named = "RUN_MYSQL_INTEGRATION_TESTS",
    matches = "(?i)true"
)
class DepositPayoutBatchProcessorIntegrationTest {

    private static HikariDataSource dataSource;
    private static AppointmentMapper appointmentMapper;
    private static DepositMapper depositMapper;
    private static DepositPayoutBatchMapper depositPayoutBatchMapper;
    private static DepositPayoutMapper depositPayoutMapper;
    private static WalletMapper walletMapper;
    private static JourneyMapper journeyMapper;
    private static JdbcTemplate jdbcTemplate;
    private static AppointmentService appointmentService;
    private static DepositPayoutBatchProcessor processor;

    private final List<Long> memberIds = new ArrayList<>();
    private final List<Long> eventIds = new ArrayList<>();
    private final List<Long> appointmentIds = new ArrayList<>();
    private final List<Long> tripIds = new ArrayList<>();
    private BigDecimal poolBalanceBeforeTest;

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

        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setConfigLocation(new ClassPathResource("mybatis-config.xml"));
        SqlSessionFactory sqlSessionFactory = factoryBean.getObject();
        sqlSessionFactory.getConfiguration().addMapper(AppointmentMapper.class);
        sqlSessionFactory.getConfiguration().addMapper(DepositMapper.class);
        sqlSessionFactory.getConfiguration().addMapper(DepositPayoutBatchMapper.class);
        sqlSessionFactory.getConfiguration().addMapper(DepositPayoutMapper.class);
        sqlSessionFactory.getConfiguration().addMapper(WalletMapper.class);
        sqlSessionFactory.getConfiguration().addMapper(WalletTransferMapper.class);
        sqlSessionFactory.getConfiguration().addMapper(WalletLedgerMapper.class);
        sqlSessionFactory.getConfiguration().addMapper(JourneyMapper.class);

        SqlSessionTemplate sqlSessionTemplate = new SqlSessionTemplate(sqlSessionFactory);
        appointmentMapper = sqlSessionTemplate.getMapper(AppointmentMapper.class);
        depositMapper = sqlSessionTemplate.getMapper(DepositMapper.class);
        depositPayoutBatchMapper = sqlSessionTemplate.getMapper(DepositPayoutBatchMapper.class);
        depositPayoutMapper = sqlSessionTemplate.getMapper(DepositPayoutMapper.class);
        walletMapper = sqlSessionTemplate.getMapper(WalletMapper.class);
        journeyMapper = sqlSessionTemplate.getMapper(JourneyMapper.class);
        WalletTransferMapper walletTransferMapper =
                sqlSessionTemplate.getMapper(WalletTransferMapper.class);
        WalletLedgerMapper walletLedgerMapper =
                sqlSessionTemplate.getMapper(WalletLedgerMapper.class);
        jdbcTemplate = new JdbcTemplate(dataSource);

        WalletTransferService walletTransferService = new WalletTransferService(
                walletMapper,
                walletTransferMapper,
                walletLedgerMapper,
                new TransactionNumberGenerator()
        );
        appointmentService = new AppointmentService(
                appointmentMapper,
                depositMapper,
                depositPayoutBatchMapper,
                walletTransferService,
                journeyMapper
        );
        processor = new DepositPayoutBatchProcessor(
                depositPayoutBatchMapper,
                depositMapper,
                depositPayoutMapper,
                appointmentMapper,
                walletTransferService,
                new SettlementAmountAllocator()
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
        if (!appointmentIds.isEmpty()) {
            String placeholders = String.join(", ", appointmentIds.stream().map(id -> "?").toList());
            Object[] ids = appointmentIds.toArray();
            jdbcTemplate.update(
                    "DELETE p FROM deposit_payouts p"
                            + " JOIN deposit_payout_batches b"
                            + " ON b.deposit_payout_batch_id = p.deposit_payout_batch_id"
                            + " WHERE b.appointment_id IN (" + placeholders + ")",
                    ids
            );
            jdbcTemplate.update(
                    "DELETE FROM deposit_payout_batches WHERE appointment_id IN (" + placeholders + ")",
                    ids
            );
            jdbcTemplate.update(
                    "DELETE d FROM deposits d"
                            + " JOIN appointment_members m ON m.appointment_member_id = d.appointment_member_id"
                            + " WHERE m.appointment_id IN (" + placeholders + ")",
                    ids
            );
            // trip_items가 fk_trip_items_appointment_item으로 appointments를 참조하므로
            // 약속을 지우기 전에 먼저 지운다.
            jdbcTemplate.update(
                    "DELETE FROM trip_items WHERE appointment_id IN (" + placeholders + ")",
                    ids
            );
            jdbcTemplate.update(
                    "DELETE FROM appointment_members WHERE appointment_id IN (" + placeholders + ")",
                    ids
            );
            jdbcTemplate.update(
                    "DELETE FROM appointments WHERE appointment_id IN (" + placeholders + ")",
                    ids
            );
        }
        if (!tripIds.isEmpty()) {
            String placeholders = String.join(", ", tripIds.stream().map(id -> "?").toList());
            jdbcTemplate.update(
                    "DELETE FROM trips WHERE trip_id IN (" + placeholders + ")",
                    tripIds.toArray()
            );
        }
        if (!eventIds.isEmpty()) {
            String placeholders = String.join(", ", eventIds.stream().map(id -> "?").toList());
            Object[] ids = eventIds.toArray();
            jdbcTemplate.update("DELETE FROM event WHERE event_id IN (" + placeholders + ")", ids);
            jdbcTemplate.update("DELETE FROM explore_items WHERE item_id IN (" + placeholders + ")", ids);
        }
        if (!memberIds.isEmpty()) {
            String placeholders = String.join(", ", memberIds.stream().map(id -> "?").toList());
            Object[] ids = memberIds.toArray();
            // 이 테스트가 만든 이체는 initiator_member_id가 채워진 것(예치)과
            // null인 것(정산 배치 처리, 시스템 자동 이체)이 섞여 있다. initiator
            // 기준만으로는 후자를 못 찾으므로, 원장에 이 회원들의 지갑이 걸린
            // 이체 ID를 먼저 뽑아 그 목록으로 지운다.
            List<Long> transferIds = jdbcTemplate.queryForList(
                    "SELECT DISTINCT wle.transfer_id FROM wallet_ledger_entries wle"
                            + " JOIN wallets w ON w.wallet_id = wle.wallet_id"
                            + " JOIN wallet_owners o ON o.wallet_owner_id = w.wallet_owner_id"
                            + " WHERE o.member_id IN (" + placeholders + ")",
                    ids,
                    Long.class
            );
            if (!transferIds.isEmpty()) {
                String transferPlaceholders =
                        String.join(", ", transferIds.stream().map(id -> "?").toList());
                Object[] transferIdArgs = transferIds.toArray();
                jdbcTemplate.update(
                        "DELETE FROM wallet_ledger_entries WHERE transfer_id IN ("
                                + transferPlaceholders + ")",
                        transferIdArgs
                );
                jdbcTemplate.update(
                        "DELETE FROM wallet_transfers WHERE transfer_id IN ("
                                + transferPlaceholders + ")",
                        transferIdArgs
                );
            }
            jdbcTemplate.update(
                    "DELETE w FROM wallets w"
                            + " JOIN wallet_owners o ON o.wallet_owner_id = w.wallet_owner_id"
                            + " WHERE o.member_id IN (" + placeholders + ")",
                    ids
            );
            jdbcTemplate.update("DELETE FROM wallet_owners WHERE member_id IN (" + placeholders + ")", ids);
            jdbcTemplate.update("DELETE FROM members WHERE member_id IN (" + placeholders + ")", ids);
        }
        if (poolBalanceBeforeTest != null) {
            jdbcTemplate.update(
                    "UPDATE wallets w JOIN wallet_owners o ON o.wallet_owner_id = w.wallet_owner_id"
                            + " SET w.available_balance = ?"
                            + " WHERE o.owner_type = 'SYSTEM' AND o.system_code = 'DEPOSIT_POOL'",
                    poolBalanceBeforeTest
            );
        }
    }

    @Test
    void processBatch_refundsAttendeeAndDistributesNoShowDepositToAttendee() {
        poolBalanceBeforeTest = jdbcTemplate.queryForObject(
                "SELECT available_balance FROM wallets w"
                        + " JOIN wallet_owners o ON o.wallet_owner_id = w.wallet_owner_id"
                        + " WHERE o.owner_type = 'SYSTEM' AND o.system_code = 'DEPOSIT_POOL'",
                BigDecimal.class
        );
        assertNotNull(poolBalanceBeforeTest);

        long hostMemberId = createMemberWithWallet("방장", new BigDecimal("50000.0000"));
        long guestMemberId = createMemberWithWallet("참여자", new BigDecimal("50000.0000"));
        long eventId = createApprovedEvent();

        AppointmentCreateRequest request = new AppointmentCreateRequest();
        request.setItemId(eventId);
        request.setItemType("EVENT");
        request.setLanguageCode("en");
        request.setAppointmentName("Payout Integration Test Appointment");
        request.setMaxMembers(5);
        request.setDepositAmount(BigDecimal.valueOf(10_000));
        request.setMeetingPlace("Test Meeting Place");
        request.setJoinDeadline(LocalDateTime.now().plusDays(1));
        request.setTripId(createJourney(hostMemberId));
        request.setVisitDate(LocalDate.now().plusDays(2));
        request.setActivityStartTime(LocalTime.of(10, 0));
        request.setActivityEndTime(LocalTime.of(12, 0));

        Appointment created = appointmentService.createAppointment(hostMemberId, request);
        appointmentIds.add(created.getAppointmentId());
        appointmentService.joinAppointment(guestMemberId, created.getAppointmentId());

        // 출석 확정은 활동이 끝난 뒤에만 열린다(APPOINTMENT-009). 상태와 함께
        // 활동 시각도 지난 값으로 맞춘다. DB의 NOW()가 아니라 앱이 만든 시각을
        // 넘긴다 — CI의 MySQL은 UTC라 DB 시계에 기대면 서비스와 갈린다.
        // join_deadline까지 함께 당기는 것은 chk_appointments_schedule이
        // join_deadline <= activity_start_at < activity_end_at을 요구하기 때문이다.
        LocalDateTime endedAt = LocalDateTime.now().minusHours(1);
        jdbcTemplate.update(
                "UPDATE appointments SET appointment_status = 'IN_PROGRESS',"
                        + " join_deadline = ?,"
                        + " activity_start_at = ?, activity_end_at = ?"
                        + " WHERE appointment_id = ?",
                Timestamp.valueOf(endedAt.minusHours(4)),
                Timestamp.valueOf(endedAt.minusHours(3)),
                Timestamp.valueOf(endedAt),
                created.getAppointmentId()
        );

        AppointmentAttendanceRequest attendanceRequest = new AppointmentAttendanceRequest();
        AppointmentAttendanceRequest.MemberAttendance hostAttendance =
                new AppointmentAttendanceRequest.MemberAttendance();
        hostAttendance.setMemberId(hostMemberId);
        hostAttendance.setAttendanceStatus(AttendanceStatus.ATTENDED);
        AppointmentAttendanceRequest.MemberAttendance guestAttendance =
                new AppointmentAttendanceRequest.MemberAttendance();
        guestAttendance.setMemberId(guestMemberId);
        guestAttendance.setAttendanceStatus(AttendanceStatus.NO_SHOW);
        attendanceRequest.setMembers(List.of(hostAttendance, guestAttendance));

        appointmentService.confirmAttendance(
                hostMemberId, created.getAppointmentId(), attendanceRequest
        );

        DepositPayoutBatch pendingBatch = depositPayoutBatchMapper.findByAppointmentId(
                created.getAppointmentId()
        );
        processor.processBatch(pendingBatch.getDepositPayoutBatchId());

        DepositPayoutBatch completedBatch = depositPayoutBatchMapper.findByAppointmentId(
                created.getAppointmentId()
        );
        assertEquals(ResolutionStatus.COMPLETED, completedBatch.getResolutionStatus());
        assertEquals(
                0, new BigDecimal("10000").compareTo(completedBatch.getTotalRefundedAmount())
        );
        assertEquals(
                0, new BigDecimal("10000").compareTo(completedBatch.getTotalNoShowAmount())
        );
        assertEquals(
                0,
                new BigDecimal("10000").compareTo(
                        completedBatch.getTotalNoShowDistributedAmount()
                )
        );

        // 방장 보증금 10000원은 본인에게 환급, 참여자(노쇼) 보증금 10000원은
        // 방장(유일한 출석자)에게 분배되어 방장 지갑은 총 20000원을 돌려받는다.
        assertEquals(
                0, new BigDecimal("60000.0000").compareTo(walletBalance(hostMemberId))
        );
        // 참여자는 노쇼라 자기 보증금을 돌려받지 못한다.
        assertEquals(
                0, new BigDecimal("40000.0000").compareTo(walletBalance(guestMemberId))
        );

        BigDecimal poolBalanceAfter = jdbcTemplate.queryForObject(
                "SELECT available_balance FROM wallets w"
                        + " JOIN wallet_owners o ON o.wallet_owner_id = w.wallet_owner_id"
                        + " WHERE o.owner_type = 'SYSTEM' AND o.system_code = 'DEPOSIT_POOL'",
                BigDecimal.class
        );
        assertEquals(
                0,
                poolBalanceBeforeTest.compareTo(poolBalanceAfter),
                "정산까지 끝나면 DEPOSIT_POOL에는 이 약속의 보증금이 남아있지 않아야 한다"
        );

        List<DepositPayout> payouts = depositPayoutMapper.findByBatchId(
                completedBatch.getDepositPayoutBatchId()
        );
        assertEquals(2, payouts.size());
    }

    private BigDecimal walletBalance(long memberId) {
        return jdbcTemplate.queryForObject(
                "SELECT available_balance FROM wallets w"
                        + " JOIN wallet_owners o ON o.wallet_owner_id = w.wallet_owner_id"
                        + " WHERE o.member_id = ?",
                BigDecimal.class,
                memberId
        );
    }

    private long createMemberWithWallet(String displayName, BigDecimal balance) {
        jdbcTemplate.update(
                "INSERT INTO members (display_name, preferred_language, member_status)"
                        + " VALUES (?, 'en', 'ACTIVE')",
                displayName
        );
        long memberId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        memberIds.add(memberId);

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
        return memberId;
    }

    private long createJourney(long memberId) {
        jdbcTemplate.update(
                "INSERT INTO trips (member_id, title, start_date, end_date)"
                        + " VALUES (?, 'Integration Test Trip', CURRENT_DATE(),"
                        + " DATE_ADD(CURRENT_DATE(), INTERVAL 30 DAY))",
                memberId
        );
        long tripId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        tripIds.add(tripId);
        return tripId;
    }

    private long createApprovedEvent() {
        long creatorMemberId = memberIds.get(0);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO explore_items "
                            + "(created_by, reviewed_by, item_type, approval_status, "
                            + "visibility_status, reviewed_at) "
                            + "VALUES (?, ?, 'EVENT', 'APPROVED', 'VISIBLE', CURRENT_TIMESTAMP)",
                    Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, creatorMemberId);
            statement.setLong(2, creatorMemberId);
            return statement;
        }, keyHolder);
        long eventId = keyHolder.getKey().longValue();
        eventIds.add(eventId);

        jdbcTemplate.update(
                "INSERT INTO event (event_id, title, start_date, end_date, status, is_permanent)"
                        + " VALUES (?, 'Payout Integration Test Event', CURRENT_DATE(),"
                        + " DATE_ADD(CURRENT_DATE(), INTERVAL 30 DAY), 'SCHEDULED', FALSE)",
                eventId
        );
        return eventId;
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required for MySQL integration tests");
        }
        return value;
    }
}
