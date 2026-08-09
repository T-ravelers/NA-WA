package me.nawa.journey.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import me.nawa.journey.domain.JourneyItem;
import me.nawa.journey.domain.JourneyTimelineItem;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.support.TransactionTemplate;

@EnabledIfEnvironmentVariable(
    named = "RUN_MYSQL_INTEGRATION_TESTS",
    matches = "(?i)true"
)
class JourneyMapperIntegrationTest {

    private static HikariDataSource dataSource;
    private static JourneyMapper mapper;
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
        jdbcTemplate = new JdbcTemplate(dataSource);
        transactionTemplate = new TransactionTemplate(
            new DataSourceTransactionManager(dataSource)
        );

        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setConfigLocation(new ClassPathResource(
            "mybatis-config.xml"
        ));
        factoryBean.setMapperLocations(new ClassPathResource(
            "me/nawa/journey/mapper/JourneyMapper.xml"
        ));

        SqlSessionFactory sqlSessionFactory = factoryBean.getObject();
        if (!sqlSessionFactory.getConfiguration().hasMapper(
            JourneyMapper.class
        )) {
            sqlSessionFactory.getConfiguration().addMapper(
                JourneyMapper.class
            );
        }
        mapper = new SqlSessionTemplate(sqlSessionFactory)
            .getMapper(JourneyMapper.class);
    }

    @AfterAll
    static void closeDatabase() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Test
    void findTimelineItems_executesV7JoinWithStableOrdering() {
        List<JourneyTimelineItem> result =
            mapper.findTimelineItemsByTripId(Long.MAX_VALUE);

        assertNotNull(result);
        assertTrue(result.isEmpty());

        Comparator<JourneyTimelineItem> timelineOrder = Comparator
            .comparing(JourneyTimelineItem::getVisitDate)
            .thenComparing(JourneyTimelineItem::getDisplayOrder)
            .thenComparing(JourneyTimelineItem::getTripItemId);
        assertEquals(
            result.stream().sorted(timelineOrder).toList(),
            result
        );
    }

    @Test
    void insertJourneyItem_duplicateKeyIsTranslatedAndTransactionRollsBack() {
        JourneyItemFixture fixture = createFixture();
        try {
            JourneyItem first = journeyItem(fixture);
            JourneyItem duplicate = journeyItem(fixture);

            assertThrowsDuplicateAndRollsBack(first, duplicate);

            assertEquals(
                0L,
                jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM trip_items WHERE trip_id = ?",
                    Long.class,
                    fixture.tripId()
                )
            );
        } finally {
            deleteFixture(fixture);
        }
    }

    private static void assertThrowsDuplicateAndRollsBack(
        JourneyItem first,
        JourneyItem duplicate
    ) {
        assertThrows(
            DuplicateKeyException.class,
            () -> transactionTemplate.executeWithoutResult(status -> {
                mapper.insertJourneyItem(first);
                mapper.insertJourneyItem(duplicate);
            })
        );
        assertNotNull(first.getTripItemId());
    }

    private static JourneyItemFixture createFixture() {
        String marker = "journey-dup-" + UUID.randomUUID();
        long memberId = insertMember(marker);
        long tripId = insertTrip(memberId, marker);
        long itemId = insertExploreItem(memberId);
        return new JourneyItemFixture(memberId, tripId, itemId);
    }

    private static long insertMember(String displayName) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO members (display_name) VALUES (?)",
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setString(1, displayName);
            return statement;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    private static long insertTrip(long memberId, String marker) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO trips "
                    + "(member_id, title, start_date, end_date) "
                    + "VALUES (?, ?, '2026-08-01', '2026-08-31')",
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, memberId);
            statement.setString(2, marker);
            return statement;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    private static long insertExploreItem(long memberId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO explore_items "
                    + "(created_by, reviewed_by, item_type, approval_status, "
                    + "visibility_status, reviewed_at) "
                    + "VALUES (?, ?, 'PLACE', 'APPROVED', 'VISIBLE', "
                    + "CURRENT_TIMESTAMP)",
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, memberId);
            statement.setLong(2, memberId);
            return statement;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    private static JourneyItem journeyItem(JourneyItemFixture fixture) {
        return JourneyItem.builder()
            .tripId(fixture.tripId())
            .itemId(fixture.itemId())
            .visitDate(java.time.LocalDate.of(2026, 8, 8))
            .displayOrder(0)
            .note("integration")
            .build();
    }

    private static void deleteFixture(JourneyItemFixture fixture) {
        jdbcTemplate.update(
            "DELETE FROM trip_items WHERE trip_id = ?",
            fixture.tripId()
        );
        jdbcTemplate.update(
            "DELETE FROM trips WHERE trip_id = ?",
            fixture.tripId()
        );
        jdbcTemplate.update(
            "DELETE FROM explore_items WHERE item_id = ?",
            fixture.itemId()
        );
        jdbcTemplate.update(
            "DELETE FROM members WHERE member_id = ?",
            fixture.memberId()
        );
    }

    private record JourneyItemFixture(long memberId, long tripId, long itemId) {
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required");
        }
        return value;
    }
}
