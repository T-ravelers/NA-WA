package me.nawa.explore.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import me.nawa.explore.dto.request.EventSearchRequest;
import me.nawa.explore.dto.response.EventDetailResponse;
import me.nawa.explore.dto.response.EventSummaryResponse;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

@EnabledIfEnvironmentVariable(
    named = "RUN_MYSQL_INTEGRATION_TESTS",
    matches = "(?i)true"
)
class EventMapperIntegrationTest {

    private static HikariDataSource dataSource;
    private static EventMapper mapper;
    private static JdbcTemplate jdbcTemplate;

    private final List<Long> eventIds = new ArrayList<>();
    private long memberId;
    private String marker;

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

        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setConfigLocation(new ClassPathResource(
            "mybatis-config.xml"
        ));
        factoryBean.setMapperLocations(new ClassPathResource(
            "me/nawa/explore/mapper/EventMapper.xml"
        ));

        SqlSessionFactory sqlSessionFactory = factoryBean.getObject();
        if (!sqlSessionFactory.getConfiguration().hasMapper(
            EventMapper.class
        )) {
            sqlSessionFactory.getConfiguration().addMapper(EventMapper.class);
        }
        mapper = new SqlSessionTemplate(sqlSessionFactory)
            .getMapper(EventMapper.class);
    }

    @BeforeEach
    void setUpFixtureOwner() {
        marker = "event-date-" + UUID.randomUUID();
        memberId = insertMember(marker);
    }

    @AfterEach
    void cleanUpFixture() {
        for (Long eventId : eventIds) {
            jdbcTemplate.update(
                "DELETE FROM explore_item_likes WHERE item_id = ?",
                eventId
            );
            jdbcTemplate.update(
                "DELETE FROM event WHERE event_id = ?",
                eventId
            );
            jdbcTemplate.update(
                "DELETE FROM explore_items WHERE item_id = ?",
                eventId
            );
        }
        jdbcTemplate.update(
            "DELETE FROM members WHERE member_id = ?",
            memberId
        );
        eventIds.clear();
    }

    @AfterAll
    static void closeDatabase() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Test
    void findEventDetail_mapsV7ColumnsAndIgnoresStoredEndedStatus() {
        LocalDate today = currentDatabaseDate();
        long eventId = insertEvent(
            "mapping",
            today.minusDays(1),
            today.plusDays(1),
            "ENDED"
        );

        EventDetailResponse result = mapper.findEventDetail(eventId, "en", null);

        assertNotNull(result);
        assertNotNull(result.getEventKind());
        assertNotNull(result.getStatus());
        assertNotNull(result.getImageUrls());
        assertTrue(result.getImageUrls().isArray());
        assertNotNull(result.getLinks());
        assertTrue(result.getLinks().isObject());
        assertNotNull(result.getPreReservation());
        assertTrue(result.getPreReservation().isObject());
        assertNotNull(result.getOperatingHours());
        assertNotNull(result.getOpenDays());
        assertTrue(result.getOpenDays().isArray());
    }

    @Test
    void searchAndDetail_useCurrentDatesForVisibilityAndPresets() {
        LocalDate today = currentDatabaseDate();
        long endedYesterday = insertEvent(
            "ended-yesterday",
            today.minusDays(2),
            today.minusDays(1),
            "SCHEDULED"
        );
        long endsToday = insertEvent(
            "ends-today",
            today.minusDays(1),
            today,
            "SCHEDULED"
        );
        long startsTomorrow = insertEvent(
            "starts-tomorrow",
            today.plusDays(1),
            today.plusDays(2),
            "ONGOING"
        );
        long startedWithScheduledStatus = insertEvent(
            "started-scheduled",
            today.minusDays(1),
            today.plusDays(1),
            "SCHEDULED"
        );
        long permanentWithEndedStatus = insertPermanentEvent(
            "permanent-ended",
            today.minusDays(10),
            "ENDED"
        );

        Set<Long> visibleIds = searchIds(null);
        assertFalse(visibleIds.contains(endedYesterday));
        assertTrue(visibleIds.contains(endsToday));
        assertTrue(visibleIds.contains(startsTomorrow));
        assertTrue(visibleIds.contains(startedWithScheduledStatus));
        assertTrue(visibleIds.contains(permanentWithEndedStatus));
        assertEquals(4L, countEvents(null));

        Set<Long> ongoingIds = searchIds("ONGOING");
        assertEquals(Set.of(
            endsToday,
            startedWithScheduledStatus,
            permanentWithEndedStatus
        ), ongoingIds);

        Set<Long> openingSoonIds = searchIds("OPENING_SOON");
        assertEquals(Set.of(startsTomorrow), openingSoonIds);

        assertNull(mapper.findEventDetail(endedYesterday, "en", null));
        assertNotNull(mapper.findEventDetail(endsToday, "en", null));
        assertNotNull(mapper.findEventDetail(
            permanentWithEndedStatus,
            "en",
            null
        ));
    }

    @Test
    void savedColumn_marksOnlyRequestingMembersActiveLikes() {
        LocalDate today = currentDatabaseDate();
        long likedId = insertEvent(
            "saved-liked",
            today.minusDays(1),
            today.plusDays(1),
            "ONGOING"
        );
        long unlikedId = insertEvent(
            "saved-unliked",
            today.minusDays(1),
            today.plusDays(1),
            "ONGOING"
        );
        long canceledId = insertEvent(
            "saved-canceled",
            today.minusDays(1),
            today.plusDays(1),
            "ONGOING"
        );
        jdbcTemplate.update(
            "INSERT INTO explore_item_likes (item_id, member_id) "
                + "VALUES (?, ?)",
            likedId,
            memberId
        );
        jdbcTemplate.update(
            "INSERT INTO explore_item_likes (item_id, member_id, deleted_at) "
                + "VALUES (?, ?, CURRENT_TIMESTAMP)",
            canceledId,
            memberId
        );

        Map<Long, Boolean> savedById = new HashMap<>();
        for (EventSummaryResponse result
            : mapper.searchEvents(request(null), 0, memberId)) {
            savedById.put(result.getItemId(), result.isSaved());
        }
        assertEquals(Boolean.TRUE, savedById.get(likedId));
        assertEquals(Boolean.FALSE, savedById.get(unlikedId));
        assertEquals(Boolean.FALSE, savedById.get(canceledId));

        for (EventSummaryResponse result
            : mapper.searchEvents(request(null), 0, null)) {
            assertFalse(result.isSaved());
        }

        assertTrue(mapper.findEventDetail(likedId, "en", memberId).isSaved());
        assertFalse(mapper.findEventDetail(likedId, "en", null).isSaved());
    }

    private Set<Long> searchIds(String datePreset) {
        EventSearchRequest request = request(datePreset);
        List<EventSummaryResponse> results = mapper.searchEvents(
            request,
            0,
            null
        );
        Set<Long> ids = new HashSet<>();
        for (EventSummaryResponse result : results) {
            ids.add(result.getItemId());
        }
        return ids;
    }

    private long countEvents(String datePreset) {
        return mapper.countEvents(request(datePreset), null);
    }

    private EventSearchRequest request(String datePreset) {
        EventSearchRequest request = new EventSearchRequest();
        request.setKeyword(marker);
        request.setDatePreset(datePreset);
        request.setSize(20);
        return request;
    }

    private LocalDate currentDatabaseDate() {
        return jdbcTemplate.queryForObject(
            "SELECT CURRENT_DATE()",
            LocalDate.class
        );
    }

    private long insertMember(String displayName) {
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

    private long insertEvent(
        String suffix,
        LocalDate startDate,
        LocalDate endDate,
        String status
    ) {
        return insertEvent(suffix, startDate, endDate, status, false);
    }

    private long insertPermanentEvent(
        String suffix,
        LocalDate startDate,
        String status
    ) {
        return insertEvent(suffix, startDate, null, status, true);
    }

    private long insertEvent(
        String suffix,
        LocalDate startDate,
        LocalDate endDate,
        String status,
        boolean permanent
    ) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO explore_items "
                    + "(created_by, reviewed_by, item_type, approval_status, "
                    + "visibility_status, reviewed_at) "
                    + "VALUES (?, ?, 'EVENT', 'APPROVED', 'VISIBLE', "
                    + "CURRENT_TIMESTAMP)",
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, memberId);
            statement.setLong(2, memberId);
            return statement;
        }, keyHolder);
        long eventId = keyHolder.getKey().longValue();
        eventIds.add(eventId);

        jdbcTemplate.update(
            """
            INSERT INTO event (
                event_id,
                title,
                start_date,
                end_date,
                status,
                is_permanent,
                image_urls,
                links,
                pre_reservation,
                operating_hours,
                open_days
            ) VALUES (
                ?, ?, ?, ?, ?, ?,
                JSON_ARRAY('image'),
                JSON_OBJECT('homepage', 'https://example.com'),
                JSON_OBJECT('has', false),
                JSON_OBJECT('monday', '09:00-18:00'),
                JSON_ARRAY('MONDAY')
            )
            """,
            eventId,
            marker + "-" + suffix,
            startDate,
            endDate,
            status,
            permanent
        );
        return eventId;
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
