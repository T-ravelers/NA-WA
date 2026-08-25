package me.nawa.explore.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.util.List;
import java.util.UUID;
import me.nawa.config.MySqlSchemaExtension;
import me.nawa.explore.dto.request.PlaceSearchRequest;
import me.nawa.explore.dto.response.PlaceDetailResponse;
import me.nawa.explore.dto.response.PlaceSummaryResponse;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
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

@ExtendWith(MySqlSchemaExtension.class)
@EnabledIfEnvironmentVariable(
    named = "RUN_MYSQL_INTEGRATION_TESTS",
    matches = "(?i)true"
)
class PlaceMapperIntegrationTest {

    private static HikariDataSource dataSource;
    private static PlaceMapper mapper;
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

        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setConfigLocation(new ClassPathResource(
            "mybatis-config.xml"
        ));
        factoryBean.setMapperLocations(new ClassPathResource(
            "me/nawa/explore/mapper/PlaceMapper.xml"
        ));

        SqlSessionFactory sqlSessionFactory = factoryBean.getObject();
        // XML의 namespace가 이미 인터페이스를 등록하므로 그대로 addMapper를 부르면
        // MapperRegistry가 중복 등록으로 BindingException을 던진다.
        if (!sqlSessionFactory.getConfiguration().hasMapper(
            PlaceMapper.class
        )) {
            sqlSessionFactory.getConfiguration().addMapper(PlaceMapper.class);
        }
        mapper = new SqlSessionTemplate(sqlSessionFactory)
            .getMapper(PlaceMapper.class);
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
    void findPlaceDetail_mapsJsonValues() {
        List<Long> placeIds = jdbcTemplate.query(
            """
            SELECT p.place_id
            FROM explore_items ei
            JOIN place p ON p.place_id = ei.item_id
            WHERE ei.item_type = 'PLACE'
              AND ei.approval_status = 'APPROVED'
              AND ei.visibility_status = 'VISIBLE'
              AND ei.deleted_at IS NULL
              AND p.deleted_at IS NULL
              AND p.is_active = TRUE
              AND p.image_urls IS NOT NULL
              AND p.opening_hours IS NOT NULL
              AND p.closed_days IS NOT NULL
            LIMIT 1
            """,
            (resultSet, rowNumber) -> resultSet.getLong("place_id")
        );

        Assumptions.assumeTrue(
            !placeIds.isEmpty(),
            "A public active Place with JSON values is required"
        );

        PlaceDetailResponse result = mapper.findPlaceDetail(
            placeIds.get(0),
            "en",
            null
        );

        assertNotNull(result);
        assertNotNull(result.getPlaceKind());
        assertNotNull(result.getImageUrls());
        assertTrue(result.getImageUrls().isArray());
        assertNotNull(result.getOpeningHours());
        assertTrue(result.getOpeningHours().isObject());
        assertNotNull(result.getClosedDays());
        assertTrue(result.getClosedDays().isArray());
    }

    /** rollback-only 트랜잭션에서 fixture를 만들어 데이터가 남지 않는다. */
    @Test
    void savedColumn_marksOnlyRequestingMembersLikes() {
        transactionTemplate.executeWithoutResult(status -> {
            status.setRollbackOnly();
            String name = "찜 플래그 테스트 " + UUID.randomUUID();

            jdbcTemplate.update(
                "INSERT INTO members (display_name) VALUES ('찜 플래그 테스트 회원')"
            );
            Long memberId = jdbcTemplate.queryForObject(
                "SELECT LAST_INSERT_ID()", Long.class
            );
            jdbcTemplate.update(
                "INSERT INTO explore_items "
                    + "(item_type, approval_status, visibility_status, "
                    + "reviewed_by, reviewed_at) "
                    + "VALUES ('PLACE', 'APPROVED', 'VISIBLE', ?, "
                    + "CURRENT_TIMESTAMP)",
                memberId
            );
            Long itemId = jdbcTemplate.queryForObject(
                "SELECT LAST_INSERT_ID()", Long.class
            );
            jdbcTemplate.update(
                "INSERT INTO place (place_id, name) VALUES (?, ?)",
                itemId, name
            );
            jdbcTemplate.update(
                "INSERT INTO explore_item_likes (item_id, member_id) "
                    + "VALUES (?, ?)",
                itemId, memberId
            );

            PlaceSearchRequest request = new PlaceSearchRequest();
            request.setKeyword(name);

            List<PlaceSummaryResponse> memberResults = mapper.searchPlaces(
                request, 0, 20, memberId
            );
            assertEquals(1, memberResults.size());
            assertTrue(memberResults.get(0).isSaved());

            List<PlaceSummaryResponse> anonymousResults = mapper.searchPlaces(
                request, 0, 20, null
            );
            assertEquals(1, anonymousResults.size());
            assertFalse(anonymousResults.get(0).isSaved());

            assertTrue(mapper.findPlaceDetail(itemId, "en", memberId).isSaved());
            assertFalse(mapper.findPlaceDetail(itemId, "en", null).isSaved());
        });
    }

    /**
     * Place도 Event와 같은 결함이 있었다 — 조회 SQL이 번역 테이블을 보지 않았고,
     * {@code findPlaceDetail}은 언어 파라미터조차 받지 않았다.
     *
     * <p>요청 언어 → 영어 → 한국어 원문 순으로 세 단을 확인한다. 영어 중간 폴백은
     * Journey 타임라인(#536)과 맞춘 것이다 — 요청 언어 번역이 없고 영어 번역만 있는
     * Place를 Explore에서는 한국어로, Journey에 담은 뒤에는 영어로 보는 어긋남을 없앤다.
     *
     * <p>rollback-only 트랜잭션 안에서 fixture를 만들어 데이터가 남지 않는다.
     */
    @Test
    void searchAndDetail_fallsBackThroughEnglishToKorean() {
        transactionTemplate.executeWithoutResult(status -> {
            status.setRollbackOnly();
            String marker = UUID.randomUUID().toString();

            long translated = insertPlace("한국어 이름 " + marker);
            long untranslated = insertPlace("번역 없음 " + marker);
            long noVietnamese = insertPlace("한국어만 " + marker);
            insertPlaceTranslation(translated, "en", "English Name " + marker);
            // 파이프라인이 번역하지 못한 필드를 빈 값으로 채우는 경우가 있다.
            insertPlaceTranslation(untranslated, "en", "   ");
            insertPlaceTranslation(noVietnamese, "en", "English Only " + marker);

            assertEquals(
                "English Name " + marker,
                mapper.findPlaceDetail(translated, "en", null).getName()
            );
            assertEquals(
                "번역 없음 " + marker,
                mapper.findPlaceDetail(untranslated, "en", null).getName()
            );

            // 요청 언어(vi) 번역이 없어도 영어 번역이 있으면 한국어를 건너뛰고 영어로 간다.
            assertEquals(
                "English Only " + marker,
                mapper.findPlaceDetail(noVietnamese, "vi", null).getName()
            );

            // 영어 번역조차 없으면 그제서야 한국어 원문으로 돌아간다.
            long noTranslationAtAll = insertPlace("번역 전혀 없음 " + marker);
            assertEquals(
                "번역 전혀 없음 " + marker,
                mapper.findPlaceDetail(noTranslationAtAll, "vi", null).getName()
            );

            PlaceSearchRequest request = new PlaceSearchRequest();
            request.setKeyword("English Name " + marker);
            request.setLanguage("en");

            List<PlaceSummaryResponse> results = mapper.searchPlaces(
                request, 0, 20, null
            );
            assertEquals(1, results.size());
            assertEquals("English Name " + marker, results.get(0).getName());
            // 목록이 번역 이름으로 찾아 줬다면 개수도 같은 조건을 봐야 한다.
            assertEquals(1, mapper.countPlaces(request, null));

            // 요청 언어 번역이 없어 영어 이름이 보이는 Place도 그 이름으로 찾을 수 있다.
            PlaceSearchRequest englishFallbackSearch = new PlaceSearchRequest();
            englishFallbackSearch.setKeyword("English Only " + marker);
            englishFallbackSearch.setLanguage("vi");

            List<PlaceSummaryResponse> fallbackResults = mapper.searchPlaces(
                englishFallbackSearch, 0, 20, null
            );
            assertEquals(1, fallbackResults.size());
            assertEquals(1, mapper.countPlaces(englishFallbackSearch, null));
        });
    }

    /**
     * {@code zh-TW} 번역이 조인된다.
     *
     * <p>EventMapper 쪽과 같다 — 컬럼 collation이 {@code utf8mb4_0900_ai_ci}라 지금은 두 표기가
     * 같은 결과를 낸다. collation이 대소문자를 가리게 바뀌면 이 테스트가 먼저 깨진다.
     */
    @Test
    void findPlaceDetail_matchesZhTwTranslation_regardlessOfCasing() {
        transactionTemplate.executeWithoutResult(status -> {
            status.setRollbackOnly();
            String marker = UUID.randomUUID().toString();

            long placeId = insertPlace("한국어 이름 " + marker);
            insertPlaceTranslation(placeId, "zh-TW", "繁體名稱 " + marker);

            assertEquals(
                "繁體名稱 " + marker,
                mapper.findPlaceDetail(placeId, "zh-TW", null).getName()
            );
            assertEquals(
                "繁體名稱 " + marker,
                mapper.findPlaceDetail(placeId, "zh-tw", null).getName()
            );
        });
    }

    /**
     * 번역된 영업시간·휴무일이 원문과 <b>같은 JSON 모양</b>으로 나가야 한다.
     *
     * <p>번역이 붙은 항목과 붙지 않은 항목의 응답 형태가 갈리면 클라이언트가 같은 필드를
     * 두 가지로 다뤄야 한다. 한국어 폴백일 때는 원문 모양이라 정상으로 보이고, 번역 데이터가
     * 붙는 순간에만 어긋나는 종류의 회귀다(#531 리뷰).
     */
    @Test
    void findPlaceDetail_keepsTranslatedHoursAndClosedDaysInTheirOriginalJsonShapes() {
        transactionTemplate.executeWithoutResult(status -> {
            status.setRollbackOnly();
            String marker = UUID.randomUUID().toString();

            long placeId = insertPlace("한국어 이름 " + marker);
            jdbcTemplate.update(
                "UPDATE place SET opening_hours = JSON_OBJECT('raw', '12:00 ~ 22:00'), "
                    + "closed_days = JSON_ARRAY('매주 월요일') WHERE place_id = ?",
                placeId
            );
            jdbcTemplate.update(
                "INSERT INTO place_translations "
                    + "(place_id, language_code, opening_hours_text, closed_days_text) "
                    + "VALUES (?, 'en', ?, ?)",
                placeId, "Mon-Fri 09:00-18:00", "Every Monday"
            );

            PlaceDetailResponse detail = mapper.findPlaceDetail(placeId, "en", null);

            // 영업시간 원문은 OBJECT다. 번역도 객체여야 프론트가 raw를 벗긴다.
            assertTrue(detail.getOpeningHours().isObject());
            assertEquals(
                "Mon-Fri 09:00-18:00",
                detail.getOpeningHours().path("raw").asText()
            );

            // 휴무일 원문은 ARRAY다. 객체로 나가면 번역 여부에 따라 응답 형태가 갈린다.
            assertTrue(
                detail.getClosedDays().isArray(),
                "휴무일 번역이 배열이 아니면 원문과 응답 형태가 갈린다"
            );
            assertEquals("Every Monday", detail.getClosedDays().get(0).asText());

            // 요청 언어(vi) 번역은 없지만 영어 번역이 있으므로 한국어를 건너뛰고 영어로 간다.
            PlaceDetailResponse englishFallback = mapper.findPlaceDetail(placeId, "vi", null);
            assertTrue(englishFallback.getClosedDays().isArray());
            assertEquals("Every Monday", englishFallback.getClosedDays().get(0).asText());

            // 영어 번역조차 없는 Place는 원문 모양 그대로 한국어로 돌아간다.
            long noTranslation = insertPlace("한국어 이름 없음 " + marker);
            jdbcTemplate.update(
                "UPDATE place SET closed_days = JSON_ARRAY('매주 월요일') WHERE place_id = ?",
                noTranslation
            );
            PlaceDetailResponse korean = mapper.findPlaceDetail(noTranslation, "vi", null);
            assertTrue(korean.getClosedDays().isArray());
            assertEquals("매주 월요일", korean.getClosedDays().get(0).asText());
        });
    }

    /**
     * {@code chk_explore_items_review}가 승인 상태에 검수자와 검수 시각을 함께 요구하므로
     * 회원을 먼저 만들어 {@code reviewed_by}에 넣는다.
     */
    private long insertPlace(String name) {
        jdbcTemplate.update(
            "INSERT INTO members (display_name) VALUES ('번역 폴백 테스트 회원')"
        );
        Long reviewerId = jdbcTemplate.queryForObject(
            "SELECT LAST_INSERT_ID()", Long.class
        );
        jdbcTemplate.update(
            "INSERT INTO explore_items "
                + "(item_type, approval_status, visibility_status, "
                + "reviewed_by, reviewed_at) "
                + "VALUES ('PLACE', 'APPROVED', 'VISIBLE', ?, CURRENT_TIMESTAMP)",
            reviewerId
        );
        Long placeId = jdbcTemplate.queryForObject(
            "SELECT LAST_INSERT_ID()", Long.class
        );
        jdbcTemplate.update(
            "INSERT INTO place (place_id, name) VALUES (?, ?)", placeId, name
        );
        return placeId;
    }

    private void insertPlaceTranslation(
        long placeId,
        String languageCode,
        String name
    ) {
        jdbcTemplate.update(
            "INSERT INTO place_translations (place_id, language_code, name) "
                + "VALUES (?, ?, ?)",
            placeId, languageCode, name
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
