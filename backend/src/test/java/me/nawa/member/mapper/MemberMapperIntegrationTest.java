package me.nawa.member.mapper;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import me.nawa.auth.mapper.OAuthAccountMapper;
import me.nawa.auth.oauth.account.OAuthMemberInsert;
import me.nawa.config.MySqlSchemaExtension;
import me.nawa.member.domain.MemberProfile;
import me.nawa.member.dto.MemberAppointmentProfileResponse;
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

import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MemberMapper의 SQL을 실제 MySQL에서 실행한다.
 *
 * MemberMapperXmlTest는 XML 파싱만 확인하므로, 갱신 대상 컬럼·영향 행 수·soft delete
 * 조건은 여기서만 검증된다. 모든 테스트는 rollback-only 트랜잭션 안에서 돌므로
 * 데이터가 남지 않는다.
 */
@ExtendWith(MySqlSchemaExtension.class)
@EnabledIfEnvironmentVariable(
        named = "RUN_MYSQL_INTEGRATION_TESTS",
        matches = "(?i)true"
)
class MemberMapperIntegrationTest {
    /** currencies FK를 만족시키기 위해 트랜잭션 안에서만 넣는 테스트 통화. */
    private static final String TEST_CURRENCY = "ZZZ";
    private static final String INACTIVE_CURRENCY = "ZZX";
    private static final String MISSING_CURRENCY = "ZZQ";

    private static HikariDataSource dataSource;
    private static MemberMapper memberMapper;
    private static OAuthAccountMapper accountMapper;
    private static JdbcTemplate jdbcTemplate;
    private static SqlSessionTemplate sqlSessionTemplate;
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
        sqlSessionFactory.getConfiguration().addMapper(MemberMapper.class);
        sqlSessionFactory.getConfiguration().addMapper(OAuthAccountMapper.class);
        sqlSessionTemplate = new SqlSessionTemplate(sqlSessionFactory);
        memberMapper = sqlSessionTemplate.getMapper(MemberMapper.class);
        accountMapper = sqlSessionTemplate.getMapper(OAuthAccountMapper.class);
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
    void updateProfile_persistsEveryField_andReportsAffectedRow() {
        transactionTemplate.executeWithoutResult(status -> {
            status.setRollbackOnly();
            long memberId = insertMember("통합 테스트 회원");
            insertCurrency(TEST_CURRENCY, true);

            int updatedRows = memberMapper.updateProfile(
                    memberId, "새 이름", "https://cdn.example.com/me.png",
                    "KR", "ja", TEST_CURRENCY);

            assertEquals(1, updatedRows);

            sqlSessionTemplate.clearCache();
            MemberProfile profile = memberMapper.findProfile(memberId);
            assertEquals("새 이름", profile.getDisplayName());
            assertEquals("https://cdn.example.com/me.png", profile.getProfileImageUrl());
            assertEquals("KR", profile.getNationalityCode());
            assertEquals("ja", profile.getPreferredLanguage());
            assertEquals(TEST_CURRENCY, profile.getPreferredCurrencyCode());
        });
    }

    @Test
    void updateProfile_keepsAbsentFields_whenPartialUpdate() {
        transactionTemplate.executeWithoutResult(status -> {
            status.setRollbackOnly();
            long memberId = insertMember("부분 갱신 회원");

            assertEquals(1, memberMapper.updateProfile(
                    memberId, null, null, null, "vi", null));

            sqlSessionTemplate.clearCache();
            MemberProfile profile = memberMapper.findProfile(memberId);
            assertEquals("부분 갱신 회원", profile.getDisplayName());
            assertNull(profile.getNationalityCode());
            assertEquals("vi", profile.getPreferredLanguage());
        });
    }

    @Test
    void updateProfile_reportsZeroRows_whenMemberSoftDeleted() {
        transactionTemplate.executeWithoutResult(status -> {
            status.setRollbackOnly();
            long memberId = insertMember("삭제 경합 회원");
            jdbcTemplate.update(
                    "UPDATE members SET deleted_at = CURRENT_TIMESTAMP WHERE member_id = ?",
                    memberId);

            assertEquals(0, memberMapper.updateProfile(
                    memberId, null, null, null, "ja", null));
        });
    }

    @Test
    void existsActiveCurrency_distinguishesActiveInactiveMissing() {
        transactionTemplate.executeWithoutResult(status -> {
            status.setRollbackOnly();
            insertCurrency(TEST_CURRENCY, true);
            insertCurrency(INACTIVE_CURRENCY, false);

            assertTrue(memberMapper.existsActiveCurrency(TEST_CURRENCY));
            assertFalse(memberMapper.existsActiveCurrency(INACTIVE_CURRENCY));
            assertFalse(memberMapper.existsActiveCurrency(MISSING_CURRENCY));
        });
    }

    @Test
    void completeOnboarding_setsCompletionOnce_andStaysIdempotent() {
        transactionTemplate.executeWithoutResult(status -> {
            status.setRollbackOnly();
            long memberId = insertMember("온보딩 회원");
            insertCurrency(TEST_CURRENCY, true);

            assertEquals(1, memberMapper.completeOnboarding(
                    memberId, "온보딩 이름", "JP", "ja", TEST_CURRENCY));

            sqlSessionTemplate.clearCache();
            assertTrue(memberMapper.findProfile(memberId).isOnboardingCompleted());

            // 완료 시각을 과거로 고정한 뒤 재호출한다. COALESCE가 최초 값을 지켜야 한다.
            Timestamp firstCompletedAt = Timestamp.valueOf("2020-01-01 00:00:00");
            jdbcTemplate.update(
                    "UPDATE members SET onboarding_completed_at = ? WHERE member_id = ?",
                    firstCompletedAt, memberId);

            assertEquals(1, memberMapper.completeOnboarding(
                    memberId, "바뀐 이름", "KR", "en", TEST_CURRENCY));

            Timestamp completedAt = jdbcTemplate.queryForObject(
                    "SELECT onboarding_completed_at FROM members WHERE member_id = ?",
                    Timestamp.class, memberId);
            assertEquals(firstCompletedAt, completedAt);

            sqlSessionTemplate.clearCache();
            MemberProfile profile = memberMapper.findProfile(memberId);
            assertEquals("바뀐 이름", profile.getDisplayName());
            assertEquals("KR", profile.getNationalityCode());
            assertTrue(profile.isOnboardingCompleted());
        });
    }

    @Test
    void completeOnboarding_reportsZeroRows_whenMemberSoftDeleted() {
        transactionTemplate.executeWithoutResult(status -> {
            status.setRollbackOnly();
            long memberId = insertMember("삭제된 온보딩 회원");
            insertCurrency(TEST_CURRENCY, true);
            jdbcTemplate.update(
                    "UPDATE members SET deleted_at = CURRENT_TIMESTAMP WHERE member_id = ?",
                    memberId);

            assertEquals(0, memberMapper.completeOnboarding(
                    memberId, "이름", "JP", "ja", TEST_CURRENCY));
        });
    }

    @Test
    void markAsMerchant_setsAccountTypeOnce_andRejectsSecondRegistration() {
        transactionTemplate.executeWithoutResult(status -> {
            status.setRollbackOnly();
            long memberId = insertMember("가맹점 회원");

            assertEquals(1, memberMapper.markAsMerchant(memberId, "○○ 카페"));

            sqlSessionTemplate.clearCache();
            MemberProfile profile = memberMapper.findProfile(memberId);
            assertEquals("MERCHANT", profile.getAccountType());
            assertEquals("○○ 카페", profile.getDisplayName());
            // 손님용 온보딩 화면이 생겨도 가맹점이 끌려가지 않도록 함께 채운다.
            assertTrue(profile.isOnboardingCompleted());

            // account_type = 'TRAVELER' 조건이 재등록을 막는다. 상호명도 덮어쓰지 않는다.
            assertEquals(0, memberMapper.markAsMerchant(memberId, "△△ 식당"));

            sqlSessionTemplate.clearCache();
            assertEquals("○○ 카페", memberMapper.findProfile(memberId).getDisplayName());
        });
    }

    @Test
    void markAsMerchant_reportsZeroRows_whenMemberSoftDeleted() {
        transactionTemplate.executeWithoutResult(status -> {
            status.setRollbackOnly();
            long memberId = insertMember("삭제된 가맹점 회원");
            jdbcTemplate.update(
                    "UPDATE members SET deleted_at = CURRENT_TIMESTAMP WHERE member_id = ?",
                    memberId);

            assertEquals(0, memberMapper.markAsMerchant(memberId, "○○ 카페"));
        });
    }

    @Test
    void existsSharedAppointment_requiresBothActiveInVisibleAppointment() {
        transactionTemplate.executeWithoutResult(status -> {
            status.setRollbackOnly();
            long viewer = insertMember("조회하는 회원");
            long mate = insertMember("같은 약속 회원");
            long stranger = insertMember("다른 약속 회원");
            long left = insertMember("나간 회원");
            long pending = insertMember("결제 전 회원");
            long itemId = insertVisibleEventItem(viewer);

            long recruiting = insertAppointment(itemId, viewer, "RECRUITING");
            insertMembership(recruiting, viewer, "ACTIVE");
            insertMembership(recruiting, mate, "ACTIVE");
            insertMembership(recruiting, left, "LEFT");
            insertMembership(recruiting, pending, "PENDING");
            long other = insertAppointment(itemId, stranger, "RECRUITING");
            insertMembership(other, stranger, "ACTIVE");

            assertTrue(memberMapper.existsSharedAppointment(viewer, mate));
            assertTrue(memberMapper.existsSharedAppointment(mate, viewer));
            assertFalse(memberMapper.existsSharedAppointment(viewer, stranger));
            assertFalse(memberMapper.existsSharedAppointment(viewer, left));
            assertFalse(memberMapper.existsSharedAppointment(left, viewer));
            assertFalse(memberMapper.existsSharedAppointment(viewer, pending));
        });
    }

    @Test
    void existsSharedAppointment_ignoresCancelledAndPaymentPendingAppointments() {
        transactionTemplate.executeWithoutResult(status -> {
            status.setRollbackOnly();
            long viewer = insertMember("조회하는 회원");
            long mate = insertMember("같은 약속 회원");
            long itemId = insertVisibleEventItem(viewer);

            for (String appointmentStatus : new String[] {"CANCELLED", "PAYMENT_PENDING"}) {
                long appointmentId = insertAppointment(itemId, viewer, appointmentStatus);
                insertMembership(appointmentId, viewer, "ACTIVE");
                insertMembership(appointmentId, mate, "ACTIVE");
            }

            assertFalse(memberMapper.existsSharedAppointment(viewer, mate));
        });
    }

    /** 허용 상태마다 다른 회원 쌍을 써서, 한 상태가 빠져도 다른 상태의 약속이 가리지 못하게 한다. */
    @Test
    void existsSharedAppointment_acceptsEveryVisibleAppointmentStatus() {
        transactionTemplate.executeWithoutResult(status -> {
            status.setRollbackOnly();
            String[] visibleStatuses = {
                    "RECRUITING", "FULL", "IN_PROGRESS", "AWAITING_ATTENDANCE", "COMPLETED"
            };

            for (String appointmentStatus : visibleStatuses) {
                long viewer = insertMember("조회하는 회원 " + appointmentStatus);
                long mate = insertMember("같은 약속 회원 " + appointmentStatus);
                long appointmentId = insertAppointment(
                        insertVisibleEventItem(viewer), viewer, appointmentStatus);
                insertMembership(appointmentId, viewer, "ACTIVE");
                insertMembership(appointmentId, mate, "ACTIVE");

                assertTrue(memberMapper.existsSharedAppointment(viewer, mate), appointmentStatus);
            }
        });
    }

    /** 요청자 참여 행·대상 참여 행·약속 중 하나라도 soft delete되면 열람 근거가 아니다. */
    @Test
    void existsSharedAppointment_ignoresSoftDeletedMembershipsAndAppointment() {
        transactionTemplate.executeWithoutResult(status -> {
            status.setRollbackOnly();
            String[] deleteTargets = {"viewer", "mate", "appointment"};

            for (String deleteTarget : deleteTargets) {
                long viewer = insertMember("조회하는 회원 " + deleteTarget);
                long mate = insertMember("같은 약속 회원 " + deleteTarget);
                long appointmentId = insertAppointment(
                        insertVisibleEventItem(viewer), viewer, "RECRUITING");
                insertMembership(appointmentId, viewer, "ACTIVE");
                insertMembership(appointmentId, mate, "ACTIVE");
                assertTrue(memberMapper.existsSharedAppointment(viewer, mate), deleteTarget);

                if ("appointment".equals(deleteTarget)) {
                    jdbcTemplate.update(
                            "UPDATE appointments SET deleted_at = CURRENT_TIMESTAMP "
                                    + "WHERE appointment_id = ?",
                            appointmentId);
                } else {
                    jdbcTemplate.update(
                            "UPDATE appointment_members SET deleted_at = CURRENT_TIMESTAMP "
                                    + "WHERE appointment_id = ? AND member_id = ?",
                            appointmentId, "viewer".equals(deleteTarget) ? viewer : mate);
                }

                sqlSessionTemplate.clearCache();
                assertFalse(memberMapper.existsSharedAppointment(viewer, mate), deleteTarget);
            }
        });
    }

    /** 출석 이력이 없는 회원도 noShowCount가 0으로, completionRate가 null로 매핑된다. */
    @Test
    void findAppointmentProfile_mapsEmptyAttendance() {
        transactionTemplate.executeWithoutResult(status -> {
            status.setRollbackOnly();
            long memberId = insertMember("이력 없는 회원");

            MemberAppointmentProfileResponse profile =
                    memberMapper.findAppointmentProfile(memberId);

            assertEquals(memberId, profile.getMemberId());
            assertNull(profile.getCompletionRate());
            assertEquals(0, profile.getNoShowCount());
            assertNull(profile.getAverageRating());
            assertEquals(0, profile.getReviewCount());
        });
    }

    /**
     * 집계는 조회한 회원의 참여 행과 그 회원이 받은 후기만 센다(#583).
     *
     * 같은 약속에 다른 회원의 출석·후기를 섞어 두고, 두 회원의 값이 서로 새지 않는지 본다.
     * 빼야 하는 행(삭제된 참여 행과 그 행이 받은 후기, HIDDEN·삭제된 후기, 삭제된 점수)은
     * 들어가면 값이 달라지도록 1점으로 넣는다.
     */
    @Test
    void findAppointmentProfile_aggregatesOnlyTargetMembersLiveRows() {
        transactionTemplate.executeWithoutResult(status -> {
            status.setRollbackOnly();
            long target = insertMember("조회 대상 회원");
            long mateA = insertMember("후기 쓴 회원 A");
            long mateB = insertMember("후기 쓴 회원 B");
            long other = insertMember("같은 약속의 다른 회원");
            long itemId = insertVisibleEventItem(target);

            // 약속 1: 출석. A의 후기 중 한 점수는 삭제됐다. 다른 회원은 노쇼이고 후기를 받았다.
            long first = insertAppointment(itemId, target, "COMPLETED");
            long targetFirst = insertAttendance(first, target, "ATTENDED");
            long mateAFirst = insertAttendance(first, mateA, "ATTENDED");
            long otherFirst = insertAttendance(first, other, "NO_SHOW");
            long scoredReview = insertReview(first, mateAFirst, targetFirst, "VISIBLE", 5, 5, 1);
            jdbcTemplate.update(
                    "UPDATE member_review_scores SET deleted_at = CURRENT_TIMESTAMP "
                            + "WHERE review_id = ? AND review_category = 'COMMUNICATION'",
                    scoredReview);
            insertReview(first, targetFirst, mateAFirst, "VISIBLE", 1, 1, 1);
            insertReview(first, mateAFirst, otherFirst, "VISIBLE", 1, 1, 1);

            // 약속 2: 출석. B의 후기가 그대로 들어간다.
            long second = insertAppointment(itemId, target, "COMPLETED");
            long targetSecond = insertAttendance(second, target, "ATTENDED");
            long mateBSecond = insertAttendance(second, mateB, "ATTENDED");
            insertReview(second, mateBSecond, targetSecond, "VISIBLE", 3, 4, 4);

            // 약속 3: 노쇼. 숨긴 후기와 삭제된 후기는 빠진다.
            long third = insertAppointment(itemId, target, "COMPLETED");
            long targetThird = insertAttendance(third, target, "NO_SHOW");
            long mateAThird = insertAttendance(third, mateA, "ATTENDED");
            long mateBThird = insertAttendance(third, mateB, "ATTENDED");
            insertReview(third, mateAThird, targetThird, "HIDDEN", 1, 1, 1);
            long deletedReview = insertReview(third, mateBThird, targetThird, "VISIBLE", 1, 1, 1);
            jdbcTemplate.update(
                    "UPDATE member_reviews SET deleted_at = CURRENT_TIMESTAMP WHERE review_id = ?",
                    deletedReview);

            // 약속 4: 출석 미확정이라 출석률 분모에 들어가지 않는다.
            insertAttendance(insertAppointment(itemId, target, "AWAITING_ATTENDANCE"),
                    target, "PENDING");

            // 약속 5: 삭제된 참여 행. 출석도, 이 행이 받은 후기도 빠진다.
            long fifth = insertAppointment(itemId, target, "COMPLETED");
            long targetFifth = insertAttendance(fifth, target, "ATTENDED");
            long mateAFifth = insertAttendance(fifth, mateA, "ATTENDED");
            insertReview(fifth, mateAFifth, targetFifth, "VISIBLE", 1, 1, 1);
            jdbcTemplate.update(
                    "UPDATE appointment_members SET deleted_at = CURRENT_TIMESTAMP "
                            + "WHERE appointment_member_id = ?",
                    targetFifth);

            MemberAppointmentProfileResponse profile =
                    memberMapper.findAppointmentProfile(target);

            // 출석 2 / 확정 3(출석 2 + 노쇼 1) = 66.7% → 67
            assertEquals(67, profile.getCompletionRate());
            assertEquals(1, profile.getNoShowCount());
            // 남는 점수는 5, 5(약속 1)와 3, 4, 4(약속 2)다.
            assertEquals(4.2, profile.getAverageRating(), 1e-9);
            assertEquals(2, profile.getReviewCount());

            MemberAppointmentProfileResponse otherProfile =
                    memberMapper.findAppointmentProfile(other);

            assertEquals(0, otherProfile.getCompletionRate());
            assertEquals(1, otherProfile.getNoShowCount());
            assertEquals(1.0, otherProfile.getAverageRating(), 1e-9);
            assertEquals(1, otherProfile.getReviewCount());
        });
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

    private long insertAppointment(long itemId, long hostMemberId, String appointmentStatus) {
        jdbcTemplate.update(
                "INSERT INTO appointments "
                        + "(item_id, host_member_id, language_code, appointment_name, "
                        + "max_members, deposit_amount, "
                        + "appointment_status, activity_start_at, activity_end_at) "
                        + "VALUES (?, ?, 'en', '프로필 통합 테스트 약속', 5, 10000, ?, "
                        + "'2026-08-10 12:00:00', '2026-08-10 14:00:00')",
                itemId, hostMemberId, appointmentStatus);
        return lastInsertId();
    }

    /** chk_appointment_members_membership — LEFT만 나간 시각이 필수다. */
    private void insertMembership(long appointmentId, long memberId, String membershipStatus) {
        jdbcTemplate.update(
                "INSERT INTO appointment_members "
                        + "(appointment_id, member_id, membership_status, left_at) "
                        + "VALUES (?, ?, ?, IF(? = 'LEFT', CURRENT_TIMESTAMP, NULL))",
                appointmentId, memberId, membershipStatus, membershipStatus);
    }

    /** chk_appointment_members_attendance — 출석을 확정했으면 확정 시각이 필수다. */
    private long insertAttendance(long appointmentId, long memberId, String attendanceStatus) {
        jdbcTemplate.update(
                "INSERT INTO appointment_members "
                        + "(appointment_id, member_id, membership_status, "
                        + "attendance_status, attendance_confirmed_at) "
                        + "VALUES (?, ?, 'ACTIVE', ?, "
                        + "IF(? = 'PENDING', NULL, CURRENT_TIMESTAMP))",
                appointmentId, memberId, attendanceStatus, attendanceStatus);
        return lastInsertId();
    }

    /** 세 항목 점수를 순서대로(시간 약속·매너·소통) 함께 넣는다. */
    private long insertReview(long appointmentId, long reviewerAppointmentMemberId,
                              long reviewedAppointmentMemberId, String visibilityStatus,
                              int punctuality, int manners, int communication) {
        jdbcTemplate.update(
                "INSERT INTO member_reviews "
                        + "(appointment_id, reviewer_appointment_member_id, "
                        + "reviewed_appointment_member_id, visibility_status) "
                        + "VALUES (?, ?, ?, ?)",
                appointmentId, reviewerAppointmentMemberId, reviewedAppointmentMemberId,
                visibilityStatus);
        long reviewId = lastInsertId();
        jdbcTemplate.update(
                "INSERT INTO member_review_scores (review_id, review_category, rating) "
                        + "VALUES (?, 'PUNCTUALITY', ?), (?, 'MANNERS', ?), "
                        + "(?, 'COMMUNICATION', ?)",
                reviewId, punctuality, reviewId, manners, reviewId, communication);
        return reviewId;
    }

    private long lastInsertId() {
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private long insertMember(String displayName) {
        OAuthMemberInsert member = new OAuthMemberInsert(displayName, null);
        assertEquals(1, accountMapper.insertMember(member));
        return member.getMemberId();
    }

    private void insertCurrency(String currencyCode, boolean active) {
        jdbcTemplate.update(
                "INSERT INTO currencies (currency_code, currency_name, is_active) "
                        + "VALUES (?, ?, ?)",
                currencyCode, "테스트 통화 " + currencyCode, active);
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
