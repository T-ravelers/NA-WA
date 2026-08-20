package me.nawa.member.mapper;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import me.nawa.auth.mapper.OAuthAccountMapper;
import me.nawa.auth.oauth.account.OAuthMemberInsert;
import me.nawa.config.MySqlSchemaExtension;
import me.nawa.member.domain.MemberProfile;
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
