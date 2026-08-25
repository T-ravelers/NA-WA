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
import me.nawa.config.MySqlSchemaExtension;
import me.nawa.explore.domain.EventStatus;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

@ExtendWith(MySqlSchemaExtension.class)
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
    // 기준일은 애플리케이션이 정한다. DB 세션 시간대가 무엇이든 결과가 같아야 한다.
    private LocalDate today;

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
        today = LocalDate.now();
    }

    @AfterEach
    void cleanUpFixture() {
        for (Long eventId : eventIds) {
            jdbcTemplate.update(
                "DELETE FROM explore_item_likes WHERE item_id = ?",
                eventId
            );
            // event를 참조하는 FK라 본체보다 먼저 지운다.
            jdbcTemplate.update(
                "DELETE FROM event_translations WHERE event_id = ?",
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

    /**
     * 요청한 언어의 번역이 있으면 그 값이, 없으면 한국어 원문이 나가야 한다.
     *
     * <p>#531 전까지 조회 SQL이 번역 테이블을 아예 조인하지 않아 어떤 언어로 요청해도
     * 한국어만 나갔다. 목록과 상세가 같은 언어를 돌려주는지도 함께 본다 — 두 곳이 따로
     * 정규화하던 것이 원인의 절반이었다.
     */
    @Test
    void searchAndDetail_returnTranslatedTextForTheRequestedLanguage() {
        long eventId = insertEvent(
            "translated",
            today.minusDays(1),
            today.plusDays(1),
            "ONGOING"
        );
        insertEventTranslation(
            eventId, "en", marker + "-english-title", "English description"
        );

        EventSearchRequest request = new EventSearchRequest();
        request.setKeyword(marker + "-english-title");
        request.setLanguage("en");
        request.setSize(20);

        List<EventSummaryResponse> results = mapper.searchEvents(
            request, 0, null, today
        );

        assertEquals(1, results.size());
        assertEquals(marker + "-english-title", results.get(0).getTitle());
        // 목록이 번역 제목으로 찾아 줬다면 개수도 같은 조건을 봐야 한다.
        assertEquals(1, mapper.countEvents(request, null, today));

        EventDetailResponse detail = mapper.findEventDetail(
            eventId, "en", null, today
        );
        assertEquals(marker + "-english-title", detail.getTitle());
        assertEquals("English description", detail.getDescription());
    }

    /**
     * {@code zh-TW} 번역이 조인된다.
     *
     * <p><b>대소문자는 지금 스키마에서는 문제가 되지 않는다.</b> 두 번역 테이블이
     * {@code utf8mb4_0900_ai_ci}(대소문자 구분 없음)라 {@code zh-tw}로 비교해도 저장된
     * {@code zh-TW}에 그대로 걸린다. 그래서 "어떤 언어로 요청해도 한국어만 나온다"의 원인은
     * 소문자 정규화가 아니라 <b>조인 자체가 없던 것</b>이었다(#531).
     *
     * <p>그럼에도 {@code SupportedLanguagePolicy}가 {@code zh-TW}를 원형으로 되돌리는 것은
     * 대비다 — 컬럼 collation을 {@code _bin}이나 {@code _as_cs}로 바꾸거나, 언어 코드를
     * 자바에서 비교하는 코드가 생기는 순간 소문자 값은 조용히 어긋난다. 이 테스트는 두 표기가
     * 지금은 같은 결과를 낸다는 사실 자체를 고정해, 나중에 collation이 바뀌면 여기서 깨지게
     * 한다.
     */
    @Test
    void findEventDetail_matchesZhTwTranslation_regardlessOfCasing() {
        long eventId = insertEvent(
            "zh-tw",
            today.minusDays(1),
            today.plusDays(1),
            "ONGOING"
        );
        insertEventTranslation(eventId, "zh-TW", marker + "-繁體標題", "繁體說明");

        assertEquals(
            marker + "-繁體標題",
            mapper.findEventDetail(eventId, "zh-TW", null, today).getTitle()
        );
        assertEquals(
            marker + "-繁體標題",
            mapper.findEventDetail(eventId, "zh-tw", null, today).getTitle()
        );
    }

    /** 번역 행이 없거나 값이 빈 문자열이면 한국어 원문으로 돌아간다. */
    @Test
    void findEventDetail_fallsBackToKorean_whenTranslationIsMissingOrBlank() {
        long noTranslation = insertEvent(
            "no-translation",
            today.minusDays(1),
            today.plusDays(1),
            "ONGOING"
        );
        long blankTranslation = insertEvent(
            "blank-translation",
            today.minusDays(1),
            today.plusDays(1),
            "ONGOING"
        );
        // 파이프라인이 번역하지 못한 필드를 NULL이 아니라 빈 값으로 채우는 경우가 있다.
        insertEventTranslation(blankTranslation, "en", "   ", "");

        assertEquals(
            marker + "-no-translation",
            mapper.findEventDetail(noTranslation, "en", null, today).getTitle()
        );
        assertEquals(
            marker + "-blank-translation",
            mapper.findEventDetail(blankTranslation, "en", null, today).getTitle()
        );
    }

    /**
     * 요청 언어 번역이 없어도 영어 번역이 있으면 한국어를 건너뛰고 영어로 간다.
     *
     * <p>Journey 타임라인(#536)과 맞춘 규칙이다 — 요청 언어 번역이 없고 영어 번역만 있는
     * Event를 Explore에서는 한국어로, Journey에 담은 뒤에는 영어로 보는 어긋남을 없앤다.
     * 영어 번역조차 없으면 그제서야 한국어 원문으로 돌아간다.
     */
    @Test
    void findEventDetail_fallsBackThroughEnglishBeforeKorean() {
        long englishOnly = insertEvent(
            "english-only",
            today.minusDays(1),
            today.plusDays(1),
            "ONGOING"
        );
        long noTranslationAtAll = insertEvent(
            "no-translation-at-all",
            today.minusDays(1),
            today.plusDays(1),
            "ONGOING"
        );
        insertEventTranslation(
            englishOnly, "en", marker + "-english-only-title", "English description"
        );

        assertEquals(
            marker + "-english-only-title",
            mapper.findEventDetail(englishOnly, "ja", null, today).getTitle()
        );
        assertEquals(
            "English description",
            mapper.findEventDetail(englishOnly, "vi", null, today).getDescription()
        );
        assertEquals(
            marker + "-no-translation-at-all",
            mapper.findEventDetail(noTranslationAtAll, "ja", null, today).getTitle()
        );

        // 목록·검색도 같은 규칙을 봐야 한다.
        EventSearchRequest request = new EventSearchRequest();
        request.setKeyword(marker + "-english-only-title");
        request.setLanguage("ja");
        request.setSize(20);

        List<EventSummaryResponse> results = mapper.searchEvents(request, 0, null, today);

        assertEquals(1, results.size());
        assertEquals(marker + "-english-only-title", results.get(0).getTitle());
        assertEquals(1, mapper.countEvents(request, null, today));
    }

    /**
     * 번역 쪽 {@code operating_hours}는 TEXT이고 응답 DTO는 JSON이다.
     *
     * <p>그대로 COALESCE하면 {@code JsonNodeTypeHandler}가 파싱에 실패해 상세 API가 통째로
     * 500이 된다. {@code {"raw": "..."}}로 감싸 원문 JSON과 같은 모양으로 맞춘다.
     */
    @Test
    void findEventDetail_wrapsTranslatedOperatingHoursAsJson() {
        long eventId = insertEvent(
            "hours",
            today.minusDays(1),
            today.plusDays(1),
            "ONGOING"
        );
        insertEventTranslation(
            eventId, "en", marker + "-hours-en", null, "Mon-Fri 09:00-18:00"
        );

        EventDetailResponse detail = mapper.findEventDetail(
            eventId, "en", null, today
        );

        assertNotNull(detail.getOperatingHours());
        assertTrue(detail.getOperatingHours().isObject());
        assertEquals(
            "Mon-Fri 09:00-18:00",
            detail.getOperatingHours().path("raw").asText()
        );
    }

    @Test
    void findEventDetail_mapsV7ColumnsAndIgnoresStoredEndedStatus() {
        long eventId = insertEvent(
            "mapping",
            today.minusDays(1),
            today.plusDays(1),
            "ENDED"
        );

        EventDetailResponse result = mapper.findEventDetail(
            eventId, "en", null, today);

        assertNotNull(result);
        assertNotNull(result.getEventKind());
        // 적재 값은 'ENDED'지만 운영 기간이 오늘을 덮으므로 진행 중으로 나간다.
        assertEquals(EventStatus.ONGOING, result.getStatus());
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
    void searchAndDetail_useCurrentDatesForVisibilityAndDateRanges() {
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

        Set<Long> visibleIds = searchIds(null, null);
        assertFalse(visibleIds.contains(endedYesterday));
        assertTrue(visibleIds.contains(endsToday));
        assertTrue(visibleIds.contains(startsTomorrow));
        assertTrue(visibleIds.contains(startedWithScheduledStatus));
        assertTrue(visibleIds.contains(permanentWithEndedStatus));
        assertEquals(4L, countEvents(null, null));

        // 날짜 프리셋은 프론트가 날짜 범위로 환원해 보낸다(#275). "오늘 진행 중"은
        // 오늘 하루짜리 기간(시작=종료)으로 온다.
        Set<Long> overlappingToday = searchIds(today, today);
        assertEquals(Set.of(
            endsToday,
            startedWithScheduledStatus,
            permanentWithEndedStatus
        ), overlappingToday);

        // "곧 시작"은 내일부터 상한 없는 기간이다 — 내일 이후에도 진행 중인
        // 이벤트를 모두 포함하는 겹침 기준이다.
        Set<Long> visibleFromTomorrow = searchIds(today.plusDays(1), null);
        assertEquals(Set.of(
            startsTomorrow,
            startedWithScheduledStatus,
            permanentWithEndedStatus
        ), visibleFromTomorrow);

        assertNull(mapper.findEventDetail(endedYesterday, "en", null, today));
        assertNotNull(mapper.findEventDetail(endsToday, "en", null, today));
        assertNotNull(mapper.findEventDetail(
            permanentWithEndedStatus,
            "en",
            null,
            today
        ));
    }

    /**
     * 화면에 나가는 status는 적재 값이 아니라 기준일이 정한다.
     *
     * <p>적재 값을 일부러 반대로 넣어 둔 픽스처들이다. 예전에는 이 값이 그대로
     * 나가서, 시작일이 지난 Event가 계속 'SCHEDULED'로 보였다.
     */
    @Test
    void searchAndDetail_deriveStatusFromDatesNotStoredColumn() {
        long startsTomorrow = insertEvent(
            "derive-starts-tomorrow",
            today.plusDays(1),
            today.plusDays(2),
            "ONGOING"
        );
        long startedToday = insertEvent(
            "derive-started-today",
            today,
            today.plusDays(1),
            "SCHEDULED"
        );
        long endsToday = insertEvent(
            "derive-ends-today",
            today.minusDays(1),
            today,
            "SCHEDULED"
        );
        long permanent = insertPermanentEvent(
            "derive-permanent",
            today.minusDays(10),
            "ENDED"
        );

        // 카드가 종료일 미상 Event를 "하루짜리 지난 행사"로 그리지 않으려면 목록도 이
        // 값을 알아야 한다. 상세만 내려주던 시절에는 카드와 상세가 다른 말을 했다.
        Map<Long, Boolean> permanentById = new HashMap<>();
        for (EventSummaryResponse result
            : mapper.searchEvents(request(null, null), 0, null, today)) {
            permanentById.put(result.getItemId(), result.getIsPermanent());
        }
        assertEquals(Boolean.TRUE, permanentById.get(permanent));
        assertEquals(Boolean.FALSE, permanentById.get(endsToday));

        Map<Long, EventStatus> statusById = searchStatuses(today);
        assertEquals(EventStatus.SCHEDULED, statusById.get(startsTomorrow));
        assertEquals(EventStatus.ONGOING, statusById.get(startedToday));
        // 종료일 당일은 아직 진행 중이다. 노출 조건(end_date >= today)과 같은 경계다.
        assertEquals(EventStatus.ONGOING, statusById.get(endsToday));
        // 종료일이 없으면 종료를 판정할 근거가 없어 ENDED로 가지 않는다.
        assertEquals(EventStatus.ONGOING, statusById.get(permanent));

        assertEquals(
            EventStatus.SCHEDULED,
            mapper.findEventDetail(startsTomorrow, "en", null, today)
                .getStatus()
        );
        assertEquals(
            EventStatus.ONGOING,
            mapper.findEventDetail(permanent, "en", null, today).getStatus()
        );
    }

    /**
     * 기준일은 DB의 CURRENT_DATE()가 아니라 넘겨받은 값이다.
     *
     * <p>같은 행이 넘긴 날짜에 따라 다른 status로 나와야, 자정 근처에서 DB 세션
     * 시간대가 응답을 흔들지 못한다.
     */
    @Test
    void derivedStatus_followsSuppliedDateNotDatabaseDate() {
        long startsTomorrow = insertEvent(
            "supplied-date",
            today.plusDays(1),
            today.plusDays(2),
            "ENDED"
        );

        assertEquals(
            EventStatus.SCHEDULED,
            searchStatuses(today).get(startsTomorrow)
        );
        assertEquals(
            EventStatus.ONGOING,
            searchStatuses(today.plusDays(1)).get(startsTomorrow)
        );
    }

    /**
     * 저장 status도 같은 규칙으로 되돌린다.
     *
     * <p>화면은 파생값을 쓰므로 이 구문을 기다리지 않는다. 여기서 맞추는 것은 저장값을
     * 그대로 읽는 쪽이다. 전진만 하지 않고 양방향으로 고친다 — 적재가 아직 시작하지
     * 않은 Event에 'ENDED'를 실어 보내면 전진만으로는 영영 틀린 채 남는다.
     *
     * <p>이 구문은 event 전체를 대상으로 한다. 단정은 이 테스트가 만든 행으로 좁힌다.
     */
    @Test
    void realignEventStatuses_movesStoredColumnToDerivedValue() {
        long startedButScheduled = insertEvent(
            "realign-started",
            today.minusDays(1),
            today.plusDays(1),
            "SCHEDULED"
        );
        long notStartedButEnded = insertEvent(
            "realign-not-started",
            today.plusDays(1),
            today.plusDays(2),
            "ENDED"
        );
        long endedButOngoing = insertEvent(
            "realign-ended",
            today.minusDays(3),
            today.minusDays(1),
            "ONGOING"
        );
        long permanentButEnded = insertPermanentEvent(
            "realign-permanent",
            today.minusDays(10),
            "ENDED"
        );
        long alreadyCorrect = insertEvent(
            "realign-correct",
            today.minusDays(1),
            today.plusDays(1),
            "ONGOING"
        );

        assertTrue(mapper.realignEventStatuses(today) >= 4);

        assertEquals("ONGOING", storedStatus(startedButScheduled));
        assertEquals("SCHEDULED", storedStatus(notStartedButEnded));
        assertEquals("ENDED", storedStatus(endedButOngoing));
        assertEquals("ONGOING", storedStatus(permanentButEnded));
        assertEquals("ONGOING", storedStatus(alreadyCorrect));

        // 이미 맞는 행만 남으면 옮길 것이 없다.
        assertEquals(0, mapper.realignEventStatuses(today));
    }

    @Test
    void savedColumn_marksOnlyRequestingMembersActiveLikes() {
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
            : mapper.searchEvents(request(null, null), 0, memberId, today)) {
            savedById.put(result.getItemId(), result.isSaved());
        }
        assertEquals(Boolean.TRUE, savedById.get(likedId));
        assertEquals(Boolean.FALSE, savedById.get(unlikedId));
        assertEquals(Boolean.FALSE, savedById.get(canceledId));

        for (EventSummaryResponse result
            : mapper.searchEvents(request(null, null), 0, null, today)) {
            assertFalse(result.isSaved());
        }

        assertTrue(mapper.findEventDetail(likedId, "en", memberId, today)
            .isSaved());
        assertFalse(mapper.findEventDetail(likedId, "en", null, today)
            .isSaved());
    }

    private String storedStatus(long eventId) {
        return jdbcTemplate.queryForObject(
            "SELECT status FROM event WHERE event_id = ?",
            String.class,
            eventId
        );
    }

    private Set<Long> searchIds(LocalDate startDate, LocalDate endDate) {
        EventSearchRequest request = request(startDate, endDate);
        List<EventSummaryResponse> results = mapper.searchEvents(
            request,
            0,
            null,
            today
        );
        Set<Long> ids = new HashSet<>();
        for (EventSummaryResponse result : results) {
            ids.add(result.getItemId());
        }
        return ids;
    }

    private Map<Long, EventStatus> searchStatuses(LocalDate referenceDate) {
        Map<Long, EventStatus> statusById = new HashMap<>();
        for (EventSummaryResponse result
            : mapper.searchEvents(request(null, null), 0, null, referenceDate)) {
            statusById.put(result.getItemId(), result.getStatus());
        }
        return statusById;
    }

    private long countEvents(LocalDate startDate, LocalDate endDate) {
        return mapper.countEvents(request(startDate, endDate), null, today);
    }

    private EventSearchRequest request(LocalDate startDate, LocalDate endDate) {
        EventSearchRequest request = new EventSearchRequest();
        request.setKeyword(marker);
        request.setStartDate(startDate);
        request.setEndDate(endDate);
        request.setSize(20);
        return request;
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

    private void insertEventTranslation(
        long eventId,
        String languageCode,
        String title,
        String description
    ) {
        insertEventTranslation(eventId, languageCode, title, description, null);
    }

    private void insertEventTranslation(
        long eventId,
        String languageCode,
        String title,
        String description,
        String operatingHours
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO event_translations (
                event_id, language_code, title, description, operating_hours
            ) VALUES (?, ?, ?, ?, ?)
            """,
            eventId,
            languageCode,
            title,
            description,
            operatingHours
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
