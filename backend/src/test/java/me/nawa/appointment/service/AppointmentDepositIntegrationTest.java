package me.nawa.appointment.service;

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
import me.nawa.appointment.domain.AppointmentMember;
import me.nawa.appointment.domain.AppointmentStatus;
import me.nawa.appointment.domain.MembershipStatus;
import me.nawa.appointment.dto.request.AppointmentAttendanceRequest;
import me.nawa.appointment.dto.request.AppointmentCreateRequest;
import me.nawa.appointment.dto.response.AppointmentMemberResponse;
import me.nawa.appointment.mapper.AppointmentMapper;
import me.nawa.config.MySqlSchemaExtension;
import me.nawa.deposit.domain.AttendanceStatus;
import me.nawa.deposit.domain.Deposit;
import me.nawa.deposit.domain.DepositPayoutBatch;
import me.nawa.deposit.domain.DepositStatus;
import me.nawa.deposit.domain.ResolutionReason;
import me.nawa.deposit.domain.ResolutionStatus;
import me.nawa.deposit.mapper.DepositMapper;
import me.nawa.deposit.mapper.DepositPayoutBatchMapper;
import me.nawa.journey.mapper.JourneyMapper;
import me.nawa.wallet.domain.SystemWalletCode;
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
 * createAppointment·joinAppointment가 실제 MySQL 위에서 약속·참여·보증금을 하나의
 * 흐름으로 만들고, 회원 지갑에서 DEPOSIT_POOL로 실제 잔액이 이동하는지 검증한다.
 *
 * 여기서 만드는 AppointmentService·WalletTransferService는 Spring 컨테이너가 관리하는
 * 빈이 아니라 직접 new한 객체라 @Transactional을 타지 않는다 — 각 매퍼 호출이 그때그때
 * 커밋된다(QrPaymentConcurrencyIntegrationTest의 같은 주석 참고). 실패 시 롤백 여부가
 * 아니라, 성공 경로의 SQL이 실제 스키마에서 그대로 동작하고 최종 상태가 맞는지가
 * 이 테스트의 목적이다.
 */
@ExtendWith(MySqlSchemaExtension.class)
@EnabledIfEnvironmentVariable(
    named = "RUN_MYSQL_INTEGRATION_TESTS",
    matches = "(?i)true"
)
class AppointmentDepositIntegrationTest {

    private static HikariDataSource dataSource;
    private static AppointmentMapper appointmentMapper;
    private static DepositMapper depositMapper;
    private static DepositPayoutBatchMapper depositPayoutBatchMapper;
    private static WalletMapper walletMapper;
    private static JourneyMapper journeyMapper;
    private static JdbcTemplate jdbcTemplate;
    private static AppointmentService appointmentService;

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
        sqlSessionFactory.getConfiguration().addMapper(WalletMapper.class);
        sqlSessionFactory.getConfiguration().addMapper(WalletTransferMapper.class);
        sqlSessionFactory.getConfiguration().addMapper(WalletLedgerMapper.class);
        sqlSessionFactory.getConfiguration().addMapper(JourneyMapper.class);

        SqlSessionTemplate sqlSessionTemplate = new SqlSessionTemplate(sqlSessionFactory);
        appointmentMapper = sqlSessionTemplate.getMapper(AppointmentMapper.class);
        depositMapper = sqlSessionTemplate.getMapper(DepositMapper.class);
        depositPayoutBatchMapper =
                sqlSessionTemplate.getMapper(DepositPayoutBatchMapper.class);
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
                    "DELETE d FROM deposits d"
                            + " JOIN appointment_members m ON m.appointment_member_id = d.appointment_member_id"
                            + " WHERE m.appointment_id IN (" + placeholders + ")",
                    ids
            );
            jdbcTemplate.update(
                    "DELETE FROM deposit_payout_batches WHERE appointment_id IN (" + placeholders + ")",
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
            // member_id로 지갑 소유자를 거쳐서 지우면 DEPOSIT_POOL 쪽(상대방) 원장 행은
            // 안 걸린다 — transfer_id 기준으로 양쪽 원장을 한 번에 지운다.
            jdbcTemplate.update(
                    "DELETE e FROM wallet_ledger_entries e"
                            + " JOIN wallet_transfers t ON t.transfer_id = e.transfer_id"
                            + " WHERE t.initiator_member_id IN (" + placeholders + ")",
                    ids
            );
            jdbcTemplate.update(
                    "DELETE FROM wallet_transfers WHERE initiator_member_id IN (" + placeholders + ")",
                    ids
            );
            jdbcTemplate.update(
                    "DELETE w FROM wallets w"
                            + " JOIN wallet_owners o ON o.wallet_owner_id = w.wallet_owner_id"
                            + " WHERE o.member_id IN (" + placeholders + ")",
                    ids
            );
            jdbcTemplate.update("DELETE FROM wallet_owners WHERE member_id IN (" + placeholders + ")", ids);
            jdbcTemplate.update("DELETE FROM members WHERE member_id IN (" + placeholders + ")", ids);
        }
        // DEPOSIT_POOL은 이 테스트가 만든 자원이 아니라 공유 시스템 지갑이므로, 잔액을
        // 테스트 시작 전 값으로 복원한다(행 자체는 지우지 않는다).
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
    void createAppointment_thenJoinAppointment_holdsBothDepositsInPool() {
        poolBalanceBeforeTest = jdbcTemplate.queryForObject(
                "SELECT available_balance FROM wallets w"
                        + " JOIN wallet_owners o ON o.wallet_owner_id = w.wallet_owner_id"
                        + " WHERE o.owner_type = 'SYSTEM' AND o.system_code = 'DEPOSIT_POOL'",
                BigDecimal.class
        );
        assertNotNull(poolBalanceBeforeTest, "V12 마이그레이션으로 DEPOSIT_POOL 지갑이 미리 존재해야 한다");

        long hostMemberId = createMemberWithWallet("방장", new BigDecimal("50000.0000"));
        long guestMemberId = createMemberWithWallet("참여자", new BigDecimal("50000.0000"));
        long eventId = createApprovedEvent();

        AppointmentCreateRequest request = new AppointmentCreateRequest();
        request.setItemId(eventId);
        request.setItemType("EVENT");
        request.setLanguageCode("en");
        request.setAppointmentName("Integration Test Appointment");
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

        assertEquals(AppointmentStatus.RECRUITING, created.getAppointmentStatus());
        AppointmentMember hostMember = appointmentMapper.findMemberByAppointmentAndMember(
                created.getAppointmentId(), hostMemberId
        );
        assertEquals(MembershipStatus.ACTIVE, hostMember.getMembershipStatus());
        Deposit hostDeposit = depositMapper.findByAppointmentMemberId(
                hostMember.getAppointmentMemberId()
        );
        assertEquals(DepositStatus.HELD, hostDeposit.getDepositStatus());
        assertEquals(0, new BigDecimal("40000.0000").compareTo(walletBalance(hostMemberId)));

        AppointmentMemberResponse joined =
                appointmentService.joinAppointment(guestMemberId, created.getAppointmentId());
        assertEquals(MembershipStatus.ACTIVE, joined.getMembershipStatus());
        assertEquals(0, new BigDecimal("40000.0000").compareTo(walletBalance(guestMemberId)));

        BigDecimal poolBalanceAfter = jdbcTemplate.queryForObject(
                "SELECT available_balance FROM wallets w"
                        + " JOIN wallet_owners o ON o.wallet_owner_id = w.wallet_owner_id"
                        + " WHERE o.owner_type = 'SYSTEM' AND o.system_code = 'DEPOSIT_POOL'",
                BigDecimal.class
        );
        assertEquals(
                0,
                poolBalanceBeforeTest.add(new BigDecimal("20000")).compareTo(poolBalanceAfter),
                "방장 10000원 + 참여자 10000원 = 20000원이 DEPOSIT_POOL에 추가로 쌓여야 한다"
        );
    }

    @Test
    void joinAppointment_thenLeaveAppointment_refundsDepositFromPool() {
        poolBalanceBeforeTest = jdbcTemplate.queryForObject(
                "SELECT available_balance FROM wallets w"
                        + " JOIN wallet_owners o ON o.wallet_owner_id = w.wallet_owner_id"
                        + " WHERE o.owner_type = 'SYSTEM' AND o.system_code = 'DEPOSIT_POOL'",
                BigDecimal.class
        );
        assertNotNull(poolBalanceBeforeTest, "V12 마이그레이션으로 DEPOSIT_POOL 지갑이 미리 존재해야 한다");

        long hostMemberId = createMemberWithWallet("방장", new BigDecimal("50000.0000"));
        long guestMemberId = createMemberWithWallet("참여자", new BigDecimal("50000.0000"));
        long eventId = createApprovedEvent();

        AppointmentCreateRequest request = new AppointmentCreateRequest();
        request.setItemId(eventId);
        request.setItemType("EVENT");
        request.setLanguageCode("en");
        request.setAppointmentName("Integration Test Appointment");
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
        assertEquals(0, new BigDecimal("40000.0000").compareTo(walletBalance(guestMemberId)));

        appointmentService.leaveAppointment(guestMemberId, created.getAppointmentId());

        AppointmentMember guestMember = appointmentMapper.findMemberByAppointmentAndMember(
                created.getAppointmentId(), guestMemberId
        );
        assertEquals(MembershipStatus.LEFT, guestMember.getMembershipStatus());
        Deposit guestDeposit = depositMapper.findByAppointmentMemberId(
                guestMember.getAppointmentMemberId()
        );
        assertEquals(DepositStatus.REFUNDED, guestDeposit.getDepositStatus());
        assertEquals(
                0,
                new BigDecimal("50000.0000").compareTo(walletBalance(guestMemberId)),
                "환급 후 참여자 지갑 잔액이 참여 전으로 돌아와야 한다"
        );

        BigDecimal poolBalanceAfter = jdbcTemplate.queryForObject(
                "SELECT available_balance FROM wallets w"
                        + " JOIN wallet_owners o ON o.wallet_owner_id = w.wallet_owner_id"
                        + " WHERE o.owner_type = 'SYSTEM' AND o.system_code = 'DEPOSIT_POOL'",
                BigDecimal.class
        );
        assertEquals(
                0,
                poolBalanceBeforeTest.add(new BigDecimal("10000")).compareTo(poolBalanceAfter),
                "방장 보증금 10000원만 DEPOSIT_POOL에 남아 있어야 한다"
        );
    }

    @Test
    void confirmAttendance_completesAppointmentAndCreatesPendingPayoutBatch() {
        poolBalanceBeforeTest = jdbcTemplate.queryForObject(
                "SELECT available_balance FROM wallets w"
                        + " JOIN wallet_owners o ON o.wallet_owner_id = w.wallet_owner_id"
                        + " WHERE o.owner_type = 'SYSTEM' AND o.system_code = 'DEPOSIT_POOL'",
                BigDecimal.class
        );
        long hostMemberId = createMemberWithWallet("방장", new BigDecimal("50000.0000"));
        long guestMemberId = createMemberWithWallet("참여자", new BigDecimal("50000.0000"));
        long eventId = createApprovedEvent();

        AppointmentCreateRequest request = new AppointmentCreateRequest();
        request.setItemId(eventId);
        request.setItemType("EVENT");
        request.setLanguageCode("en");
        request.setAppointmentName("Integration Test Appointment");
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

        // 스케줄러가 실제로 IN_PROGRESS로 넘길 때까지 기다리지 않고, 출석
        // 확정 자체의 동작만 검증하기 위해 상태를 직접 IN_PROGRESS로 맞춘다.
        // 활동 종료도 지나 있어야 한다(APPOINTMENT-009). 시각은 DB의 NOW()가
        // 아니라 앱이 만든 값을 넘긴다 — CI는 MySQL을 UTC로 두기 때문에 DB 시계에
        // 기대면 서비스가 보는 시각과 갈린다. join_deadline까지 함께 당기는 것은
        // chk_appointments_schedule이 join_deadline <= activity_start_at
        // < activity_end_at을 요구하기 때문이다.
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

        assertEquals(
                "COMPLETED",
                jdbcTemplate.queryForObject(
                        "SELECT appointment_status FROM appointments WHERE appointment_id = ?",
                        String.class,
                        created.getAppointmentId()
                )
        );

        DepositPayoutBatch batch = depositPayoutBatchMapper.findByAppointmentId(
                created.getAppointmentId()
        );
        assertNotNull(batch, "출석 확정 후 정산 배치가 PENDING으로 생성돼야 한다");
        assertEquals(ResolutionStatus.PENDING, batch.getResolutionStatus());
        assertEquals(ResolutionReason.APPOINTMENT_COMPLETED, batch.getResolutionReason());
        assertEquals(
                0,
                new BigDecimal("20000").compareTo(batch.getTotalHeldAmount()),
                "방장·참여자 보증금 10000원씩 합쳐 20000원이 정산 대상이어야 한다"
        );

        AppointmentMember hostMember = appointmentMapper.findMemberByAppointmentAndMember(
                created.getAppointmentId(), hostMemberId
        );
        AppointmentMember guestMember = appointmentMapper.findMemberByAppointmentAndMember(
                created.getAppointmentId(), guestMemberId
        );
        assertEquals(AttendanceStatus.ATTENDED, hostMember.getAttendanceStatus());
        assertEquals(AttendanceStatus.NO_SHOW, guestMember.getAttendanceStatus());
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
                        + " VALUES (?, 'Integration Test Event', CURRENT_DATE(),"
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
