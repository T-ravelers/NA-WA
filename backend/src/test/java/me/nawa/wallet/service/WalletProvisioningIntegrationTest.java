package me.nawa.wallet.service;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import me.nawa.auth.mapper.OAuthAccountMapper;
import me.nawa.auth.oauth.OAuthProvider;
import me.nawa.auth.oauth.account.OAuthLoginAccount;
import me.nawa.auth.oauth.account.OAuthMemberTransactionImpl;
import me.nawa.auth.oauth.identity.OAuthUserProfile;
import me.nawa.member.mapper.MemberMapper;
import me.nawa.wallet.domain.Wallet;
import me.nawa.wallet.mapper.WalletMapper;
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

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 가입 트랜잭션과 지갑 생성이 실제 MySQL 위에서 맞물리는지 검증한다.
 * 단위 테스트(WalletProvisioningServiceImplTest)는 fake mapper로 호출 순서만 보므로
 * 스키마 제약·기본값·롤백은 여기서만 확인된다. V8이 시드한 KRW 통화도 함께 검증된다.
 */
@EnabledIfEnvironmentVariable(
        named = "RUN_MYSQL_INTEGRATION_TESTS",
        matches = "(?i)true"
)
class WalletProvisioningIntegrationTest {
    private static HikariDataSource dataSource;
    private static OAuthAccountMapper accountMapper;
    private static MemberMapper memberMapper;
    private static WalletMapper walletMapper;
    private static TransactionTemplate transactionTemplate;
    // 한 트랜잭션 안에서 같은 조회를 반복하면 MyBatis 1차 캐시가 이전 결과를 돌려준다.
    // 실제 요청은 매번 새 세션이므로, 요청 경계를 흉내내려면 테스트가 캐시를 비워야 한다.
    private static SqlSessionTemplate sqlSessionTemplate;

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
        sqlSessionFactory.getConfiguration().addMapper(WalletMapper.class);
        sqlSessionTemplate = new SqlSessionTemplate(sqlSessionFactory);
        accountMapper = sqlSessionTemplate.getMapper(OAuthAccountMapper.class);
        memberMapper = sqlSessionTemplate.getMapper(MemberMapper.class);
        walletMapper = sqlSessionTemplate.getMapper(WalletMapper.class);
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
    void newOAuthMember_getsActiveKrwWalletWithZeroBalance() {
        OAuthUserProfile profile = newProfile();
        OAuthMemberTransactionImpl transaction = new OAuthMemberTransactionImpl(
                accountMapper,
                new WalletProvisioningServiceImpl(walletMapper)
        );

        transactionTemplate.executeWithoutResult(status -> {
            status.setRollbackOnly();
            OAuthLoginAccount account = transaction.resolveOrCreate(profile);

            Wallet wallet = walletMapper.findByMemberId(account.getMemberId());
            assertNotNull(
                    wallet,
                    "가입 직후 GET /api/v1/wallet이 보는 경로로 지갑이 조회돼야 한다"
            );
            assertTrue(wallet.getWalletId() > 0);
            assertEquals("KRW", wallet.getCurrencyCode());
            assertEquals("ACTIVE", wallet.getWalletStatus());
            assertEquals(
                    0,
                    BigDecimal.ZERO.compareTo(wallet.getAvailableBalance()),
                    "스키마 기본값대로 잔액은 0이어야 한다"
            );
        });
    }

    @Test
    void walletProvisioningFailure_rollsBackMemberAndSocialAccount() {
        OAuthUserProfile profile = newProfile();
        AtomicLong attemptedMemberId = new AtomicLong();
        // 지갑 생성만 실패시켜, 앞서 INSERT된 members·social_accounts가 함께 되돌아가는지 본다
        WalletProvisioningService failing = memberId -> {
            attemptedMemberId.set(memberId);
            throw new IllegalStateException("Failed to insert wallet");
        };
        OAuthMemberTransactionImpl transaction = new OAuthMemberTransactionImpl(
                accountMapper,
                failing
        );

        assertThrows(
                IllegalStateException.class,
                () -> transactionTemplate.executeWithoutResult(
                        status -> transaction.resolveOrCreate(profile)
                )
        );
        assertTrue(
                attemptedMemberId.get() > 0,
                "지갑 생성은 member INSERT 이후에 시도돼야 한다"
        );

        transactionTemplate.executeWithoutResult(status -> {
            status.setRollbackOnly();
            assertNull(
                    accountMapper.findLoginAccount(
                            profile.getProvider().getRegistrationId(),
                            profile.getProviderUserId()
                    ),
                    "social_accounts가 롤백돼야 한다"
            );
            assertNull(
                    memberMapper.findProfile(attemptedMemberId.get()),
                    "members가 롤백돼야 한다"
            );
        });
    }

    // #94 — uq_wallet_owners_member·uq_wallets_owner에 deleted_at이 없어 재생성이 불가능하다.
    // soft-delete된 행이 남은 회원에게 provisionForMember를 다시 돌려도 같은 지갑이 살아나야 한다.
    @Test
    void softDeletedWallet_isRestoredWithSameIdentityAndBalance() {
        OAuthUserProfile profile = newProfile();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        WalletProvisioningServiceImpl provisioning = new WalletProvisioningServiceImpl(
                walletMapper
        );
        OAuthMemberTransactionImpl transaction = new OAuthMemberTransactionImpl(
                accountMapper,
                provisioning
        );

        transactionTemplate.executeWithoutResult(status -> {
            status.setRollbackOnly();
            OAuthLoginAccount account = transaction.resolveOrCreate(profile);
            long memberId = account.getMemberId();
            long originalWalletId = walletMapper.findByMemberId(memberId).getWalletId();

            // 탈퇴가 도입되면 생길 상태를 그대로 만든다: 잔액이 남은 지갑을 소유자와 함께 soft-delete한다
            jdbc.update(
                    "UPDATE wallets SET available_balance = 15000, deleted_at = NOW()"
                            + " WHERE wallet_id = ?",
                    originalWalletId
            );
            jdbc.update(
                    "UPDATE wallet_owners SET deleted_at = NOW() WHERE member_id = ?",
                    memberId
            );
            sqlSessionTemplate.clearCache();
            assertNull(
                    walletMapper.findByMemberId(memberId),
                    "soft-delete된 지갑은 조회 경로에서 사라져야 한다 (교착의 전제)"
            );

            long restoredWalletId = provisioning.provisionForMember(memberId);

            assertEquals(
                    originalWalletId,
                    restoredWalletId,
                    "새 지갑을 만들지 않고 기존 wallet_id를 되살려야 한다"
            );
            assertEquals(
                    1,
                    jdbc.queryForObject(
                            "SELECT COUNT(*) FROM wallet_owners WHERE member_id = ?",
                            Integer.class,
                            memberId
                    ),
                    "wallet_owners에 중복 행이 생기면 안 된다"
            );

            sqlSessionTemplate.clearCache();
            Wallet wallet = walletMapper.findByMemberId(memberId);
            assertNotNull(wallet, "복구 후 GET /api/v1/wallet 경로로 다시 조회돼야 한다");
            assertEquals(originalWalletId, wallet.getWalletId());
            assertEquals(
                    0,
                    new BigDecimal("15000").compareTo(wallet.getAvailableBalance()),
                    "복구가 잔액을 초기화하면 안 된다"
            );
        });
    }

    // 복구는 멱등해야 한다. 이미 정상인 회원에게 다시 돌려도 중복 행이 생기면 안 된다.
    @Test
    void provisioningActiveWalletAgain_isIdempotent() {
        OAuthUserProfile profile = newProfile();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        WalletProvisioningServiceImpl provisioning = new WalletProvisioningServiceImpl(
                walletMapper
        );
        OAuthMemberTransactionImpl transaction = new OAuthMemberTransactionImpl(
                accountMapper,
                provisioning
        );

        transactionTemplate.executeWithoutResult(status -> {
            status.setRollbackOnly();
            OAuthLoginAccount account = transaction.resolveOrCreate(profile);
            long memberId = account.getMemberId();
            long originalWalletId = walletMapper.findByMemberId(memberId).getWalletId();

            assertEquals(
                    originalWalletId,
                    provisioning.provisionForMember(memberId),
                    "복구 UPDATE가 0행을 남겨도 실패로 보면 안 된다"
            );
            assertEquals(
                    1,
                    jdbc.queryForObject(
                            "SELECT COUNT(*) FROM wallets w"
                                    + " JOIN wallet_owners o"
                                    + " ON o.wallet_owner_id = w.wallet_owner_id"
                                    + " WHERE o.member_id = ?",
                            Integer.class,
                            memberId
                    )
            );
        });
    }

    private static OAuthUserProfile newProfile() {
        return new OAuthUserProfile(
                OAuthProvider.LINE,
                "wallet-provisioning-" + UUID.randomUUID(),
                null,
                "Wallet Traveler",
                null
        );
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
