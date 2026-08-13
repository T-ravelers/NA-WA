package me.nawa.journey.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import me.nawa.journey.domain.Journey;
import me.nawa.journey.domain.JourneyExploreItem;
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
    void findAvailableExploreItemById_excludesEndedEventByDate() {
        String marker = "j-event-" + UUID.randomUUID();
        long memberId = insertMember(marker);
        List<Long> eventItemIds = new ArrayList<>();
        LocalDate today = jdbcTemplate.queryForObject(
            "SELECT CURRENT_DATE()",
            LocalDate.class
        );

        try {
            long endedEventId = insertExploreItem(memberId, "EVENT");
            eventItemIds.add(endedEventId);
            insertEvent(
                endedEventId,
                marker + "-ended",
                today.minusDays(2),
                today.minusDays(1),
                "SCHEDULED"
            );

            long activeEventId = insertExploreItem(memberId, "EVENT");
            eventItemIds.add(activeEventId);
            insertEvent(
                activeEventId,
                marker + "-active",
                today.minusDays(1),
                today,
                "ENDED"
            );

            assertNull(mapper.findAvailableExploreItemById(endedEventId));
            JourneyExploreItem active = mapper.findAvailableExploreItemById(
                activeEventId
            );
            assertNotNull(active);
            assertEquals("EVENT", active.getItemType());
        } finally {
            for (Long eventItemId : eventItemIds) {
                jdbcTemplate.update(
                    "DELETE FROM event WHERE event_id = ?",
                    eventItemId
                );
                jdbcTemplate.update(
                    "DELETE FROM explore_items WHERE item_id = ?",
                    eventItemId
                );
            }
            jdbcTemplate.update(
                "DELETE FROM members WHERE member_id = ?",
                memberId
            );
        }
    }

    @Test
    void findJourneysByMemberId_countsItemsUsingTimelineVisibilityRules() {
        String marker = "journey-cnt-" + UUID.randomUUID();
        long memberId = insertMember(marker);
        List<Long> tripIds = new ArrayList<>();
        List<Long> eventItemIds = new ArrayList<>();
        List<Long> placeItemIds = new ArrayList<>();
        LocalDate visitDate = LocalDate.of(2026, 8, 8);

        try {
            long eventOnlyTripId = insertTrip(memberId, marker + "-event-only");
            tripIds.add(eventOnlyTripId);
            long eventItem1 = insertExploreItem(memberId, "EVENT");
            long eventItem2 = insertExploreItem(memberId, "EVENT");
            eventItemIds.add(eventItem1);
            eventItemIds.add(eventItem2);
            insertEvent(eventItem1, marker);
            insertEvent(eventItem2, marker);
            insertTripItem(eventOnlyTripId, eventItem1, visitDate);
            insertTripItem(eventOnlyTripId, eventItem2, visitDate);

            long placeOnlyTripId = insertTrip(memberId, marker + "-place-only");
            tripIds.add(placeOnlyTripId);
            long placeItem1 = insertExploreItem(memberId, "PLACE");
            long placeItem2 = insertExploreItem(memberId, "PLACE");
            placeItemIds.add(placeItem1);
            placeItemIds.add(placeItem2);
            insertPlace(placeItem1, marker);
            insertPlace(placeItem2, marker);
            insertTripItem(placeOnlyTripId, placeItem1, visitDate);
            insertTripItem(placeOnlyTripId, placeItem2, visitDate);

            long mixedTripId = insertTrip(memberId, marker + "-mixed");
            tripIds.add(mixedTripId);
            long mixedEventItem = insertExploreItem(memberId, "EVENT");
            long mixedPlaceItem = insertExploreItem(memberId, "PLACE");
            eventItemIds.add(mixedEventItem);
            placeItemIds.add(mixedPlaceItem);
            insertEvent(mixedEventItem, marker);
            insertPlace(mixedPlaceItem, marker);
            insertTripItem(mixedTripId, mixedEventItem, visitDate);
            insertTripItem(mixedTripId, mixedPlaceItem, visitDate);

            long emptyTripId = insertTrip(memberId, marker + "-empty");
            tripIds.add(emptyTripId);

            long softDeletedItemTripId =
                insertTrip(memberId, marker + "-soft-deleted-item");
            tripIds.add(softDeletedItemTripId);
            long softDeletedEventItem = insertExploreItem(memberId, "EVENT");
            eventItemIds.add(softDeletedEventItem);
            insertEvent(softDeletedEventItem, marker);
            long softDeletedTripItemId = insertTripItem(
                softDeletedItemTripId,
                softDeletedEventItem,
                visitDate
            );
            softDeleteTripItem(softDeletedTripItemId);

            long deletedOriginalTripId =
                insertTrip(memberId, marker + "-deleted-original");
            tripIds.add(deletedOriginalTripId);
            long deletedOriginalEventItem = insertExploreItem(memberId, "EVENT");
            eventItemIds.add(deletedOriginalEventItem);
            insertEvent(deletedOriginalEventItem, marker);
            insertTripItem(
                deletedOriginalTripId,
                deletedOriginalEventItem,
                visitDate
            );
            softDeleteEvent(deletedOriginalEventItem);

            long deletedExploreItemTripId =
                insertTrip(memberId, marker + "-deleted-explore-item");
            tripIds.add(deletedExploreItemTripId);
            long deletedExploreEventItem = insertExploreItem(memberId, "EVENT");
            long deletedExplorePlaceItem = insertExploreItem(memberId, "PLACE");
            eventItemIds.add(deletedExploreEventItem);
            placeItemIds.add(deletedExplorePlaceItem);
            insertEvent(deletedExploreEventItem, marker);
            insertPlace(deletedExplorePlaceItem, marker);
            insertTripItem(
                deletedExploreItemTripId,
                deletedExploreEventItem,
                visitDate
            );
            insertTripItem(
                deletedExploreItemTripId,
                deletedExplorePlaceItem,
                visitDate
            );
            softDeleteExploreItem(deletedExploreEventItem);
            softDeleteExploreItem(deletedExplorePlaceItem);

            long deletedOriginalPlaceTripId =
                insertTrip(memberId, marker + "-deleted-original-place");
            tripIds.add(deletedOriginalPlaceTripId);
            long deletedOriginalPlaceItem = insertExploreItem(memberId, "PLACE");
            placeItemIds.add(deletedOriginalPlaceItem);
            insertPlace(deletedOriginalPlaceItem, marker);
            insertTripItem(
                deletedOriginalPlaceTripId,
                deletedOriginalPlaceItem,
                visitDate
            );
            softDeletePlace(deletedOriginalPlaceItem);

            List<Journey> journeys = mapper.findJourneysByMemberId(memberId);

            assertJourneyCounts(journeys, eventOnlyTripId, 2L, 0L);
            assertJourneyCounts(journeys, placeOnlyTripId, 0L, 2L);
            assertJourneyCounts(journeys, mixedTripId, 1L, 1L);
            assertJourneyCounts(journeys, emptyTripId, 0L, 0L);
            assertJourneyCounts(journeys, softDeletedItemTripId, 0L, 0L);
            assertJourneyCounts(journeys, deletedOriginalTripId, 0L, 0L);
            assertJourneyCounts(journeys, deletedExploreItemTripId, 0L, 0L);
            assertJourneyCounts(journeys, deletedOriginalPlaceTripId, 0L, 0L);
        } finally {
            deleteCountFixture(memberId, tripIds, eventItemIds, placeItemIds);
        }
    }

    private static void assertJourneyCounts(
        List<Journey> journeys,
        long tripId,
        long expectedEventCount,
        long expectedPlaceCount
    ) {
        Journey journey = journeys.stream()
            .filter(candidate -> candidate.getTripId() == tripId)
            .findFirst()
            .orElseThrow();
        assertEquals(expectedEventCount, journey.getEventCount());
        assertEquals(expectedPlaceCount, journey.getPlaceCount());
    }

    private static void deleteCountFixture(
        long memberId,
        List<Long> tripIds,
        List<Long> eventItemIds,
        List<Long> placeItemIds
    ) {
        for (Long tripId : tripIds) {
            jdbcTemplate.update(
                "DELETE FROM trip_items WHERE trip_id = ?",
                tripId
            );
        }
        for (Long tripId : tripIds) {
            jdbcTemplate.update("DELETE FROM trips WHERE trip_id = ?", tripId);
        }
        for (Long itemId : eventItemIds) {
            jdbcTemplate.update(
                "DELETE FROM event WHERE event_id = ?",
                itemId
            );
        }
        for (Long itemId : placeItemIds) {
            jdbcTemplate.update(
                "DELETE FROM place WHERE place_id = ?",
                itemId
            );
        }
        List<Long> allItemIds = new ArrayList<>();
        allItemIds.addAll(eventItemIds);
        allItemIds.addAll(placeItemIds);
        for (Long itemId : allItemIds) {
            jdbcTemplate.update(
                "DELETE FROM explore_items WHERE item_id = ?",
                itemId
            );
        }
        jdbcTemplate.update("DELETE FROM members WHERE member_id = ?", memberId);
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
        return insertExploreItem(memberId, "PLACE");
    }

    private static long insertExploreItem(long memberId, String itemType) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO explore_items "
                    + "(created_by, reviewed_by, item_type, approval_status, "
                    + "visibility_status, reviewed_at) "
                    + "VALUES (?, ?, ?, 'APPROVED', 'VISIBLE', "
                    + "CURRENT_TIMESTAMP)",
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, memberId);
            statement.setLong(2, memberId);
            statement.setString(3, itemType);
            return statement;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    private static void insertEvent(long itemId, String marker) {
        jdbcTemplate.update(
            "INSERT INTO event (event_id, title, start_date, end_date) "
                + "VALUES (?, ?, '2026-08-01', '2026-08-02')",
            itemId,
            marker
        );
    }

    private static void insertEvent(
        long itemId,
        String marker,
        LocalDate startDate,
        LocalDate endDate,
        String status
    ) {
        jdbcTemplate.update(
            "INSERT INTO event "
                + "(event_id, title, start_date, end_date, status) "
                + "VALUES (?, ?, ?, ?, ?)",
            itemId,
            marker,
            startDate,
            endDate,
            status
        );
    }

    private static void insertPlace(long itemId, String marker) {
        jdbcTemplate.update(
            "INSERT INTO place (place_id, name) VALUES (?, ?)",
            itemId,
            marker
        );
    }

    private static long insertTripItem(
        long tripId,
        long itemId,
        LocalDate visitDate
    ) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO trip_items "
                    + "(trip_id, item_id, appointment_id, visit_date, "
                    + "trip_item_status, display_order, note, confirmed_at) "
                    + "VALUES (?, ?, NULL, ?, 'ADDED', 0, NULL, NULL)",
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, tripId);
            statement.setLong(2, itemId);
            statement.setObject(3, visitDate);
            return statement;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    private static void softDeleteTripItem(long tripItemId) {
        jdbcTemplate.update(
            "UPDATE trip_items SET deleted_at = CURRENT_TIMESTAMP "
                + "WHERE trip_item_id = ?",
            tripItemId
        );
    }

    private static void softDeleteEvent(long itemId) {
        jdbcTemplate.update(
            "UPDATE event SET deleted_at = CURRENT_TIMESTAMP WHERE event_id = ?",
            itemId
        );
    }

    private static void softDeleteExploreItem(long itemId) {
        jdbcTemplate.update(
            "UPDATE explore_items SET deleted_at = CURRENT_TIMESTAMP "
                + "WHERE item_id = ?",
            itemId
        );
    }

    private static void softDeletePlace(long itemId) {
        jdbcTemplate.update(
            "UPDATE place SET deleted_at = CURRENT_TIMESTAMP WHERE place_id = ?",
            itemId
        );
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
