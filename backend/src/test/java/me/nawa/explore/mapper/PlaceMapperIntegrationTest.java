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
     * <p>rollback-only 트랜잭션 안에서 fixture를 만들어 데이터가 남지 않는다.
     */
    @Test
    void searchAndDetail_returnTranslatedTextAndFallBackToKorean() {
        transactionTemplate.executeWithoutResult(status -> {
            status.setRollbackOnly();
            String marker = UUID.randomUUID().toString();

            long translated = insertPlace("한국어 이름 " + marker);
            long untranslated = insertPlace("번역 없음 " + marker);
            insertPlaceTranslation(translated, "en", "English Name " + marker);
            // 파이프라인이 번역하지 못한 필드를 빈 값으로 채우는 경우가 있다.
            insertPlaceTranslation(untranslated, "en", "   ");

            assertEquals(
                "English Name " + marker,
                mapper.findPlaceDetail(translated, "en", null).getName()
            );
            assertEquals(
                "번역 없음 " + marker,
                mapper.findPlaceDetail(untranslated, "en", null).getName()
            );

            // 번역이 없는 언어를 요청하면 한국어 원문으로 돌아간다.
            assertEquals(
                "한국어 이름 " + marker,
                mapper.findPlaceDetail(translated, "vi", null).getName()
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
