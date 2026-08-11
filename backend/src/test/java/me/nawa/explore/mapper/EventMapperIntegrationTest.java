package me.nawa.explore.mapper;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.util.List;
import me.nawa.explore.dto.response.EventDetailResponse;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

@EnabledIfEnvironmentVariable(
    named = "RUN_MYSQL_INTEGRATION_TESTS",
    matches = "(?i)true"
)
class EventMapperIntegrationTest {

    private static HikariDataSource dataSource;
    private static EventMapper mapper;
    private static JdbcTemplate jdbcTemplate;

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
        // XML의 namespace가 이미 인터페이스를 등록하므로 그대로 addMapper를 부르면
        // MapperRegistry가 중복 등록으로 BindingException을 던진다.
        if (!sqlSessionFactory.getConfiguration().hasMapper(
            EventMapper.class
        )) {
            sqlSessionFactory.getConfiguration().addMapper(EventMapper.class);
        }
        mapper = new SqlSessionTemplate(sqlSessionFactory)
            .getMapper(EventMapper.class);
    }

    @AfterAll
    static void closeDatabase() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Test
    void findEventDetail_mapsV7ColumnsAndJsonValues() {
        List<Long> eventIds = jdbcTemplate.query(
            """
            SELECT e.event_id
            FROM explore_items ei
            JOIN event e ON e.event_id = ei.item_id
            WHERE ei.item_type = 'EVENT'
              AND ei.approval_status = 'APPROVED'
              AND ei.visibility_status = 'VISIBLE'
              AND ei.deleted_at IS NULL
              AND e.deleted_at IS NULL
              AND e.status IN ('SCHEDULED', 'ONGOING')
              AND e.image_urls IS NOT NULL
              AND e.links IS NOT NULL
              AND e.pre_reservation IS NOT NULL
              AND e.operating_hours IS NOT NULL
              AND e.open_days IS NOT NULL
            LIMIT 1
            """,
            (resultSet, rowNumber) -> resultSet.getLong("event_id")
        );

        Assumptions.assumeTrue(
            !eventIds.isEmpty(),
            "A public Event with all V7 JSON values is required"
        );

        EventDetailResponse result = mapper.findEventDetail(
            eventIds.get(0),
            "en"
        );

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
