package me.nawa.journey.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import me.nawa.config.MySqlSchemaExtension;
import me.nawa.journey.domain.Journey;
import me.nawa.journey.domain.JourneyExploreItem;
import me.nawa.journey.domain.JourneyItem;
import me.nawa.journey.domain.JourneyTimelineItem;
import me.nawa.journey.domain.TripRegion;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MySqlSchemaExtension.class)
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
            mapper.findTimelineItemsByTripId(Long.MAX_VALUE, "en");

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

    /*
     * 항목 운영 기간 검사(JOURNEY-012)가 읽는 값이다. 컬럼이 SELECT에서 빠지면 전부
     * null로 와서 검사가 조용히 통과하므로, 매핑까지 실제로 확인한다.
     */
    @Test
    void findAvailableExploreItemById_returnsEventPeriod() {
        String marker = "j-period-" + UUID.randomUUID();
        long memberId = insertMember(marker);
        List<Long> eventItemIds = new ArrayList<>();
        LocalDate today = jdbcTemplate.queryForObject(
            "SELECT CURRENT_DATE()",
            LocalDate.class
        );

        try {
            long datedEventId = insertExploreItem(memberId, "EVENT");
            eventItemIds.add(datedEventId);
            insertEvent(
                datedEventId,
                marker + "-dated",
                today,
                today.plusDays(3),
                "ONGOING"
            );

            long permanentEventId = insertExploreItem(memberId, "EVENT");
            eventItemIds.add(permanentEventId);
            insertPermanentEvent(
                permanentEventId,
                marker + "-permanent",
                today.minusDays(5)
            );

            JourneyExploreItem dated = mapper.findAvailableExploreItemById(
                datedEventId
            );
            assertNotNull(dated);
            assertEquals(today, dated.getStartDate());
            assertEquals(today.plusDays(3), dated.getEndDate());

            JourneyExploreItem permanent = mapper.findAvailableExploreItemById(
                permanentEventId
            );
            assertNotNull(permanent);
            assertEquals(today.minusDays(5), permanent.getStartDate());
            assertNull(permanent.getEndDate());
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

    /*
     * 커버 사진 선택 규칙을 실제 DB로 확인한다.
     *
     * 순서는 상세 타임라인과 같은 `visit_date -> display_order -> trip_item_id`여야 하고,
     * 썸네일이 없는 항목은 건너뛰어야 한다. 둘 중 하나만 어긋나도 목록의 사진이 상세의
     * 첫 줄과 달라지거나, 뒤에 사진이 있는데도 자리표시가 나온다.
     */
    @Test
    void findJourneysByMemberId_picksCoverFromFirstTimelineItemWithThumbnail() {
        String marker = "journey-cover-" + UUID.randomUUID();
        long memberId = insertMember(marker);
        List<Long> tripIds = new ArrayList<>();
        List<Long> eventItemIds = new ArrayList<>();
        List<Long> placeItemIds = new ArrayList<>();
        LocalDate firstDay = LocalDate.of(2026, 8, 8);
        LocalDate secondDay = LocalDate.of(2026, 8, 9);

        try {
            // 방문일이 앞선 항목이 이긴다. 담은 순서(trip_item_id)가 뒤여도 마찬가지다.
            long byDateTripId = insertTrip(memberId, marker + "-by-date");
            tripIds.add(byDateTripId);
            long laterDayItem = insertExploreItem(memberId, "EVENT");
            long earlierDayItem = insertExploreItem(memberId, "EVENT");
            eventItemIds.add(laterDayItem);
            eventItemIds.add(earlierDayItem);
            insertEventWithThumbnail(laterDayItem, marker, "https://cdn.test/later.jpg");
            insertEventWithThumbnail(earlierDayItem, marker, "https://cdn.test/earlier.jpg");
            insertTripItem(byDateTripId, laterDayItem, secondDay, 0);
            insertTripItem(byDateTripId, earlierDayItem, firstDay, 0);

            // 같은 날이면 display_order가 작은 쪽이 이긴다.
            long byOrderTripId = insertTrip(memberId, marker + "-by-order");
            tripIds.add(byOrderTripId);
            long secondSlotItem = insertExploreItem(memberId, "PLACE");
            long firstSlotItem = insertExploreItem(memberId, "PLACE");
            placeItemIds.add(secondSlotItem);
            placeItemIds.add(firstSlotItem);
            insertPlaceWithThumbnail(secondSlotItem, marker, "https://cdn.test/second.jpg");
            insertPlaceWithThumbnail(firstSlotItem, marker, "https://cdn.test/first.jpg");
            insertTripItem(byOrderTripId, secondSlotItem, firstDay, 1);
            insertTripItem(byOrderTripId, firstSlotItem, firstDay, 0);

            // 맨 앞 항목에 썸네일이 없으면 건너뛰고 다음 것을 쓴다.
            long skipTripId = insertTrip(memberId, marker + "-skip");
            tripIds.add(skipTripId);
            long noThumbnailItem = insertExploreItem(memberId, "EVENT");
            long withThumbnailItem = insertExploreItem(memberId, "PLACE");
            eventItemIds.add(noThumbnailItem);
            placeItemIds.add(withThumbnailItem);
            insertEvent(noThumbnailItem, marker);
            insertPlaceWithThumbnail(withThumbnailItem, marker, "https://cdn.test/third.jpg");
            insertTripItem(skipTripId, noThumbnailItem, firstDay, 0);
            insertTripItem(skipTripId, withThumbnailItem, firstDay, 1);

            /*
             * 방문일과 표시 순서가 모두 같으면 `trip_item_id`가 가른다. `display_order`는
             * 유니크가 아니라 실제로 도달하는 상태이고, ORDER BY의 마지막 갈래가 상세
             * 타임라인과 같아야 한다고 못 박은 지점이라 여기서 함께 잠근다.
             */
            long tieTripId = insertTrip(memberId, marker + "-tie");
            tripIds.add(tieTripId);
            long earlierRowItem = insertExploreItem(memberId, "EVENT");
            long laterRowItem = insertExploreItem(memberId, "EVENT");
            eventItemIds.add(earlierRowItem);
            eventItemIds.add(laterRowItem);
            insertEventWithThumbnail(earlierRowItem, marker, "https://cdn.test/earlier-row.jpg");
            insertEventWithThumbnail(laterRowItem, marker, "https://cdn.test/later-row.jpg");
            insertTripItem(tieTripId, earlierRowItem, firstDay, 0);
            insertTripItem(tieTripId, laterRowItem, firstDay, 0);

            // 모두 썸네일이 없으면 null이다. 빈 문자열이 아니다.
            long noneTripId = insertTrip(memberId, marker + "-none");
            tripIds.add(noneTripId);
            long plainItem = insertExploreItem(memberId, "EVENT");
            eventItemIds.add(plainItem);
            insertEvent(plainItem, marker);
            insertTripItem(noneTripId, plainItem, firstDay, 0);

            // 담긴 항목이 아예 없는 여정도 null이다.
            long emptyTripId = insertTrip(memberId, marker + "-empty");
            tripIds.add(emptyTripId);

            List<Journey> journeys = mapper.findJourneysByMemberId(memberId);

            assertEquals("https://cdn.test/earlier.jpg", coverOf(journeys, byDateTripId));
            assertEquals("https://cdn.test/first.jpg", coverOf(journeys, byOrderTripId));
            assertEquals("https://cdn.test/third.jpg", coverOf(journeys, skipTripId));
            assertEquals("https://cdn.test/earlier-row.jpg", coverOf(journeys, tieTripId));
            assertNull(coverOf(journeys, noneTripId));
            assertNull(coverOf(journeys, emptyTripId));
        } finally {
            deleteCountFixture(memberId, tripIds, eventItemIds, placeItemIds);
        }
    }

    private static String coverOf(List<Journey> journeys, long tripId) {
        return journeys.stream()
            .filter(candidate -> candidate.getTripId() == tripId)
            .findFirst()
            .orElseThrow()
            .getCoverImageUrl();
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

    @Test
    void insertJourneyItem_softDeletedSameItemAndDateCanBeReadded() {
        JourneyItemFixture fixture = createFixture();
        try {
            JourneyItem deleted = journeyItem(fixture);
            mapper.insertJourneyItem(deleted);
            assertTrue(mapper.existsJourneyItem(
                fixture.tripId(),
                fixture.itemId(),
                deleted.getVisitDate()
            ));
            softDeleteTripItem(deleted.getTripItemId());
            assertFalse(mapper.existsJourneyItem(
                fixture.tripId(),
                fixture.itemId(),
                deleted.getVisitDate()
            ));

            JourneyItem readded = journeyItem(fixture);
            mapper.insertJourneyItem(readded);

            assertNotNull(deleted.getTripItemId());
            assertNotNull(readded.getTripItemId());
            assertNotEquals(deleted.getTripItemId(), readded.getTripItemId());
            assertTrue(mapper.existsJourneyItem(
                fixture.tripId(),
                fixture.itemId(),
                readded.getVisitDate()
            ));
            assertEquals(2L, countAllTripItems(fixture.tripId()));
            assertEquals(1L, countActiveTripItems(fixture.tripId()));
        } finally {
            deleteFixture(fixture);
        }
    }

    @Test
    void insertConfirmedJourneyItem_activeAppointmentDuplicateIsBlockedAndDeletedCanBeReadded() {
        JourneyItemFixture fixture = createEventFixture();
        long appointmentId = insertAppointment(fixture.itemId(), fixture.memberId());
        try {
            JourneyItem first = confirmedJourneyItem(
                fixture,
                appointmentId,
                LocalDate.of(2026, 8, 8)
            );
            mapper.insertConfirmedJourneyItem(first);

            JourneyItem activeDuplicate = confirmedJourneyItem(
                fixture,
                appointmentId,
                LocalDate.of(2026, 8, 9)
            );
            assertThrows(
                DuplicateKeyException.class,
                () -> mapper.insertConfirmedJourneyItem(activeDuplicate)
            );

            softDeleteTripItem(first.getTripItemId());
            JourneyItem readded = confirmedJourneyItem(
                fixture,
                appointmentId,
                LocalDate.of(2026, 8, 9)
            );
            mapper.insertConfirmedJourneyItem(readded);

            assertNotNull(readded.getTripItemId());
            assertNotEquals(first.getTripItemId(), readded.getTripItemId());
            assertEquals(2L, countAllTripItems(fixture.tripId()));
            assertEquals(1L, countActiveTripItems(fixture.tripId()));
        } finally {
            deleteTripItems(fixture.tripId());
            jdbcTemplate.update(
                "DELETE FROM appointments WHERE appointment_id = ?",
                appointmentId
            );
            jdbcTemplate.update(
                "DELETE FROM event WHERE event_id = ?",
                fixture.itemId()
            );
            deleteFixtureRows(fixture);
        }
    }

    @Test
    void updateJourney_replacesRegionsAndDetectsDateConflict() {
        JourneyItemFixture fixture = createFixture();
        try {
            insertTripItem(
                fixture.tripId(),
                fixture.itemId(),
                LocalDate.of(2026, 8, 20)
            );

            assertTrue(mapper.hasJourneyItemsOutsideRange(
                fixture.tripId(),
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 10)
            ));

            Journey updated = Journey.builder()
                .tripId(fixture.tripId())
                .title("Updated integration journey")
                .startDate(LocalDate.of(2026, 8, 1))
                .endDate(LocalDate.of(2026, 8, 31))
                .companionPreference("FRIENDS")
                .build();
            assertEquals(1, mapper.updateJourney(updated));

            TripRegion first = TripRegion.builder()
                .tripId(fixture.tripId())
                .regionCode("SEOUL")
                .regionName("Seoul")
                .displayOrder(0)
                .build();
            mapper.insertRegions(List.of(first));
            assertEquals(1, mapper.softDeleteRegionsByTripId(fixture.tripId()));

            TripRegion restored = TripRegion.builder()
                .tripId(fixture.tripId())
                .regionCode("SEOUL")
                .regionName("Seoul City")
                .displayOrder(1)
                .build();
            mapper.insertRegions(List.of(restored));

            Journey reloaded = mapper.findJourneyById(fixture.tripId());
            List<TripRegion> regions = mapper.findRegionsByTripId(
                fixture.tripId()
            );
            assertEquals("Updated integration journey", reloaded.getTitle());
            assertEquals("FRIENDS", reloaded.getCompanionPreference());
            assertEquals(1, regions.size());
            assertEquals("Seoul City", regions.get(0).getRegionName());
            assertEquals(1, regions.get(0).getDisplayOrder());
        } finally {
            jdbcTemplate.update(
                "DELETE FROM trip_regions WHERE trip_id = ?",
                fixture.tripId()
            );
            deleteFixture(fixture);
        }
    }

    @Test
    void findCurrentSpentAmount_sumsOnlyEligibleLiveJourneyExpenses() {
        /*
         * 다른 테스트의 marker(prefix + 전체 UUID, 36자)를 그대로 쓰면 이 테스트에서만
         * 폭이 좁은 두 컬럼에 동시에 걸린다. marker 자체가 members.display_name
         * VARCHAR(50)에 들어가고, 가장 긴 접미사 "-settlement"(11자)를 붙인 값은
         * wallet_transfers.transfer_number VARCHAR(50)에 들어간다. "journey-spending-"
         * (17자) + UUID(36자) = 53자로 접미사 없이도 이미 display_name을 넘겼고,
         * MySQL 통합 테스트에서 SQL 검증 전에 MysqlDataTruncation으로 끊겼다(리뷰).
         * 접두사를 짧게 줄이는 대신 UUID를 앞 8자로 잘라 marker 전체를 줄인다 —
         * finally에서 즉시 정리되는 범위라 8자로도 실제로 부족한 적이 없다.
         */
        String marker = "spend-" + UUID.randomUUID().toString().substring(0, 8);
        long memberId = insertMember(marker);
        long tripId = insertTrip(memberId, marker);
        WalletFixture wallet = insertMemberWallet(memberId);
        List<Long> transferIds = new ArrayList<>();

        try {
            transferIds.add(insertWalletTransferDebit(
                wallet.walletId(), memberId, marker + "-qr", "QR_PAYMENT",
                "COMPLETED", LocalDateTime.of(2026, 8, 5, 12, 0),
                new BigDecimal("100000.0000")
            ));
            transferIds.add(insertWalletTransferDebit(
                wallet.walletId(), memberId, marker + "-settlement", "SETTLEMENT",
                "COMPLETED", LocalDateTime.of(2026, 8, 10, 15, 0),
                new BigDecimal("284500.0000")
            ));
            transferIds.add(insertWalletTransferDebit(
                wallet.walletId(), memberId, marker + "-topup", "TOPUP",
                "COMPLETED", LocalDateTime.of(2026, 8, 8, 9, 0),
                new BigDecimal("50000.0000")
            ));
            transferIds.add(insertWalletTransferDebit(
                wallet.walletId(), memberId, marker + "-failed", "QR_PAYMENT",
                "FAILED", null, new BigDecimal("70000.0000")
            ));
            transferIds.add(insertWalletTransferDebit(
                wallet.walletId(), memberId, marker + "-outside", "QR_PAYMENT",
                "COMPLETED", LocalDateTime.of(2026, 9, 1, 10, 0),
                new BigDecimal("90000.0000")
            ));

            assertEquals(
                new BigDecimal("384500.0000"),
                mapper.findCurrentSpentAmount(tripId, memberId)
            );
        } finally {
            jdbcTemplate.update(
                "DELETE FROM wallet_ledger_entries WHERE wallet_id = ?",
                wallet.walletId()
            );
            for (Long transferId : transferIds) {
                jdbcTemplate.update(
                    "DELETE FROM wallet_transfers WHERE transfer_id = ?",
                    transferId
                );
            }
            jdbcTemplate.update(
                "DELETE FROM wallets WHERE wallet_id = ?",
                wallet.walletId()
            );
            jdbcTemplate.update(
                "DELETE FROM wallet_owners WHERE wallet_owner_id = ?",
                wallet.walletOwnerId()
            );
            jdbcTemplate.update("DELETE FROM trips WHERE trip_id = ?", tripId);
            jdbcTemplate.update("DELETE FROM members WHERE member_id = ?", memberId);
        }
    }

    @Test
    void journeyRowLock_serializesSettingsUpdateAndItemAddition()
        throws Exception {
        JourneyItemFixture fixture = createFixture();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch settingsLockAcquired = new CountDownLatch(1);
        CountDownLatch releaseSettingsUpdate = new CountDownLatch(1);
        CountDownLatch itemAdditionStarted = new CountDownLatch(1);

        try {
            Future<Void> settingsUpdate = executor.submit(() -> {
                transactionTemplate.executeWithoutResult(status -> {
                    Journey locked = mapper.findJourneyByIdForUpdate(
                        fixture.tripId()
                    );
                    settingsLockAcquired.countDown();
                    await(releaseSettingsUpdate);
                    locked.setTitle("Locked settings update");
                    assertEquals(1, mapper.updateJourney(locked));
                });
                return null;
            });

            assertTrue(settingsLockAcquired.await(5, TimeUnit.SECONDS));

            Future<String> itemAddition = executor.submit(() ->
                transactionTemplate.execute(status -> {
                    itemAdditionStarted.countDown();
                    Journey locked = mapper.findJourneyByIdForUpdate(
                        fixture.tripId()
                    );
                    mapper.insertJourneyItem(journeyItem(fixture));
                    return locked.getTitle();
                })
            );

            assertTrue(itemAdditionStarted.await(5, TimeUnit.SECONDS));
            assertThrows(
                TimeoutException.class,
                () -> itemAddition.get(300, TimeUnit.MILLISECONDS)
            );

            releaseSettingsUpdate.countDown();
            settingsUpdate.get(5, TimeUnit.SECONDS);
            assertEquals(
                "Locked settings update",
                itemAddition.get(5, TimeUnit.SECONDS)
            );
            assertEquals(
                1L,
                jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM trip_items WHERE trip_id = ? "
                        + "AND deleted_at IS NULL",
                    Long.class,
                    fixture.tripId()
                )
            );
        } finally {
            releaseSettingsUpdate.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
            deleteFixture(fixture);
        }
    }

    @Test
    void updateJourney_middleFailureRollsBackSettingsAndRegions() {
        JourneyItemFixture fixture = createFixture();
        Journey before = mapper.findJourneyById(fixture.tripId());
        TripRegion originalRegion = TripRegion.builder()
            .tripId(fixture.tripId())
            .regionCode("SEOUL")
            .regionName("Seoul")
            .displayOrder(0)
            .build();
        mapper.insertRegions(List.of(originalRegion));

        try {
            assertThrows(
                DataIntegrityViolationException.class,
                () -> transactionTemplate.executeWithoutResult(status -> {
                    Journey locked = mapper.findJourneyByIdForUpdate(
                        fixture.tripId()
                    );
                    locked.setTitle("Must roll back");
                    assertEquals(1, mapper.updateJourney(locked));
                    assertEquals(
                        1,
                        mapper.softDeleteRegionsByTripId(fixture.tripId())
                    );
                    jdbcTemplate.update(
                        "INSERT INTO trip_regions "
                            + "(trip_id, region_code, region_name, "
                            + "display_order) VALUES (?, NULL, ?, ?)",
                        fixture.tripId(),
                        "Invalid region",
                        1
                    );
                })
            );

            Journey reloaded = mapper.findJourneyById(fixture.tripId());
            List<TripRegion> regions = mapper.findRegionsByTripId(
                fixture.tripId()
            );
            assertEquals(before.getTitle(), reloaded.getTitle());
            assertEquals(1, regions.size());
            assertEquals("SEOUL", regions.get(0).getRegionCode());
            assertEquals("Seoul", regions.get(0).getRegionName());
        } finally {
            jdbcTemplate.update(
                "DELETE FROM trip_regions WHERE trip_id = ?",
                fixture.tripId()
            );
            deleteFixture(fixture);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for test latch");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                "Interrupted while waiting for test latch",
                exception
            );
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

    private static JourneyItemFixture createEventFixture() {
        String marker = "journey-appt-" + UUID.randomUUID();
        long memberId = insertMember(marker);
        long tripId = insertTrip(memberId, marker);
        long itemId = insertExploreItem(memberId, "EVENT");
        insertEvent(itemId, marker);
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

    private static WalletFixture insertMemberWallet(long memberId) {
        KeyHolder ownerKey = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO wallet_owners (member_id, owner_type) VALUES (?, 'MEMBER')",
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, memberId);
            return statement;
        }, ownerKey);

        long walletOwnerId = ownerKey.getKey().longValue();
        KeyHolder walletKey = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO wallets "
                    + "(wallet_owner_id, currency_code, available_balance) "
                    + "VALUES (?, 'KRW', 1000000)",
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, walletOwnerId);
            return statement;
        }, walletKey);

        return new WalletFixture(
            walletOwnerId,
            walletKey.getKey().longValue()
        );
    }

    private static long insertWalletTransferDebit(
        long walletId,
        long memberId,
        String transferNumber,
        String transferType,
        String transferStatus,
        LocalDateTime completedAt,
        BigDecimal amount
    ) {
        KeyHolder transferKey = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO wallet_transfers "
                    + "(currency_code, initiator_member_id, transfer_number, "
                    + "transfer_type, transfer_status, amount, completed_at) "
                    + "VALUES ('KRW', ?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, memberId);
            statement.setString(2, transferNumber);
            statement.setString(3, transferType);
            statement.setString(4, transferStatus);
            statement.setBigDecimal(5, amount);
            statement.setObject(6, completedAt);
            return statement;
        }, transferKey);

        long transferId = transferKey.getKey().longValue();
        jdbcTemplate.update(
            "INSERT INTO wallet_ledger_entries "
                + "(transfer_id, wallet_id, entry_type, amount, balance_after) "
                + "VALUES (?, ?, 'DEBIT', ?, 0)",
            transferId,
            walletId,
            amount
        );
        return transferId;
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

    /*
     * 상시 이벤트. chk_event_period가 `is_permanent = TRUE ⟺ end_date IS NULL`을
     * 강제하므로 end_date를 채우면 INSERT 자체가 거부된다.
     */
    private static void insertPermanentEvent(
        long itemId,
        String marker,
        LocalDate startDate
    ) {
        jdbcTemplate.update(
            "INSERT INTO event "
                + "(event_id, title, start_date, end_date, is_permanent) "
                + "VALUES (?, ?, ?, NULL, TRUE)",
            itemId,
            marker,
            startDate
        );
    }

    private static void insertPlace(long itemId, String marker) {
        jdbcTemplate.update(
            "INSERT INTO place (place_id, name) VALUES (?, ?)",
            itemId,
            marker
        );
    }

    private static void insertEventWithThumbnail(
        long itemId,
        String marker,
        String thumbnailUrl
    ) {
        jdbcTemplate.update(
            "INSERT INTO event "
                + "(event_id, title, start_date, end_date, thumbnail_url) "
                + "VALUES (?, ?, '2026-08-01', '2026-08-02', ?)",
            itemId,
            marker,
            thumbnailUrl
        );
    }

    private static void insertPlaceWithThumbnail(
        long itemId,
        String marker,
        String thumbnailUrl
    ) {
        jdbcTemplate.update(
            "INSERT INTO place (place_id, name, thumbnail_url) VALUES (?, ?, ?)",
            itemId,
            marker,
            thumbnailUrl
        );
    }

    /** 커버 선택은 방문일과 표시 순서에 달려 있어, 그 둘을 지정할 수 있어야 한다. */
    private static long insertTripItem(
        long tripId,
        long itemId,
        LocalDate visitDate,
        int displayOrder
    ) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO trip_items "
                    + "(trip_id, item_id, appointment_id, visit_date, "
                    + "trip_item_status, display_order, note, confirmed_at) "
                    + "VALUES (?, ?, NULL, ?, 'ADDED', ?, NULL, NULL)",
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, tripId);
            statement.setLong(2, itemId);
            statement.setObject(3, visitDate);
            statement.setInt(4, displayOrder);
            return statement;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    private static long insertTripItem(
        long tripId,
        long itemId,
        LocalDate visitDate
    ) {
        return insertTripItem(tripId, itemId, visitDate, 0);
    }

    private static void softDeleteTripItem(long tripItemId) {
        jdbcTemplate.update(
            "UPDATE trip_items SET deleted_at = CURRENT_TIMESTAMP "
                + "WHERE trip_item_id = ?",
            tripItemId
        );
    }

    private static long insertAppointment(long itemId, long hostMemberId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        LocalDateTime activityStartAt = LocalDateTime.of(2026, 8, 8, 10, 0);
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO appointments "
                    + "(item_id, host_member_id, language_code, appointment_name, "
                    + "max_members, deposit_amount, "
                    + "appointment_status, activity_start_at, activity_end_at) "
                    + "VALUES (?, ?, 'en', 'journey re-add integration', "
                    + "5, 10000, 'RECRUITING', ?, ?)",
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, itemId);
            statement.setLong(2, hostMemberId);
            statement.setObject(3, activityStartAt);
            statement.setObject(4, activityStartAt.plusHours(2));
            return statement;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    private static long countAllTripItems(long tripId) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM trip_items WHERE trip_id = ?",
            Long.class,
            tripId
        );
    }

    private static long countActiveTripItems(long tripId) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM trip_items "
                + "WHERE trip_id = ? AND deleted_at IS NULL",
            Long.class,
            tripId
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

    private static JourneyItem confirmedJourneyItem(
        JourneyItemFixture fixture,
        long appointmentId,
        LocalDate visitDate
    ) {
        return JourneyItem.builder()
            .tripId(fixture.tripId())
            .itemId(fixture.itemId())
            .appointmentId(appointmentId)
            .visitDate(visitDate)
            .displayOrder(0)
            .note("appointment integration")
            .build();
    }

    private static void deleteFixture(JourneyItemFixture fixture) {
        deleteTripItems(fixture.tripId());
        deleteFixtureRows(fixture);
    }

    private static void deleteTripItems(long tripId) {
        jdbcTemplate.update(
            "DELETE FROM trip_items WHERE trip_id = ?",
            tripId
        );
    }

    private static void deleteFixtureRows(JourneyItemFixture fixture) {
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

    private record WalletFixture(long walletOwnerId, long walletId) {
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required");
        }
        return value;
    }
}
