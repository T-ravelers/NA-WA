package me.nawa.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

class RootConfigMyBatisTest {

    private static final String MAPPER_LOCATION_PATTERN =
        "classpath*:me/nawa/**/mapper/*Mapper.xml";

    @Test
    void sqlSessionFactory_loadsDepositMapperXmlStatements() throws Exception {
        RootConfig rootConfig = new RootConfig();
        PathMatchingResourcePatternResolver resolver =
            new PathMatchingResourcePatternResolver();
        Resource mapperConfig = new ClassPathResource("mybatis-config.xml");
        Resource[] mapperResources = resolver.getResources(
            MAPPER_LOCATION_PATTERN
        );

        rootConfig.applicationContext = applicationContext(
            mapperConfig,
            mapperResources
        );

        SqlSessionFactory sqlSessionFactory = rootConfig.sqlSessionFactory(
            dataSource(),
            null
        );

        assertTrue(sqlSessionFactory.getConfiguration().hasStatement(
            "me.nawa.deposit.mapper.DepositMapper.findById"
        ));
        assertTrue(sqlSessionFactory.getConfiguration().hasStatement(
            "me.nawa.deposit.mapper.DepositPayoutBatchMapper.findById"
        ));
        assertTrue(sqlSessionFactory.getConfiguration().hasStatement(
            "me.nawa.deposit.mapper.DepositPayoutMapper.findById"
        ));
    }

    private static ApplicationContext applicationContext(
        Resource mapperConfig,
        Resource[] mapperResources
    ) {
        return (ApplicationContext) Proxy.newProxyInstance(
            ApplicationContext.class.getClassLoader(),
            new Class<?>[]{ApplicationContext.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getResource" -> mapperConfig;
                case "getResources" -> mapperResources;
                case "toString" -> "RootConfigMyBatisTestApplicationContext";
                default -> throw new UnsupportedOperationException(
                    method.toString()
                );
            }
        );
    }

    private static DataSource dataSource() {
        return (DataSource) Proxy.newProxyInstance(
            DataSource.class.getClassLoader(),
            new Class<?>[]{DataSource.class},
            (proxy, method, args) -> null
        );
    }
}
