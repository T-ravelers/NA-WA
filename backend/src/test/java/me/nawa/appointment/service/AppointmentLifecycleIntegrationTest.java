package me.nawa.appointment.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import me.nawa.appointment.mapper.AppointmentMapper;
import me.nawa.config.MySqlSchemaExtension;
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
 * closeExpiredRecruitingAppointments·startDueClosedAppointments가 실제 MySQL
 * 위에서 시간 조건에 맞는 약속만 정확히 골라 전환하는지 검증한다.
 *
 * QrPaymentConcurrencyIntegrationTest·AppointmentDepositIntegrationTest와 같은
 * 이유로, 여기서 만드는 AppointmentMapper는 Spring 컨테이너가 관리하는 빈이 아니라
 * 직접 세션을 열어 얻은 매퍼라 @Transactional을 타지 않는다 — 각 UPDATE가 그때그때
 * 커밋된다.
 */
@ExtendWith(MySqlSchemaExtension.class)
@EnabledIfEnvironmentVariable(
    named = "RUN_MYSQL_INTEGRATION_TESTS",
    matches = "(?i)true"
)
class AppointmentLifecycleIntegrationTest {

    private static HikariDataSource dataSource;
    private static AppointmentMapper appointmentMapper;
    private static JdbcTemplate jdbcTemplate;

    private final List<Long> memberIds = new ArrayList<>();
    private final List<Long> eventIds = new ArrayList<>();
    private final List<Long> appointmentIds = new ArrayList<>();

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
                    "DELETE FROM appointment_members WHERE appointment_id IN (" + placeholders + ")",
                    ids
            );
            jdbcTemplate.update(
                    "DELETE FROM appointments WHERE appointment_id IN (" + placeholders + ")",
                    ids
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
            jdbcTemplate.update("DELETE FROM members WHERE member_id IN (" + placeholders + ")", ids);
        }
    }

    // 정원이 차지 않은 약속은 FULL을 거치지 않으므로 RECRUITING인 채로 활동
    // 시작 시각을 맞는다. 이 배치가 RECRUITING을 대상에서 빼면 그런 약속은
    // 활동이 시작돼도 모집 중으로 남는다.
    @Test
    void startDueAppointments_startsRecruitingAndFullPastActivityStart() {
        long eventId = createApprovedEvent();
        long dueRecruitingId = insertAppointment(eventId, "RECRUITING",
                LocalDateTime.now().minusMinutes(1));
        long dueFullId = insertAppointment(eventId, "FULL",
                LocalDateTime.now().minusMinutes(1));
        long notYetDueId = insertAppointment(eventId, "RECRUITING",
                LocalDateTime.now().plusDays(1));

        appointmentMapper.startDueAppointments(LocalDateTime.now());

        assertEquals("IN_PROGRESS", appointmentStatus(dueRecruitingId));
        assertEquals("IN_PROGRESS", appointmentStatus(dueFullId));
        assertEquals("RECRUITING", appointmentStatus(notYetDueId));
    }

    private String appointmentStatus(long appointmentId) {
        return jdbcTemplate.queryForObject(
                "SELECT appointment_status FROM appointments WHERE appointment_id = ?",
                String.class,
                appointmentId
        );
    }

    private long insertAppointment(
            long eventId,
            String status,
            LocalDateTime activityStartAt) {
        long hostMemberId = createMember("방장");
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO appointments "
                            + "(item_id, host_member_id, language_code, appointment_name, "
                            + "max_members, deposit_amount, appointment_status, "
                            + "meeting_place, activity_start_at, activity_end_at) "
                            + "VALUES (?, ?, 'en', 'Lifecycle Test Appointment', 5, 10000, ?, "
                            + "'Test Meeting Place', ?, ?)",
                    Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, eventId);
            statement.setLong(2, hostMemberId);
            statement.setString(3, status);
            statement.setObject(4, activityStartAt);
            statement.setObject(5, activityStartAt.plusHours(2));
            return statement;
        }, keyHolder);
        long appointmentId = keyHolder.getKey().longValue();
        appointmentIds.add(appointmentId);
        return appointmentId;
    }

    private long createMember(String displayName) {
        jdbcTemplate.update(
                "INSERT INTO members (display_name, preferred_language, member_status)"
                        + " VALUES (?, 'en', 'ACTIVE')",
                displayName
        );
        long memberId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        memberIds.add(memberId);
        return memberId;
    }

    private long createApprovedEvent() {
        long creatorMemberId = createMember("주최자");
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
                        + " VALUES (?, 'Lifecycle Test Event', CURRENT_DATE(),"
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
