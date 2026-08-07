package me.nawa.journey.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.util.Comparator;
import java.util.List;
import me.nawa.journey.domain.JourneyTimelineItem;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;

@EnabledIfEnvironmentVariable(
    named = "RUN_MYSQL_INTEGRATION_TESTS",
    matches = "(?i)true"
)
class JourneyMapperIntegrationTest {

    private static HikariDataSource dataSource;
    private static JourneyMapper mapper;

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

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required");
        }
        return value;
    }
}
