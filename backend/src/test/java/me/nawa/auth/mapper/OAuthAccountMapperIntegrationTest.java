package me.nawa.auth.mapper;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import me.nawa.auth.oauth.account.OAuthLoginAccount;
import me.nawa.auth.oauth.account.OAuthMemberInsert;
import me.nawa.member.domain.MemberAuthState;
import me.nawa.member.domain.MemberProfile;
import me.nawa.member.mapper.MemberMapper;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(
        named = "RUN_MYSQL_INTEGRATION_TESTS",
        matches = "(?i)true"
)
class OAuthAccountMapperIntegrationTest {
    private static HikariDataSource dataSource;
    private static OAuthAccountMapper mapper;
    private static MemberMapper memberMapper;
    private static JdbcTemplate jdbcTemplate;
    private static TransactionTemplate transactionTemplate;

    @BeforeAll
    static void setUpDatabase() throws Exception {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setDriverClassName(requiredEnvironment(
                "DATABASE_DRIVER"
        ));
        hikariConfig.setJdbcUrl(requiredEnvironment("DATABASE_URL"));
        hikariConfig.setUsername(requiredEnvironment("DATABASE_USERNAME"));
        hikariConfig.setPassword(requiredEnvironment("DATABASE_PASSWORD"));
        hikariConfig.setMaximumPoolSize(2);
        hikariConfig.setMinimumIdle(0);
        dataSource = new HikariDataSource(hikariConfig);

        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setConfigLocation(new ClassPathResource(
                "mybatis-config.xml"
        ));
        SqlSessionFactory sqlSessionFactory = factoryBean.getObject();
        sqlSessionFactory.getConfiguration().addMapper(
                OAuthAccountMapper.class
        );
        sqlSessionFactory.getConfiguration().addMapper(MemberMapper.class);
        SqlSessionTemplate sqlSessionTemplate = new SqlSessionTemplate(
                sqlSessionFactory
        );
        mapper = sqlSessionTemplate.getMapper(
                OAuthAccountMapper.class
        );
        memberMapper = sqlSessionTemplate.getMapper(MemberMapper.class);
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
    void memberAndEmailLessLineAccount_insertAndReadInOneTransaction() {
        String providerUserId = "codex-line-" + UUID.randomUUID();

        transactionTemplate.executeWithoutResult(status -> {
            status.setRollbackOnly();
            OAuthMemberInsert member = new OAuthMemberInsert(
                    "LINE Traveler",
                    null
            );

            assertEquals(1, mapper.insertMember(member));
            assertTrue(member.getMemberId() > 0);
            assertEquals(
                    1,
                    mapper.insertSocialAccount(
                            member.getMemberId(),
                            "line",
                            providerUserId,
                            null
                    )
            );

            OAuthLoginAccount account = mapper.findLoginAccount(
                    "line",
                    providerUserId
            );
            assertEquals(member.getMemberId(), account.getMemberId());
            assertEquals("ACTIVE", account.getMemberStatus());
            assertFalse(account.isMemberDeleted());
            assertFalse(account.isSocialAccountDeleted());
            MemberProfile profile = memberMapper.findProfile(
                    member.getMemberId()
            );
            assertEquals(member.getMemberId(), profile.getMemberId());
            assertEquals("LINE Traveler", profile.getDisplayName());
            assertEquals("en", profile.getPreferredLanguage());
            assertFalse(profile.isOnboardingCompleted());
            assertFalse(profile.isDeleted());

            MemberAuthState activeState = memberMapper.findAuthState(
                    member.getMemberId()
            );
            assertEquals("ACTIVE", activeState.getMemberStatus());
            assertFalse(activeState.isDeleted());

            jdbcTemplate.update(
                    "UPDATE members SET member_status = 'SUSPENDED' "
                            + "WHERE member_id = ?",
                    member.getMemberId()
            );
            MemberAuthState suspendedState = memberMapper.findAuthState(
                    member.getMemberId()
            );
            assertEquals("SUSPENDED", suspendedState.getMemberStatus());
            assertFalse(suspendedState.isDeleted());

            jdbcTemplate.update(
                    "UPDATE members SET deleted_at = CURRENT_TIMESTAMP "
                            + "WHERE member_id = ?",
                    member.getMemberId()
            );
            assertTrue(memberMapper.findAuthState(
                    member.getMemberId()
            ).isDeleted());
            assertNull(mapper.findLoginAccount(
                    "google",
                    providerUserId
            ));
        });

        OAuthLoginAccount rolledBack = transactionTemplate.execute(
                status -> mapper.findLoginAccount("line", providerUserId)
        );
        assertNull(rolledBack);
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
