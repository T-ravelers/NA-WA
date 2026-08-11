package me.nawa.report.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import me.nawa.common.exception.BusinessException;
import me.nawa.report.dto.request.ReportCreateRequest;
import me.nawa.report.dto.response.ReportDetailResponse;
import me.nawa.report.exception.ReportErrorCode;
import me.nawa.report.mapper.ReportMapper;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@EnabledIfEnvironmentVariable(
    named = "RUN_MYSQL_INTEGRATION_TESTS",
    matches = "(?i)true"
)
class ReportConcurrencyIntegrationTest {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
    private static final long TIMEOUT_SECONDS = 10L;

    private static HikariDataSource dataSource;
    private static JdbcTemplate jdbcTemplate;
    private static ReportService reportService;
    private static PlatformTransactionManager transactionManager;

    @BeforeAll
    static void setUpDatabase() throws Exception {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setDriverClassName(requiredEnvironment(
            "DATABASE_DRIVER"
        ));
        hikariConfig.setJdbcUrl(requiredEnvironment("DATABASE_URL"));
        hikariConfig.setUsername(requiredEnvironment("DATABASE_USERNAME"));
        hikariConfig.setPassword(requiredEnvironment("DATABASE_PASSWORD"));
        hikariConfig.setMaximumPoolSize(4);
        hikariConfig.setMinimumIdle(0);
        dataSource = new HikariDataSource(hikariConfig);
        jdbcTemplate = new JdbcTemplate(dataSource);

        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setConfigLocation(new ClassPathResource(
            "mybatis-config.xml"
        ));
        factoryBean.setMapperLocations(new ClassPathResource(
            "me/nawa/report/mapper/ReportMapper.xml"
        ));

        SqlSessionFactory sqlSessionFactory = factoryBean.getObject();
        if (!sqlSessionFactory.getConfiguration().hasMapper(
            ReportMapper.class
        )) {
            sqlSessionFactory.getConfiguration().addMapper(
                ReportMapper.class
            );
        }
        ReportMapper mapper = new SqlSessionTemplate(sqlSessionFactory)
            .getMapper(ReportMapper.class);
        reportService = new ReportService(mapper);
        transactionManager = new DataSourceTransactionManager(dataSource);
    }

    @AfterAll
    static void closeDatabase() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Test
    void createReport_concurrentTransactionsPersistExactlyOneActiveReport()
        throws Exception {
        long memberId = insertMember();
        long tripId = insertEndedJourney(memberId);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Callable<CreationOutcome> task = () -> createReport(
                memberId,
                tripId,
                ready,
                start
            );
            Future<CreationOutcome> first = executor.submit(task);
            Future<CreationOutcome> second = executor.submit(task);

            assertTrue(ready.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            start.countDown();
            List<CreationOutcome> outcomes = List.of(
                first.get(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                second.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            );

            assertEquals(
                1L,
                outcomes.stream().filter(CreationOutcome::succeeded).count()
            );
            BusinessException conflict = outcomes.stream()
                .map(CreationOutcome::failure)
                .filter(failure -> failure != null)
                .findFirst()
                .orElseThrow();
            assertEquals(
                ReportErrorCode.REPORT_ALREADY_EXISTS,
                conflict.getErrorCode()
            );
            assertEquals("REPORT-005", conflict.getErrorCode().getCode());
            assertEquals(
                1,
                jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*)
                    FROM reports
                    WHERE trip_id = ?
                      AND deleted_at IS NULL
                    """,
                    Integer.class,
                    tripId
                )
            );
        } finally {
            start.countDown();
            executor.shutdownNow();
            executor.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            jdbcTemplate.update("DELETE FROM reports WHERE trip_id = ?", tripId);
            jdbcTemplate.update("DELETE FROM trips WHERE trip_id = ?", tripId);
            jdbcTemplate.update(
                "DELETE FROM members WHERE member_id = ?",
                memberId
            );
        }
    }

    private CreationOutcome createReport(
        long memberId,
        long tripId,
        CountDownLatch ready,
        CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        if (!start.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent start timed out");
        }

        try {
            ReportDetailResponse response = new TransactionTemplate(
                transactionManager
            ).execute(status -> reportService.createReport(
                memberId,
                tripId,
                new ReportCreateRequest("en")
            ));
            return new CreationOutcome(response, null);
        } catch (BusinessException exception) {
            return new CreationOutcome(null, exception);
        }
    }

    private long insertMember() {
        Number key = new SimpleJdbcInsert(dataSource)
            .withTableName("members")
            .usingColumns("display_name")
            .usingGeneratedKeyColumns("member_id")
            .executeAndReturnKey(Map.of(
                "display_name",
                "Report concurrency test"
            ));
        return key.longValue();
    }

    private long insertEndedJourney(long memberId) {
        LocalDate endDate = LocalDate.now(KOREA_ZONE).minusDays(1);
        Number key = new SimpleJdbcInsert(dataSource)
            .withTableName("trips")
            .usingColumns("member_id", "title", "start_date", "end_date")
            .usingGeneratedKeyColumns("trip_id")
            .executeAndReturnKey(Map.of(
                "member_id", memberId,
                "title", "Concurrent Report Journey",
                "start_date", endDate.minusDays(2),
                "end_date", endDate
            ));
        return key.longValue();
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

    private record CreationOutcome(
        ReportDetailResponse response,
        BusinessException failure
    ) {
        private boolean succeeded() {
            return response != null;
        }
    }
}
