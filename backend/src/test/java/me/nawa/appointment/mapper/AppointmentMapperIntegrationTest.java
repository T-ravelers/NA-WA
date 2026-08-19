package me.nawa.appointment.mapper;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import me.nawa.appointment.domain.MyOngoingAppointment;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * findMyOngoingAppointments의 scope 분기(진행 중만 / 취소 제외 전체)와 정렬,
 * explore_items 조인으로 얻는 itemType을 실제 MySQL에서 검증한다.
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

            // 분류가 MySQL NOW() 기준이라 시각 차가 하루 미만이어도 안전하게
            // 미래·과거 여백을 하루 이상 둔다.
            LocalDateTime now = LocalDateTime.now();
            long paymentPendingId = insertAppointment(
                    itemId, memberId, "PAYMENT_PENDING", now.plusDays(1));
            long ongoingId = insertAppointment(
                    itemId, memberId, "IN_PROGRESS", now.plusDays(2));
            long farFutureId = insertAppointment(
                    itemId, memberId, "CONFIRMED", now.plusDays(10));
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
                    appointmentMapper.findMyOngoingAppointments(memberId, false);
            assertEquals(1, ongoing.size());
            assertEquals(ongoingId, ongoing.get(0).getAppointmentId());
            assertEquals(itemId, ongoing.get(0).getItemId());
            assertEquals("EVENT", ongoing.get(0).getItemType());
            assertEquals("IN_PROGRESS", ongoing.get(0).getAppointmentStatus());

            // 전체: 취소 제외. 예정은 임박한 순으로 위, 지난 것은 최근 순으로 아래.
            // PAYMENT_PENDING은 포함된다 — 근거는 APPOINTMENT_API.md.
            List<MyOngoingAppointment> all =
                    appointmentMapper.findMyOngoingAppointments(memberId, true);
            assertEquals(
                    List.of(paymentPendingId, ongoingId, farFutureId,
                            finishedId, olderFinishedId),
                    all.stream().map(MyOngoingAppointment::getAppointmentId).toList());
            assertEquals("PAYMENT_PENDING", all.get(0).getAppointmentStatus());
            assertEquals("COMPLETED", all.get(3).getAppointmentStatus());
        });
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
                        + "max_members, join_deadline, deposit_amount, "
                        + "appointment_status, activity_start_at, activity_end_at) "
                        + "VALUES (?, ?, 'en', '약속 목록 통합 테스트', 5, ?, 10000, ?, ?, ?)",
                itemId, hostMemberId, activityStartAt.minusDays(1),
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
