package me.nawa.notification.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import me.nawa.config.MySqlSchemaExtension;
import me.nawa.notification.domain.PushSubscription;
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
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;

@ExtendWith(MySqlSchemaExtension.class)
@EnabledIfEnvironmentVariable(
    named = "RUN_MYSQL_INTEGRATION_TESTS",
    matches = "(?i)true"
)
class PushSubscriptionMapperIntegrationTest {

    private static HikariDataSource dataSource;
    private static JdbcTemplate jdbcTemplate;
    private static PushSubscriptionMapper mapper;

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
        jdbcTemplate = new JdbcTemplate(dataSource);

        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setConfigLocation(new ClassPathResource("mybatis-config.xml"));
        factoryBean.setMapperLocations(
            new ClassPathResource("me/nawa/notification/mapper/PushSubscriptionMapper.xml")
        );
        SqlSessionFactory sqlSessionFactory = factoryBean.getObject();
        if (!sqlSessionFactory.getConfiguration().hasMapper(PushSubscriptionMapper.class)) {
            sqlSessionFactory.getConfiguration().addMapper(PushSubscriptionMapper.class);
        }
        mapper = new SqlSessionTemplate(sqlSessionFactory).getMapper(PushSubscriptionMapper.class);
    }

    @AfterAll
    static void closeDatabase() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    /** 한 회원이 폰과 노트북을 모두 등록하면 행이 둘 생긴다. */
    @Test
    void upsert_keepsOneRowPerDeviceForTheSameMember() {
        long memberId = insertMember();
        String phone = endpoint();
        String laptop = endpoint();
        try {
            mapper.upsert(memberId, phone, "p256dh-a", "auth-a", "en");
            mapper.upsert(memberId, laptop, "p256dh-b", "auth-b", "ja");
            // 같은 기기를 다시 등록해도 행이 늘지 않는다.
            mapper.upsert(memberId, phone, "p256dh-a", "auth-a", "en");

            assertEquals(2, countByMember(memberId));
        } finally {
            deleteSubscriptions(phone, laptop);
            deleteMember(memberId);
        }
    }

    /**
     * 같은 기기에 다른 계정이 로그인하면 구독이 새로 생기지 않고 주인만 옮겨 간다.
     * 옮겨 가지 않으면 이전 계정의 알림이 이 기기에 계속 도착한다.
     */
    @Test
    void upsert_movesTheSubscriptionToTheMemberWhoLoggedInOnTheSameDevice() {
        long first = insertMember();
        long second = insertMember();
        String device = endpoint();
        try {
            mapper.upsert(first, device, "old-key", "old-auth", "en");
            mapper.upsert(second, device, "new-key", "new-auth", "vi");

            assertEquals(0, countByMember(first));
            assertEquals(1, countByMember(second));
            Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT p256dh, auth_secret, language_code FROM push_subscriptions WHERE endpoint = ?",
                device
            );
            assertEquals("new-key", row.get("p256dh"));
            assertEquals("new-auth", row.get("auth_secret"));
            assertEquals("vi", row.get("language_code"));
        } finally {
            deleteSubscriptions(device);
            deleteMember(first);
            deleteMember(second);
        }
    }

    /** 해지는 내 기기만 지운다. 남의 endpoint를 적어 보내도 아무 행도 바뀌지 않는다. */
    @Test
    void deleteByMemberAndEndpoint_onlyRemovesTheOwnersSubscription() {
        long owner = insertMember();
        long stranger = insertMember();
        String device = endpoint();
        try {
            mapper.upsert(owner, device, "key", "auth", "en");

            assertEquals(0, mapper.deleteByMemberAndEndpoint(stranger, device));
            assertEquals(1, countByMember(owner));

            assertEquals(1, mapper.deleteByMemberAndEndpoint(owner, device));
            assertEquals(0, countByMember(owner));
        } finally {
            deleteSubscriptions(device);
            deleteMember(owner);
            deleteMember(stranger);
        }
    }

    /** 발송이 읽는 값이다. 회원의 모든 기기가 저장한 키·언어 그대로 나오고, 남의 기기는 섞이지 않는다. */
    @Test
    void findByMemberId_returnsEveryDeviceOfTheMemberWithTheStoredKeysAndLanguage() {
        long member = insertMember();
        long other = insertMember();
        String phone = endpoint();
        String laptop = endpoint();
        String othersDevice = endpoint();
        try {
            mapper.upsert(member, phone, "p256dh-a", "auth-a", "ja");
            mapper.upsert(member, laptop, "p256dh-b", "auth-b", "vi");
            mapper.upsert(other, othersDevice, "p256dh-c", "auth-c", "en");

            List<PushSubscription> subscriptions = mapper.findByMemberId(member);

            assertEquals(2, subscriptions.size());
            PushSubscription first = subscriptions.get(0);
            assertNotNull(first.getPushSubscriptionId());
            assertEquals(member, first.getMemberId());
            assertEquals(phone, first.getEndpoint());
            assertEquals("p256dh-a", first.getP256dh());
            assertEquals("auth-a", first.getAuthSecret());
            assertEquals("ja", first.getLanguageCode());
            assertEquals(laptop, subscriptions.get(1).getEndpoint());
        } finally {
            deleteSubscriptions(phone, laptop, othersDevice);
            deleteMember(member);
            deleteMember(other);
        }
    }

    /** 푸시 서비스가 끝났다고 알린 구독은 주인이 누구든 지운다. 이미 없으면 0행이다. */
    @Test
    void deleteByEndpoint_removesOnlyThatSubscriptionRegardlessOfOwner() {
        long member = insertMember();
        String gone = endpoint();
        String kept = endpoint();
        try {
            mapper.upsert(member, gone, "key", "auth", "en");
            mapper.upsert(member, kept, "key", "auth", "en");

            assertEquals(1, mapper.deleteByEndpoint(gone));
            assertEquals(0, mapper.deleteByEndpoint(gone));
            assertEquals(1, countByMember(member));
        } finally {
            deleteSubscriptions(gone, kept);
            deleteMember(member);
        }
    }

    /** 상한 검사가 쓰는 개수다. 이 기기와 다른 회원의 기기는 세지 않는다. */
    @Test
    void countByMemberExcludingEndpoint_countsOnlyTheOtherDevicesOfTheMember() {
        long memberId = insertMember();
        long otherMemberId = insertMember();
        String phone = endpoint();
        String laptop = endpoint();
        String otherMembersTablet = endpoint();
        String unregistered = endpoint();
        try {
            mapper.upsert(memberId, phone, "p256dh-a", "auth-a", "en");
            mapper.upsert(memberId, laptop, "p256dh-b", "auth-b", "en");
            mapper.upsert(otherMemberId, otherMembersTablet, "p256dh-c", "auth-c", "en");

            // 이미 등록한 기기를 다시 등록하는 경우: 나머지 한 대만 센다.
            assertEquals(1, mapper.countByMemberExcludingEndpoint(memberId, phone));
            // 새 기기를 등록하는 경우: 가진 기기 둘을 모두 세고, 다른 회원의 기기는 세지 않는다.
            assertEquals(2, mapper.countByMemberExcludingEndpoint(memberId, unregistered));
            assertEquals(1, mapper.countByMemberExcludingEndpoint(otherMemberId, unregistered));
        } finally {
            deleteSubscriptions(phone, laptop, otherMembersTablet);
            deleteMember(memberId);
            deleteMember(otherMemberId);
        }
    }

    private static String endpoint() {
        return "https://push.example.test/send/" + UUID.randomUUID();
    }

    private static long insertMember() {
        return new SimpleJdbcInsert(dataSource)
            .withTableName("members")
            .usingColumns("display_name")
            .usingGeneratedKeyColumns("member_id")
            .executeAndReturnKey(Map.of("display_name", "psh-" + UUID.randomUUID()))
            .longValue();
    }

    private static int countByMember(long memberId) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM push_subscriptions WHERE member_id = ?", Integer.class, memberId
        );
    }

    private static void deleteSubscriptions(String... endpoints) {
        for (String endpoint : endpoints) {
            jdbcTemplate.update("DELETE FROM push_subscriptions WHERE endpoint = ?", endpoint);
        }
    }

    private static void deleteMember(long memberId) {
        jdbcTemplate.update("DELETE FROM members WHERE member_id = ?", memberId);
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required");
        }
        return value;
    }
}
