package me.nawa.explore.mapper;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import me.nawa.config.MySqlSchemaExtension;
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
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 찜 등록·취소의 상태 전이와 favorite_count 증감을 실제 MySQL에서 검증한다.
 *
 * 모든 테스트는 rollback-only 트랜잭션 안에서 돌므로 데이터가 남지 않는다.
 * 회원 fixture는 FK(explore_item_likes → members)를 만족시키기 위해 직접 넣는다.
 */
@ExtendWith(MySqlSchemaExtension.class)
@EnabledIfEnvironmentVariable(
        named = "RUN_MYSQL_INTEGRATION_TESTS",
        matches = "(?i)true"
)
class ExploreItemLikeMapperIntegrationTest {
    private static HikariDataSource dataSource;
    private static ExploreItemLikeMapper likeMapper;
    private static JdbcTemplate jdbcTemplate;
    private static TransactionTemplate transactionTemplate;

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

        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setConfigLocation(new ClassPathResource("mybatis-config.xml"));
        SqlSessionFactory sqlSessionFactory = factoryBean.getObject();
        sqlSessionFactory.getConfiguration().addMapper(ExploreItemLikeMapper.class);
        SqlSessionTemplate sqlSessionTemplate = new SqlSessionTemplate(sqlSessionFactory);
        likeMapper = sqlSessionTemplate.getMapper(ExploreItemLikeMapper.class);
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
    void likeLifecycle_transitionsOnce_andMovesEventFavoriteCount() {
        transactionTemplate.executeWithoutResult(status -> {
            status.setRollbackOnly();
            long memberId = insertMember();
            long itemId = insertEventItem(memberId, "VISIBLE");

            assertEquals("EVENT", likeMapper.findVisibleItemType(itemId));

            // 첫 찜: 되살릴 행이 없으므로 INSERT가 전이를 만든다.
            assertEquals(0, likeMapper.reviveLike(itemId, memberId));
            assertEquals(1, likeMapper.insertLike(itemId, memberId));
            jdbcTemplate.update(
                    "UPDATE event SET favorite_count = favorite_count + 1 "
                            + "WHERE event_id = ?", itemId);

            // 중복 찜: 어느 쪽도 전이를 만들지 않는다.
            assertEquals(0, likeMapper.reviveLike(itemId, memberId));
            assertEquals(0, likeMapper.insertLike(itemId, memberId));

            // 취소: 전이 1회. 재취소는 0.
            assertEquals(1, likeMapper.softDeleteLike(itemId, memberId));
            assertEquals(0, likeMapper.softDeleteLike(itemId, memberId));

            // 재찜: soft-delete된 행이 되살아난다. INSERT IGNORE는 개입하지 않는다.
            assertEquals(1, likeMapper.reviveLike(itemId, memberId));

            // 카운트 증감 구문과 GREATEST 하한을 확인한다.
            assertEquals(1, likeMapper.adjustEventFavoriteCount(itemId, 1));
            assertEquals(2L, favoriteCount("event", "event_id", itemId));
            likeMapper.adjustEventFavoriteCount(itemId, -1);
            likeMapper.adjustEventFavoriteCount(itemId, -1);
            likeMapper.adjustEventFavoriteCount(itemId, -1);
            assertEquals(0L, favoriteCount("event", "event_id", itemId));
        });
    }

    @Test
    void findVisibleItemType_returnsNull_whenHidden_butFindItemTypeStillFindsIt() {
        transactionTemplate.executeWithoutResult(status -> {
            status.setRollbackOnly();
            long reviewerId = insertMember();
            long itemId = insertEventItem(reviewerId, "HIDDEN");

            assertNull(likeMapper.findVisibleItemType(itemId));
            assertEquals("EVENT", likeMapper.findItemType(itemId));
        });
    }

    @Test
    void adjustPlaceFavoriteCount_movesPlaceRow() {
        transactionTemplate.executeWithoutResult(status -> {
            status.setRollbackOnly();
            long reviewerId = insertMember();
            long itemId = insertPlaceItem(reviewerId);

            assertEquals("PLACE", likeMapper.findVisibleItemType(itemId));
            likeMapper.adjustPlaceFavoriteCount(itemId, 1);
            assertEquals(1L, favoriteCount("place", "place_id", itemId));
        });
    }

    /**
     * 등록 게이트는 explore_items 행만 본다 — 목록·상세가 거르는 세 조건은 확인하지 않는다.
     * EXPLORE_API.md의 찜 등록 불릿이 확정한 계약이라(#244) 여기서 고정한다. 이 셋 중
     * 하나라도 findVisibleItemType에 추가되면 그 문서가 다시 거짓이 되고, 이미 찜한
     * 항목의 재등록도 404로 깨진다.
     */
    @Test
    void findVisibleItemType_returnsType_evenWhenListAndDetailWouldExclude() {
        transactionTemplate.executeWithoutResult(status -> {
            status.setRollbackOnly();
            long reviewerId = insertMember();

            // 어제 끝난 Event — 목록·상세는 end_date로 거른다.
            long endedEventId = insertEndedEventItem(reviewerId);
            assertEquals("EVENT", likeMapper.findVisibleItemType(endedEventId));

            // 비활성 Place — 목록·상세는 is_active로 거른다.
            long inactivePlaceId = insertInactivePlaceItem(reviewerId);
            assertEquals("PLACE", likeMapper.findVisibleItemType(inactivePlaceId));

            // event 행만 soft-delete — 목록·상세는 e.deleted_at으로 거른다.
            long deletedEventId = insertEventItem(reviewerId, "VISIBLE");
            jdbcTemplate.update(
                    "UPDATE event SET deleted_at = CURRENT_TIMESTAMP "
                            + "WHERE event_id = ?", deletedEventId);
            assertEquals("EVENT", likeMapper.findVisibleItemType(deletedEventId));
        });
    }

    private long insertMember() {
        jdbcTemplate.update(
                "INSERT INTO members (display_name) VALUES ('찜 통합 테스트 회원')");
        return lastInsertId();
    }

    /** chk_explore_items_review — APPROVED는 검수자·검수 시각이 필수라 함께 넣는다. */
    private long insertEventItem(long reviewerId, String visibilityStatus) {
        jdbcTemplate.update(
                "INSERT INTO explore_items "
                        + "(item_type, approval_status, visibility_status, "
                        + "reviewed_by, reviewed_at) "
                        + "VALUES ('EVENT', 'APPROVED', ?, ?, CURRENT_TIMESTAMP)",
                visibilityStatus, reviewerId);
        long itemId = lastInsertId();
        // chk_event_period — 상설(is_permanent=TRUE)은 end_date가 NULL이어야 한다.
        jdbcTemplate.update(
                "INSERT INTO event (event_id, title, start_date, is_permanent) "
                        + "VALUES (?, '찜 통합 테스트 이벤트', CURRENT_DATE, TRUE)",
                itemId);
        return itemId;
    }

    private long insertPlaceItem(long reviewerId) {
        jdbcTemplate.update(
                "INSERT INTO explore_items "
                        + "(item_type, approval_status, visibility_status, "
                        + "reviewed_by, reviewed_at) "
                        + "VALUES ('PLACE', 'APPROVED', 'VISIBLE', ?, CURRENT_TIMESTAMP)",
                reviewerId);
        long itemId = lastInsertId();
        jdbcTemplate.update(
                "INSERT INTO place (place_id, name) VALUES (?, '찜 통합 테스트 플레이스')",
                itemId);
        return itemId;
    }

    /** chk_event_period — 상설이 아니면 end_date가 필수다. 어제 끝난 Event를 만든다. */
    private long insertEndedEventItem(long reviewerId) {
        jdbcTemplate.update(
                "INSERT INTO explore_items "
                        + "(item_type, approval_status, visibility_status, "
                        + "reviewed_by, reviewed_at) "
                        + "VALUES ('EVENT', 'APPROVED', 'VISIBLE', ?, CURRENT_TIMESTAMP)",
                reviewerId);
        long itemId = lastInsertId();
        jdbcTemplate.update(
                "INSERT INTO event "
                        + "(event_id, title, start_date, end_date, is_permanent) "
                        + "VALUES (?, '찜 통합 테스트 종료 이벤트', "
                        + "CURRENT_DATE - INTERVAL 7 DAY, "
                        + "CURRENT_DATE - INTERVAL 1 DAY, FALSE)",
                itemId);
        return itemId;
    }

    private long insertInactivePlaceItem(long reviewerId) {
        jdbcTemplate.update(
                "INSERT INTO explore_items "
                        + "(item_type, approval_status, visibility_status, "
                        + "reviewed_by, reviewed_at) "
                        + "VALUES ('PLACE', 'APPROVED', 'VISIBLE', ?, CURRENT_TIMESTAMP)",
                reviewerId);
        long itemId = lastInsertId();
        jdbcTemplate.update(
                "INSERT INTO place (place_id, name, is_active) "
                        + "VALUES (?, '찜 통합 테스트 비활성 플레이스', FALSE)",
                itemId);
        return itemId;
    }

    private long favoriteCount(String table, String idColumn, long itemId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT favorite_count FROM " + table + " WHERE " + idColumn + " = ?",
                Long.class, itemId);
        return count == null ? -1L : count;
    }

    private long lastInsertId() {
        Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        if (id == null || id == 0L) {
            throw new IllegalStateException("LAST_INSERT_ID()를 읽지 못했습니다");
        }
        return id;
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
