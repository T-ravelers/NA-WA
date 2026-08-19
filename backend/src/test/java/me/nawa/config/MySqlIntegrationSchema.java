package me.nawa.config;

import org.flywaydb.core.Flyway;

/**
 * MySQL 게이트 통합 테스트가 공유하는 스키마 준비 지점이다.
 *
 * 게이트 대상 통합 테스트는 테이블이 이미 있다고 가정하고 INSERT/SELECT만 한다.
 * 예전에는 마이그레이션을 {@link FlywayMigrationIntegrationTest}가 직접 실행했는데,
 * 테스트 클래스 실행 순서는 보장되지 않아 빈 DB에서는 그보다 먼저 도는 클래스들이
 * 전부 "Table doesn't exist"로 깨졌다(#278). 그래서 마이그레이션은
 * {@link MySqlSchemaExtension}이 각 통합 테스트 클래스 앞에서 호출하고, 이
 * 클래스가 실제 실행을 한 번으로 묶는다.
 */
public final class MySqlIntegrationSchema {
    private static boolean prepared;
    private static RuntimeException preparationFailure;

    private MySqlIntegrationSchema() {
    }

    /**
     * 마이그레이션을 한 번만 적용한다. 게이트 통합 테스트 클래스마다 호출되지만
     * 실제 실행은 첫 호출뿐이다.
     *
     * 한 번 실패한 준비를 클래스 수만큼 재시도해봐야 같은 이유로 또 실패하므로,
     * 실패도 기억해 두고 이후 호출에는 곧바로 같은 원인을 던진다.
     */
    public static synchronized void prepare() {
        if (preparationFailure != null) {
            throw new IllegalStateException(
                    "MySQL 통합 테스트 스키마 준비가 앞서 실패했습니다",
                    preparationFailure
            );
        }
        if (prepared) {
            return;
        }

        try {
            flyway().migrate();
            prepared = true;
        } catch (RuntimeException exception) {
            preparationFailure = exception;
            throw exception;
        }
    }

    /** 마이그레이션 적용과 검증이 같은 설정을 쓰도록 한 곳에서 만든다. */
    public static Flyway flyway() {
        return Flyway.configure()
                .dataSource(
                        requiredEnvironment("DATABASE_URL"),
                        requiredEnvironment("DATABASE_USERNAME"),
                        requiredEnvironment("DATABASE_PASSWORD")
                )
                .locations("classpath:db/migration")
                .load();
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
