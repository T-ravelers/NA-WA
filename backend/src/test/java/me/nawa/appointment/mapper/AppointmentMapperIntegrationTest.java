package me.nawa.appointment.mapper;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import me.nawa.appointment.domain.Appointment;
import me.nawa.appointment.domain.MyOngoingAppointment;
import me.nawa.appointment.dto.request.AppointmentSearchRequest;
import me.nawa.config.MySqlSchemaExtension;
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
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * findMyOngoingAppointments의 scope 분기(진행 중만 / 취소 제외 전체)와 정렬,
 * explore_items 조인으로 얻는 itemType을 실제 MySQL에서 검증한다. 같은 정렬을
 * 쓰는 공개 목록(searchAppointments)의 순서와 페이지 경계도 함께 검증한다.
 *
 * 모든 테스트는 rollback-only 트랜잭션 안에서 돌므로 데이터가 남지 않는다.
 */
@ExtendWith(MySqlSchemaExtension.class)
@EnabledIfEnvironmentVariable(
        named = "RUN_MYSQL_INTEGRATION_TESTS",
        matches = "(?i)true"
)
class AppointmentMapperIntegrationTest {
    private static HikariDataSource dataSource;
    private static AppointmentMapper appointmentMapper;
    private static JdbcTemplate jdbcTemplate;
    private static TransactionTemplate transactionTemplate;

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
        SqlSessionTemplate sqlSessionTemplate = new SqlSessionTemplate(sqlSessionFactory);
        appointmentMapper = sqlSessionTemplate.getMapper(AppointmentMapper.class);
        jdbcTemplate = new JdbcTemplate(dataSource);
        transactionTemplate = new TransactionTemplate(
                new DataSourceTransactionManager(dataSource)
        );
    }

    @AfterAll
    static void closeDatabase() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Test
    void findMyOngoingAppointments_scopesAndSortsByStatus() {
        transactionTemplate.executeWithoutResult(status -> {
            status.setRollbackOnly();
            long memberId = insertMember();
            long itemId = insertVisibleEventItem(memberId);
            long tripId = insertTrip(memberId);

            // 분류 기준이 애플리케이션이 넘긴 시각이므로 DB 서버 시간대와 무관하다.
            // 예전에는 MySQL NOW() 기준이라 여백을 하루 이상 둬야 안전했는데, 그
            // 여백이 정확히 시간대 시차를 가려 버그를 통과시키고 있었다. 아래
            // findMyOngoingAppointments_classifiesAroundApplicationClock이 분 단위
            // 경계를 직접 확인한다.
            LocalDateTime now = LocalDateTime.now();
            long paymentPendingId = insertAppointment(
                    itemId, memberId, "PAYMENT_PENDING", now.plusDays(1));
            long ongoingId = insertAppointment(
                    itemId, memberId, "IN_PROGRESS", now.plusDays(2));
            long farFutureId = insertAppointment(
                    itemId, memberId, "RECRUITING", now.plusDays(10));
            long finishedId = insertAppointment(
                    itemId, memberId, "COMPLETED", now.minusDays(5));
            long olderFinishedId = insertAppointment(
                    itemId, memberId, "COMPLETED", now.minusDays(8));
            long cancelledId = insertAppointment(
                    itemId, memberId, "CANCELLED", now.minusDays(3));
            for (long appointmentId : new long[] {
                    paymentPendingId, ongoingId, farFutureId,
                    finishedId, olderFinishedId, cancelledId}) {
                insertActiveMembership(appointmentId, memberId, tripId);
            }

            // 기존 계약: 진행 중만, 다가오는 순.
            List<MyOngoingAppointment> ongoing =
                    appointmentMapper.findMyOngoingAppointments(memberId, false, now);
            assertEquals(1, ongoing.size());
            assertEquals(ongoingId, ongoing.get(0).getAppointmentId());
            assertEquals(itemId, ongoing.get(0).getItemId());
            assertEquals("EVENT", ongoing.get(0).getItemType());
            assertEquals("IN_PROGRESS", ongoing.get(0).getAppointmentStatus());

            // 전체: 취소 제외. 예정은 임박한 순으로 위, 지난 것은 최근 순으로 아래.
            // PAYMENT_PENDING은 포함된다 — 근거는 APPOINTMENT_API.md.
            List<MyOngoingAppointment> all =
                    appointmentMapper.findMyOngoingAppointments(memberId, true, now);
            assertEquals(
                    List.of(paymentPendingId, ongoingId, farFutureId,
                            finishedId, olderFinishedId),
                    all.stream().map(MyOngoingAppointment::getAppointmentId).toList());
            assertEquals("PAYMENT_PENDING", all.get(0).getAppointmentStatus());
            assertEquals("COMPLETED", all.get(3).getAppointmentStatus());
        });
    }

    /**
     * 예정/지난 경계가 DB 서버 시계가 아니라 애플리케이션이 넘긴 시각으로 갈리는지
     * 분 단위로 확인한다.
     *
     * DB가 UTC이고 애플리케이션이 Asia/Seoul이면 두 시계는 9시간 어긋난다. 경계에서
     * 몇 분 떨어진 약속들은 그 시차 안에 들어가므로, 분류가 DB 시계를 따르면 예정과
     * 지난 것이 통째로 뒤집힌다. 하루 이상 여백을 두면 이 차이가 가려지기 때문에
     * 반드시 분 단위로 확인해야 한다.
     */
    @Test
    void findMyOngoingAppointments_classifiesAroundApplicationClock() {
        transactionTemplate.executeWithoutResult(status -> {
            status.setRollbackOnly();
            long memberId = insertMember();
            long itemId = insertVisibleEventItem(memberId);
            long tripId = insertTrip(memberId);

            LocalDateTime now = LocalDateTime.now();
            long justAheadId = insertAppointment(
                    itemId, memberId, "RECRUITING", now.plusMinutes(5));
            long laterAheadId = insertAppointment(
                    itemId, memberId, "RECRUITING", now.plusMinutes(30));
            long justPastId = insertAppointment(
                    itemId, memberId, "COMPLETED", now.minusMinutes(5));
            long olderPastId = insertAppointment(
                    itemId, memberId, "COMPLETED", now.minusMinutes(30));
            for (long appointmentId : new long[] {
                    justAheadId, laterAheadId, justPastId, olderPastId}) {
                insertActiveMembership(appointmentId, memberId, tripId);
            }

            List<MyOngoingAppointment> all =
                    appointmentMapper.findMyOngoingAppointments(memberId, true, now);

            // 예정은 임박한 순으로 위, 지난 것은 최근 순으로 아래.
            assertEquals(
                    List.of(justAheadId, laterAheadId, justPastId, olderPastId),
                    all.stream().map(MyOngoingAppointment::getAppointmentId).toList());
        });
    }

    /**
     * 공개 목록은 참여할 수 있는 약속을 임박한 순으로 먼저, 이미 시작한 약속을 최근
     * 순으로 뒤에 둔다. 이미 시작해 진행 중인 약속도 참여할 수 없으니 뒤로 간다.
     * 개수는 카드와 같은 대상을 센다.
     */
    @Test
    void searchAppointments_ordersUpcomingFirstThenRecentPast() {
        transactionTemplate.executeWithoutResult(status -> {
            status.setRollbackOnly();
            LocalDateTime now = LocalDateTime.now();
            ListFixture fixture = insertListFixture(now);

            AppointmentSearchRequest request = searchRequest(fixture.itemId(), 20);
            List<Long> ids = searchIds(request, 0, now);

            assertEquals(fixture.expectedOrder(), ids);
            assertEquals(
                    fixture.expectedOrder().size(),
                    appointmentMapper.countAppointments(request));
        });
    }

    /**
     * 경계는 애플리케이션이 넘긴 시각이다. CI MySQL은 UTC라 DB 시계로 가르면 몇 분
     * 떨어진 약속들이 시차 안에 들어가 통째로 반대쪽에 선다. 그래서 분 단위로 본다.
     */
    @Test
    void searchAppointments_classifiesAroundApplicationClock() {
        transactionTemplate.executeWithoutResult(status -> {
            status.setRollbackOnly();
            long memberId = insertMember();
            long itemId = insertVisibleEventItem(memberId);

            LocalDateTime now = LocalDateTime.now();
            long justAheadId = insertAppointment(
                    itemId, memberId, "RECRUITING", now.plusMinutes(5));
            long laterAheadId = insertAppointment(
                    itemId, memberId, "RECRUITING", now.plusMinutes(30));
            long justPastId = insertAppointment(
                    itemId, memberId, "IN_PROGRESS", now.minusMinutes(5));
            long olderPastId = insertAppointment(
                    itemId, memberId, "IN_PROGRESS", now.minusMinutes(30));

            assertEquals(
                    List.of(justAheadId, laterAheadId, justPastId, olderPastId),
                    searchIds(searchRequest(itemId, 20), 0, now));
        });
    }

    /**
     * 같은 시각의 약속이 페이지 경계에 걸려도 카드가 겹치거나 빠지지 않는다.
     * 마지막 정렬 키(appointment_id)가 빠지면 동점의 순서가 호출마다 달라질 수 있다.
     */
    @Test
    void searchAppointments_pagesWithoutOverlapOrGap() {
        transactionTemplate.executeWithoutResult(status -> {
            status.setRollbackOnly();
            LocalDateTime now = LocalDateTime.now();
            ListFixture fixture = insertListFixture(now);

            int size = 2;
            AppointmentSearchRequest request = searchRequest(fixture.itemId(), size);
            List<Long> paged = new ArrayList<>();
            for (int page = 0; page * size < fixture.expectedOrder().size(); page++) {
                paged.addAll(searchIds(request, page * size, now));
            }

            assertEquals(fixture.expectedOrder(), paged);
        });
    }

    private record ListFixture(long itemId, List<Long> expectedOrder) {
    }

    /**
     * 공개 목록용 약속 묶음. 같은 시각 쌍(sameTime*)은 size=2일 때 첫 페이지와 둘째
     * 페이지의 경계에 걸리도록 두 번째·세 번째 자리에 둔다.
     */
    private ListFixture insertListFixture(LocalDateTime now) {
        long memberId = insertMember();
        long itemId = insertVisibleEventItem(memberId);

        long soonId = insertAppointment(
                itemId, memberId, "RECRUITING", now.plusDays(2));
        // 같은 시각이면 나중에 만든(id가 큰) 약속이 먼저다.
        long sameTimeOlderId = insertAppointment(
                itemId, memberId, "FULL", now.plusDays(5));
        long sameTimeNewerId = insertAppointment(
                itemId, memberId, "RECRUITING", now.plusDays(5));
        long laterId = insertAppointment(
                itemId, memberId, "RECRUITING", now.plusDays(10));
        long startedId = insertAppointment(
                itemId, memberId, "IN_PROGRESS", now.minusHours(1));
        long awaitingId = insertAppointment(
                itemId, memberId, "AWAITING_ATTENDANCE", now.minusDays(3));
        // 출석을 확정하지 않아 COMPLETED가 되지 못하고 쌓인 약속.
        long staleId = insertAppointment(
                itemId, memberId, "IN_PROGRESS", now.minusDays(10));
        // 목록 대상이 아니다.
        insertAppointment(itemId, memberId, "COMPLETED", now.minusDays(1));
        insertAppointment(itemId, memberId, "PAYMENT_PENDING", now.plusDays(1));

        return new ListFixture(itemId, List.of(
                soonId, sameTimeNewerId, sameTimeOlderId, laterId,
                startedId, awaitingId, staleId));
    }

    /** 다른 테스트 데이터가 섞이지 않도록 이번에 만든 항목으로만 좁힌다. */
    private static AppointmentSearchRequest searchRequest(long itemId, int size) {
        AppointmentSearchRequest request = new AppointmentSearchRequest();
        request.setItemId(itemId);
        request.setSize(size);
        return request;
    }

    private List<Long> searchIds(
            AppointmentSearchRequest request, int offset, LocalDateTime now) {
        return appointmentMapper.searchAppointments(request, offset, now).stream()
                .map(Appointment::getAppointmentId)
                .toList();
    }

    private long insertMember() {
        jdbcTemplate.update(
                "INSERT INTO members (display_name) VALUES ('약속 목록 통합 테스트 회원')");
        return lastInsertId();
    }

    /** chk_explore_items_review — APPROVED는 검수자·검수 시각이 필수라 함께 넣는다. */
    private long insertVisibleEventItem(long reviewerId) {
        jdbcTemplate.update(
                "INSERT INTO explore_items "
                        + "(item_type, approval_status, visibility_status, "
                        + "reviewed_by, reviewed_at) "
                        + "VALUES ('EVENT', 'APPROVED', 'VISIBLE', ?, CURRENT_TIMESTAMP)",
                reviewerId);
        return lastInsertId();
    }

    private long insertTrip(long memberId) {
        jdbcTemplate.update(
                "INSERT INTO trips (member_id, title, start_date, end_date) "
                        + "VALUES (?, '약속 목록 통합 테스트 여행', "
                        + "'2026-08-01', '2026-08-31')",
                memberId);
        return lastInsertId();
    }

    private long insertAppointment(
            long itemId, long hostMemberId, String appointmentStatus,
            LocalDateTime activityStartAt) {
        jdbcTemplate.update(
                "INSERT INTO appointments "
                        + "(item_id, host_member_id, language_code, appointment_name, "
                        + "max_members, deposit_amount, "
                        + "appointment_status, activity_start_at, activity_end_at) "
                        + "VALUES (?, ?, 'en', '약속 목록 통합 테스트', 5, 10000, ?, ?, ?)",
                itemId, hostMemberId,
                appointmentStatus, activityStartAt, activityStartAt.plusHours(2));
        return lastInsertId();
    }

    private void insertActiveMembership(long appointmentId, long memberId, long tripId) {
        jdbcTemplate.update(
                "INSERT INTO appointment_members "
                        + "(appointment_id, member_id, trip_id, membership_status) "
                        + "VALUES (?, ?, ?, 'ACTIVE')",
                appointmentId, memberId, tripId);
    }

    private long lastInsertId() {
        Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        if (id == null || id == 0L) {
            throw new IllegalStateException("LAST_INSERT_ID()를 읽지 못했습니다");
        }
        return id;
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    name + " is required for MySQL integration tests"
            );
        }
        return value;
    }
}
