package me.nawa.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.PropertySource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;


@Configuration
@PropertySource("classpath:/application.properties")
@Import({RedisConfig.class, SecurityConfig.class})
@MapperScan(basePackages = {"me.nawa.auth.mapper",
    "me.nawa.appointment.mapper",
    "me.nawa.review.mapper",
    "me.nawa.explore.mapper",
    "me.nawa.journey.mapper",
    "me.nawa.report.mapper",
    "me.nawa.map.mapper",
    "me.nawa.member.mapper",
    "me.nawa.wallet.mapper",
    "me.nawa.settlement.mapper",
    "me.nawa.deposit.mapper"})
@ComponentScan(basePackages = {"me.nawa.auth.service",
    "me.nawa.auth.jwt",
    "me.nawa.auth.oauth",
    "me.nawa.auth.refresh",
    "me.nawa.auth.token",
    "me.nawa.auth.cookie",
    "me.nawa.auth.security",
    "me.nawa.appointment.service",
    "me.nawa.review.service",
    "me.nawa.explore.service",
    "me.nawa.journey.service",
    "me.nawa.report.service",
    "me.nawa.map.service",
    "me.nawa.member.service",
    "me.nawa.wallet.service",
    "me.nawa.wallet.external.stripe",
    "me.nawa.wallet.util",
    "me.nawa.settlement.service",
    "me.nawa.common.storage",
    "me.nawa.deposit.service"})
@EnableTransactionManagement
@EnableScheduling
public class RootConfig {
    @Value("${jdbc.driver}")
    private String driver;

    @Value("${jdbc.url}")
    private String url;

    @Value("${jdbc.username}")
    private String username;

    @Value("${jdbc.password}")
    private String password;

    @Autowired
    ApplicationContext applicationContext;

    /*
     * SQL SessionFactory 빈 등록
     * - MyBatis 팩토리 객체를 스프링 컨테이너에 등록
     */
    /*
     * 앱 시작 시 DB 마이그레이션 자동 적용
     * - src/main/resources/db/migration 안의 V{n}__*.sql을 버전 순서대로 실행
     * - sqlSessionFactory가 이 빈을 파라미터로 받아 스키마 적용 이후에 뜨도록 강제함
     */
    @Bean
    public Flyway flyway(DataSource dataSource) {
        Flyway flyway = Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load();
        flyway.migrate();
        return flyway;
    }

    @Bean
    public SqlSessionFactory sqlSessionFactory(DataSource dataSource, Flyway flyway) throws Exception {
        SqlSessionFactoryBean sqlSessionFactory = new SqlSessionFactoryBean();

        // MyBatis global 설정
        sqlSessionFactory.setConfigLocation(applicationContext
            .getResource("classpath:/mybatis-config.xml"));

        // DB 연결 설정
        sqlSessionFactory.setDataSource(dataSource);

        sqlSessionFactory.setMapperLocations(
            applicationContext.getResources(
                "classpath*:me/nawa/**/mapper/*Mapper.xml"
            )
        );

        return sqlSessionFactory.getObject();
    }

    /*
     * 트랜잭션 매니저
     * - Spring에서 DB 트랜잭션을 관리하기 위해서
     */
    @Bean
    public DataSourceTransactionManager transactionManager() {
        DataSourceTransactionManager manager = new DataSourceTransactionManager(dataSource());
        return manager;
    }

    /*
     * HikariCP 커넥션 풀을 사용한 DataSource 빈 생성
     */
    @Bean
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();

        // DB 연결 정보 세팅
        config.setDriverClassName(driver); // JDBC 드라이버 클래스
        config.setJdbcUrl(url); // 데이터베이스 URL
        config.setUsername(username);
        config.setPassword(password);

        // 커넥션 풀 설정
        config.setMaximumPoolSize(10); // 최대로 가질 수 있는 커넥션 수
        config.setMinimumIdle(5); // 최소 유지 커넥션 수
        config.setConnectionTimeout(30000); // 연결 타임아웃 (30초)
        config.setIdleTimeout(600000); // 유휴 타임아웃 (10분)

        HikariDataSource dataSource = new HikariDataSource(config);

        return dataSource;
    }
}
