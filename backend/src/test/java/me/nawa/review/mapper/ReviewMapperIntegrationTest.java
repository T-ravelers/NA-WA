package me.nawa.review.mapper;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * findReviewedAppointmentMemberIds가 실제 MySQL에서 작성자별로만 걸러 오는지,
 * 소프트 삭제된 후기를 빼는지 검증한다. 컬럼명·조건은 SQL이 실행돼야만 드러난다.
 *
 * 모든 테스트는 rollback-only 트랜잭션 안에서 돌므로 데이터가 남지 않는다.
 */
@ExtendWith(MySqlSchemaExtension.class)
@EnabledIfEnvironmentVariable(
        named = "RUN_MYSQL_INTEGRATION_TESTS",
        matches = "(?i)true"
)
class ReviewMapperIntegrationTest {
    private static HikariDataSource dataSource;
    private static ReviewMapper reviewMapper;
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
        sqlSessionFactory.getConfiguration().addMapper(ReviewMapper.class);
        SqlSessionTemplate sqlSessionTemplate =
                new SqlSessionTemplate(sqlSessionFactory);
        reviewMapper = sqlSessionTemplate.getMapper(ReviewMapper.class);
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
    void findReviewedAppointmentMemberIds_scopesToReviewerAndSkipsDeleted() {
        transactionTemplate.executeWithoutResult(status -> {
            status.setRollbackOnly();
            long hostId = insertMember();
            long itemId = insertVisibleEventItem(hostId);
            long tripId = insertTrip(hostId);
            long appointmentId = insertAppointment(itemId, hostId);

            long reviewer = insertMembership(appointmentId, hostId, tripId);
            long targetA = insertMembership(
                    appointmentId, insertMember(), insertTrip(hostId));
            long targetB = insertMembership(
                    appointmentId, insertMember(), insertTrip(hostId));
            long targetC = insertMembership(
                    appointmentId, insertMember(), insertTrip(hostId));

            insertReview(appointmentId, reviewer, targetB, false);
            insertReview(appointmentId, reviewer, targetA, false);
            // 소프트 삭제된 후기는 다시 쓸 수 있어야 하므로 목록에 없어야 한다.
            insertReview(appointmentId, reviewer, targetC, true);
            // 다른 사람이 쓴 후기는 내 목록에 섞이면 안 된다.
            insertReview(appointmentId, targetA, targetB, false);

            assertEquals(
                    List.of(targetA, targetB),
                    reviewMapper.findReviewedAppointmentMemberIds(
                            appointmentId, reviewer));
            assertTrue(reviewMapper.findReviewedAppointmentMemberIds(
                    appointmentId, targetC).isEmpty());
        });
    }

    private long insertMember() {
        jdbcTemplate.update(
                "INSERT INTO members (display_name) VALUES ('후기 통합 테스트 회원')");
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
                        + "VALUES (?, '후기 통합 테스트 여행', '2026-08-01', '2026-08-31')",
                memberId);
        return lastInsertId();
    }

    private long insertAppointment(long itemId, long hostMemberId) {
        jdbcTemplate.update(
                "INSERT INTO appointments "
                        + "(item_id, host_member_id, language_code, appointment_name, "
                        + "max_members, join_deadline, deposit_amount, "
                        + "appointment_status, activity_start_at, activity_end_at) "
                        + "VALUES (?, ?, 'en', '후기 통합 테스트 약속', 5, "
                        + "'2026-08-09 12:00:00', 10000, 'COMPLETED', "
                        + "'2026-08-10 12:00:00', '2026-08-10 14:00:00')",
                itemId, hostMemberId);
        return lastInsertId();
    }

    /** chk_appointment_members_attendance — ATTENDED는 확정 시각이 필수다. */
    private long insertMembership(long appointmentId, long memberId, long tripId) {
        jdbcTemplate.update(
                "INSERT INTO appointment_members "
                        + "(appointment_id, member_id, trip_id, membership_status, "
                        + "attendance_status, attendance_confirmed_at) "
                        + "VALUES (?, ?, ?, 'ACTIVE', 'ATTENDED', CURRENT_TIMESTAMP)",
                appointmentId, memberId, tripId);
        return lastInsertId();
    }

    private void insertReview(
            long appointmentId, long reviewerId, long reviewedId, boolean deleted) {
        jdbcTemplate.update(
                "INSERT INTO member_reviews "
                        + "(appointment_id, reviewer_appointment_member_id, "
                        + "reviewed_appointment_member_id, visibility_status, deleted_at) "
                        + "VALUES (?, ?, ?, 'VISIBLE', ?)",
                appointmentId, reviewerId, reviewedId,
                deleted ? "2026-08-11 00:00:00" : null);
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
