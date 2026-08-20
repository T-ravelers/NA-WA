package me.nawa.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.InputStream;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 모든 mapper XML의 SQL을 실제 스키마에 대고 검증한다.
 *
 * 개별 {@code *MapperXmlTest}는 statement가 등록됐는지와 SQL 문자열에 특정 조각이
 * 들어 있는지만 본다. SQL을 실행하지 않으므로, 마이그레이션에서 컬럼을 이름 변경·
 * 삭제하고 mapper XML을 고치지 않아도(또는 그 반대여도) 통과한다.
 *
 * 여기서는 statement를 실행하지 않고 prepare만 한다. MySQL은 prepare 단계에서
 * 테이블·컬럼명을 검증하므로, 데이터를 건드리지 않고 XML↔스키마 드리프트를 잡을 수
 * 있다. mapper 등록 패턴을 RootConfig와 똑같이 맞춰 두어, 새 mapper XML이 추가되면
 * 이 테스트를 고치지 않아도 자동으로 검증 대상에 들어간다.
 *
 * <p><b>잡는 것</b>: 없는 테이블·컬럼, 오타, 마이그레이션과 어긋난 이름, 문법 오류.
 *
 * <p><b>못 잡는 것</b>: 컬럼 타입 불일치, resultMap↔도메인 객체 매핑 오류, 조건절의
 * 의미가 틀린 경우. 이런 것은 각 mapper의 통합 테스트가 담당한다.
 */
@EnabledIfEnvironmentVariable(
        named = "RUN_MYSQL_INTEGRATION_TESTS",
        matches = "(?i)true"
)
class MapperSqlSchemaIntegrationTest {

    private static final String MAPPER_PATTERN =
            "classpath*:me/nawa/**/mapper/*Mapper.xml";

    private static HikariDataSource dataSource;

    @BeforeAll
    static void setUpDataSource() {
        HikariConfig config = new HikariConfig();
        config.setDriverClassName(requiredEnvironment("DATABASE_DRIVER"));
        config.setJdbcUrl(requiredEnvironment("DATABASE_URL"));
        config.setUsername(requiredEnvironment("DATABASE_USERNAME"));
        config.setPassword(requiredEnvironment("DATABASE_PASSWORD"));
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(0);
        dataSource = new HikariDataSource(config);
    }

    @AfterAll
    static void closeDataSource() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    /**
     * 이 테스트가 실제로 무언가를 검증하는지 먼저 확인한다.
     *
     * MySQL Connector/J는 기본적으로 클라이언트에서 문자열만 조립하므로
     * prepare가 스키마를 전혀 보지 않는다. 그 상태에서는 아래 본 검증이 없는 컬럼도
     * 조용히 통과시켜, 아무것도 막지 못하면서 초록불만 켜는 테스트가 된다.
     */
    @Test
    void preparingUnknownColumn_fails() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            try {
                prepareOnServer(
                        connection,
                        "SELECT no_such_column_for_guard FROM appointments");
                fail("없는 컬럼이 prepare를 통과했습니다. 이 상태로는 아래 검증이 "
                        + "아무것도 막지 못합니다.");
            } catch (SQLException expected) {
                assertTrue(
                        expected.getMessage().contains("no_such_column_for_guard"),
                        "예상과 다른 오류입니다: " + expected.getMessage()
                );
            }
        }
    }

    @Test
    void everyMappedStatement_preparesAgainstTheRealSchema() throws Exception {
        Configuration configuration = loadAllMappers();
        List<String> statementIds = configuration.getMappedStatementNames().stream()
                // MyBatis는 statement를 전체 이름과 짧은 이름 두 번 등록한다.
                .filter(name -> name.contains("."))
                .filter(name -> name.startsWith("me.nawa."))
                .distinct()
                .sorted()
                .toList();

        assertTrue(
                statementIds.size() >= 100,
                "mapper XML을 제대로 읽지 못했습니다. 등록된 statement: "
                        + statementIds.size()
        );

        Map<String, String> failures = new TreeMap<>();
        int prepared = 0;
        try (Connection connection = dataSource.getConnection()) {
            for (String statementId : statementIds) {
                MappedStatement statement =
                        configuration.getMappedStatement(statementId, false);
                String sql;
                try {
                    BoundSql boundSql =
                            statement.getBoundSql(AnyValue.INSTANCE);
                    sql = boundSql.getSql();
                } catch (RuntimeException exception) {
                    failures.put(
                            statementId,
                            "SQL 생성 실패: " + rootMessage(exception)
                    );
                    continue;
                }
                try {
                    prepareOnServer(connection, sql);
                    prepared++;
                } catch (SQLException exception) {
                    failures.put(
                            statementId,
                            exception.getMessage() + "\n      SQL: " + oneLine(sql)
                    );
                }
            }
        }

        if (!failures.isEmpty()) {
            StringBuilder message = new StringBuilder()
                    .append(failures.size())
                    .append("개 statement가 실제 스키마에 대해 prepare되지 않았습니다")
                    .append(" (전체 ")
                    .append(statementIds.size())
                    .append("개). mapper XML과 마이그레이션이 어긋났는지 확인하세요.\n");
            failures.forEach((id, reason) ->
                    message.append("  - ").append(id)
                            .append("\n      ").append(reason).append('\n'));
            fail(message.toString());
        }

        assertTrue(prepared == statementIds.size());
    }

    private static Configuration loadAllMappers() throws Exception {
        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        // <foreach> 항목의 파라미터 매핑은 항목 타입의 TypeHandler를 요구한다. 실행은
        // 하지 않으니 동작은 필요 없고, 등록되어 있기만 하면 된다.
        configuration.getTypeHandlerRegistry()
                .register(AnyValue.class, new AnyValueTypeHandler());
        Resource[] resources = new PathMatchingResourcePatternResolver()
                .getResources(MAPPER_PATTERN);
        // RootConfig가 등록하는 것과 같은 목록이어야 한다. 적게 잡히면 검증에 구멍이
        // 생기므로 개수를 함께 확인한다.
        assertTrue(
                resources.length >= 15,
                "mapper XML을 " + resources.length + "개만 찾았습니다. "
                        + "패턴이 RootConfig와 어긋났는지 확인하세요."
        );
        for (Resource resource : resources) {
            String location = "me/nawa/"
                    + resource.getURL().toString().split("/me/nawa/")[1];
            try (InputStream input = resource.getInputStream()) {
                new XMLMapperBuilder(
                        input,
                        configuration,
                        location,
                        configuration.getSqlFragments()
                ).parse();
            }
        }
        return configuration;
    }

    /**
     * SQL 수준 PREPARE로 서버에 직접 검증시킨다.
     *
     * JDBC의 {@code Connection.prepareStatement}를 쓰지 않는 이유가 있다. MySQL
     * Connector/J는 기본적으로 클라이언트에서 문자열만 조립하므로(
     * {@code useServerPrepStmts=false}) 없는 컬럼도 그대로 통과시킨다. URL 옵션으로
     * 서버 측 prepare를 켜는 방법은 드라이버 버전과 다른 옵션에 따라 조용히 무시될 수
     * 있어, 검증이 꺼진 줄 모른 채 초록불만 보게 된다. SQL 수준 PREPARE는 서버가
     * 실행하므로 그런 회색지대가 없다.
     *
     * SQL을 사용자 변수에 담아 넘겨 따옴표 이스케이프를 피한다.
     */
    private static void prepareOnServer(Connection connection, String sql)
            throws SQLException {
        try (PreparedStatement assignment =
                     connection.prepareStatement("SET @mapper_sql = ?")) {
            assignment.setString(1, sql);
            assignment.execute();
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("PREPARE mapper_sql_guard FROM @mapper_sql");
            statement.execute("DEALLOCATE PREPARE mapper_sql_guard");
        }
    }

    private static String oneLine(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getClass().getSimpleName() + ": " + current.getMessage();
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

    /**
     * 동적 SQL의 모든 가지를 켜기 위한 파라미터 대역이다.
     *
     * SQL 문자열만 필요하고 실행은 하지 않으므로 값의 내용은 중요하지 않다. 필요한
     * 것은 SQL의 모양뿐이다.
     *
     * <ul>
     *   <li>어떤 이름으로 접근하든 자기 자신을 돌려줘 {@code request.itemId} 같은
     *       중첩 접근이 계속 통한다.</li>
     *   <li>항목이 하나인 Map이라 {@code <foreach>}가 정확히 한 번 돌고
     *       {@code .size() > 0}이 참이 된다.</li>
     *   <li>{@code equals}는 boolean true에만 참이다. {@code x == true}는 켜지고
     *       {@code x != ''}도 참으로 남는다 — 항상 참을 주면 후자가 꺼진다.</li>
     * </ul>
     */
    private static final class AnyValue
            extends AbstractMap<String, Object> {

        private static final AnyValue INSTANCE = new AnyValue();

        @Override
        public Object get(Object key) {
            return INSTANCE;
        }

        @Override
        public boolean containsKey(Object key) {
            return true;
        }

        @Override
        public Set<Entry<String, Object>> entrySet() {
            List<Entry<String, Object>> single = new ArrayList<>(1);
            single.add(new SimpleEntry<>("any", INSTANCE));
            return Set.copyOf(single);
        }

        @Override
        public boolean equals(Object other) {
            return Boolean.TRUE.equals(other);
        }

        @Override
        public int hashCode() {
            return 1;
        }

        /**
         * OGNL의 수치 비교({@code limit > 0})는 피연산자를 문자열로 바꿔 숫자로
         * 읽는다. 기본 Map의 toString은 숫자가 아니라 그 자리에서 터진다.
         */
        @Override
        public String toString() {
            return "1";
        }
    }

    /** 실행하지 않으므로 값 변환은 필요 없다. 등록 자체가 목적이다. */
    private static final class AnyValueTypeHandler
            extends BaseTypeHandler<AnyValue> {

        @Override
        public void setNonNullParameter(
                PreparedStatement statement,
                int index,
                AnyValue parameter,
                JdbcType jdbcType) throws SQLException {
            statement.setObject(index, null);
        }

        @Override
        public AnyValue getNullableResult(ResultSet resultSet, String columnName) {
            return AnyValue.INSTANCE;
        }

        @Override
        public AnyValue getNullableResult(ResultSet resultSet, int columnIndex) {
            return AnyValue.INSTANCE;
        }

        @Override
        public AnyValue getNullableResult(CallableStatement statement, int columnIndex) {
            return AnyValue.INSTANCE;
        }
    }
}
